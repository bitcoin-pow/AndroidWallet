package com.wallet.btcw.network

import android.os.Handler
import android.os.Looper
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import java.util.concurrent.ConcurrentLinkedQueue
import com.wallet.btcw.wallet.*

data class SyncStatus(
    val message: String = "Sync paused",
    val peer: String? = null,
    val downloaded: Int = 0,
    val anchored: Int = 0,
    val peerHeight: Int? = null,
    val peerAgreement: String = "Peer comparison pending",
)

/** Foreground-only, one peer at a time with DNS discovery and bounded failover. */
class PeerSync(private val directory: File, private val onFunds: (FundsState) -> Unit = {}, private val onStatus: (SyncStatus) -> Unit) : AutoCloseable {
    private val journal = HeaderJournal(File(directory, "btcw-headers-v1.dat"))
    private val rescanRequest = File(directory, "wallet-rescan-requested")
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val comparison = PeerComparison()
    private val dns = ThreadPoolExecutor(2, 2, 0, TimeUnit.MILLISECONDS, ArrayBlockingQueue(8)) { r ->
        Thread(r, "btcw-dns").apply { isDaemon = true }
    }
    private val generation = AtomicInteger()
    private var task: Future<*>? = null
    @Volatile private var activePeer: PeerSession? = null
    private var lastStatus = SyncStatus()
    @Volatile private var watch = emptyList<WatchAddress>()
    @Volatile private var spendSnapshot: SpendSnapshot? = null
    private val sends = ConcurrentLinkedQueue<QueuedSend>()
    internal data class SpendSnapshot(val wallet: String, val tip: String, val inputs: List<TransactionSigner.Input>, val time: Long)
    private data class QueuedSend(val snapshot: SpendSnapshot, val signed: TransactionSigner.Signed, val result: (String) -> Unit)

    fun watchWallet(addresses: List<WatchAddress>) {
        if (watch == addresses) return
        stop(); watch = addresses.toList(); start()
    }

    /** Persist intent before restarting; the worker resets the ledger under storageLock. */
    fun rescanWallet() {
        stop()
        try {
            rescanRequest.createNewFile()
            onFunds(FundsState(scannedHeight = WalletLedger.SCAN_START_HEIGHT - 1,
                chainHeight = lastStatus.downloaded, message = "Rescan requested from block ${WalletLedger.SCAN_START_HEIGHT}"))
            start()
        } catch (_: Exception) {
            onFunds(FundsState(message = "Could not request a rescan. Check device storage and try again."))
        }
    }

    internal fun snapshot(): SpendSnapshot {
        val snapshot = spendSnapshot ?: error("Finish syncing before sending")
        check(System.currentTimeMillis() - snapshot.time < 60_000) { "Waiting for fresh peer data" }
        return snapshot
    }

    internal fun enqueue(snapshot: SpendSnapshot, signed: TransactionSigner.Signed, result: (String) -> Unit) {
        sends.add(QueuedSend(snapshot, signed, result))
    }

    private fun funds(token: Int, value: FundsState) {
        main.post { if (generation.get() == token) onFunds(value) }
    }

    fun start() {
        if (task?.isDone == false) return
        val token = generation.incrementAndGet()
        task = worker.submit {
            try {
                storageLock.lockInterruptibly()
                try { if (current(token)) synchronize(token) } finally { storageLock.unlock() }
            } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
        }
    }

    fun stop() {
        generation.incrementAndGet()
        activePeer?.close()
        comparison.cancel()
        task?.cancel(true)
        task = null
        spendSnapshot = null
        while (true) {
            val canceled = sends.poll() ?: break
            canceled.result("Submission canceled before it was queued. Review again.")
        }
        lastStatus = lastStatus.copy(message = "Sync paused", peer = null)
        onStatus(lastStatus)
    }

    private fun publish(token: Int, status: SyncStatus) {
        main.post {
            if (generation.get() == token) { lastStatus = status; onStatus(status) }
        }
    }

    private fun current(token: Int) = generation.get() == token && !Thread.currentThread().isInterrupted

    private fun synchronize(token: Int) {
        try {
            var chain = journal.restore()
            var nextComparison = 0L
            var agreement = "Peer comparison pending"
            var preferred = emptySet<InetAddress>()
            val addresses = watch
            val ledger = if (addresses.isEmpty()) null else WalletLedger(File(directory, "wallet-transactions-v1.json"), addresses)
            if (ledger != null && rescanRequest.exists()) {
                ledger.resetForReorg("Rechecking after rescan")
                check(rescanRequest.delete()) { "Cannot clear rescan request" }
                funds(token, ledger.state(chain.downloadedHeight, false, "Rescanning from block ${WalletLedger.SCAN_START_HEIGHT}"))
            }
            fun status(message: String, peer: String? = null, height: Int? = null) =
                publish(token, SyncStatus(message, peer, chain.downloadedHeight, chain.checkpointAnchoredHeight, height, agreement))
            fun compare(candidates: List<InetAddress>) {
                if (chain.downloadedHeight == 0 || System.nanoTime() < nextComparison) return
                status("Comparing hashes from up to 10 peers")
                spendSnapshot = null
                val result = comparison.compare(candidates, chain)
                nextComparison = System.nanoTime() + TimeUnit.MINUTES.toNanos(10)
                preferred = result.supporters
                agreement = if (result.winner == null) "No clear peer agreement (${result.responses}/10 replies); keeping known checkpoints"
                    else "${result.supporters.size}/${result.responses} peers agree at block ${result.height} · ${result.winner.take(12)}"
            }
            while (current(token)) {
                status("Discovering peers")
                val candidates = linkedSetOf<InetAddress>()
                for (seed in BtcwNetwork.dnsSeeds.shuffled()) {
                    if (!current(token)) return
                    val lookup = dns.submit<List<InetAddress>> { InetAddress.getAllByName(seed).toList() }
                    try { candidates.addAll(lookup.get(5, TimeUnit.SECONDS).take(16)) }
                    catch (e: InterruptedException) { throw e }
                    catch (_: Exception) { /* Another DNS seed may still work. */ }
                    finally { lookup.cancel(true); dns.purge() }
                }
                if (candidates.isEmpty()) status("No peers found. Retrying in 30 seconds.")
                compare(candidates.toList())
                for (address in candidates.shuffled().sortedByDescending { it in preferred }.take(32)) {
                    if (!current(token)) return
                    val name = address.hostAddress ?: "Peer"
                    val session = PeerSession()
                    activePeer = session
                    try {
                        if (!current(token)) return
                        status("Connecting", name)
                        val version = session.connect(InetSocketAddress(address, BtcwNetwork.port), chain.downloadedHeight)
                        val delivered = mutableSetOf<String>()
                        while (current(token)) {
                            status("Downloading headers", name, version.height)
                            val payload = session.headers(chain.tipHash)
                            // Parse/check atomically before writing anything to disk.
                            val before = chain.downloadedHeight
                            try { chain.appendHeaders(payload) }
                            catch (e: IllegalArgumentException) {
                                // We cannot choose competing branches by work yet. Replay from the
                                // immutable genesis/checkpoints with another peer, never accept a fork.
                                journal.reset()
                                chain = CheckpointHeaderChain()
                                throw e
                            }
                            if (chain.downloadedHeight == before) {
                                compare(candidates.toList())
                                if (preferred.isNotEmpty() && address !in preferred) error("Switching to a peer with the most common block hash")
                                require(before >= version.height && before >= BtcwCheckpoints.latestHeight) {
                                    "Peer stopped before its announced height or the final checkpoint"
                                }
                                if (ledger != null) {
                                    if (ledger.height > chain.downloadedHeight || (ledger.blockHash.isNotEmpty() && ledger.blockHash != chain.hashAt(ledger.height).toHex())) {
                                        ledger.resetForReorg()
                                    }
                                    while (ledger.height < chain.downloadedHeight && current(token)) {
                                        compare(candidates.toList())
                                        if (preferred.isNotEmpty() && address !in preferred) error("Switching to a peer with the most common block hash")
                                        val next = ledger.height + 1
                                        val end = minOf(chain.downloadedHeight, next + 7)
                                        funds(token, ledger.state(chain.downloadedHeight, false, "Scanning blocks $next–$end. Initial sync can take time."))
                                        val blocks = session.blocks((next..end).map { chain.headerAt(it) })
                                        for ((offset, block) in blocks.withIndex()) {
                                            SpvWork.verify(next + offset, chain, block)
                                            ledger.apply(next + offset, block.header.hash.toHex(), block.transactions)
                                        }
                                        try { ledger.save() } catch (e: Exception) { throw StorageFailure(e) }
                                    }
                                    if (!current(token)) return
                                    spendSnapshot = SpendSnapshot(addresses[0].address, chain.tipHash.toHex(), ledger.inputs(chain.downloadedHeight), System.currentTimeMillis())
                                    funds(token, ledger.state(chain.downloadedHeight, true, "SPV scan complete · spendable after 1 confirmation"))
                                    // Newly approved transactions are durably reserved before submission.
                                    var commands = 0
                                    while (commands++ < 100) {
                                        val queued = sends.poll() ?: break
                                            try {
                                                require(queued.snapshot.wallet == addresses[0].address && queued.snapshot.tip == chain.tipHash.toHex()) { "Chain changed; review again" }
                                                ledger.queue(queued.signed)
                                                spendSnapshot = SpendSnapshot(addresses[0].address, chain.tipHash.toHex(), ledger.inputs(chain.downloadedHeight), System.currentTimeMillis())
                                                main.post { queued.result("Transaction queued. Waiting for peer delivery and confirmation.") }
                                            } catch (_: Exception) {
                                                main.post { queued.result("Could not queue this transaction. Refresh and review again.") }
                                            }
                                    }
                                    for ((id, raw) in ledger.queued()) {
                                        if (!current(token)) return
                                        if (id in delivered) continue
                                        session.submitTransaction(raw.hexBytes())
                                        try { ledger.submitted(id) } catch (e: Exception) { throw StorageFailure(e) }
                                        delivered.add(id)
                                    }
                                    funds(token, ledger.state(chain.downloadedHeight, true, "SPV scan complete · submitted transactions await confirmation"))
                                    status("Headers and wallet scan up to date", name, version.height)
                                } else status("Headers downloaded · unlock to scan wallet", name, version.height)
                                // Poll for approvals promptly while retaining a 10-second header cadence.
                                for (tick in 0 until 50) {
                                    if (!current(token)) return
                                    if (!sends.isEmpty()) break
                                    Thread.sleep(200)
                                }
                            } else {
                                // Storage failures are fatal to this run, not reasons to blame peers.
                                try { journal.append(payload) }
                                catch (e: Exception) { throw StorageFailure(e) }
                                status("Headers downloaded", name, version.height)
                            }
                        }
                    } catch (e: InterruptedException) { throw e }
                    catch (e: StorageFailure) { throw e }
                    catch (e: Exception) {
                        spendSnapshot = null
                        ledger?.let { funds(token, it.state(chain.downloadedHeight, false, "Peer disconnected; reconnecting before spending")) }
                        if (current(token)) status("Peer unavailable: ${e.message?.take(120) ?: "connection failed"}. Trying another peer.")
                    } finally {
                        session.close()
                        if (activePeer === session) activePeer = null
                    }
                }
                if (current(token)) Thread.sleep(30_000)
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (e: Exception) {
            spendSnapshot = null
            funds(token, FundsState(message = "Wallet scan stopped: ${e.message?.take(120)}"))
            publish(token, SyncStatus("Sync stopped: ${e.message?.take(160) ?: "storage error"}. Reopen the app to retry."))
        }
    }

    override fun close() { stop(); worker.shutdownNow(); dns.shutdownNow(); comparison.close() }
    private class StorageFailure(cause: Exception) : Exception("Cannot save header progress", cause)
    private companion object {
        // Activity recreation must not replay a journal while the old worker finishes a write.
        val storageLock = ReentrantLock()
    }
}
