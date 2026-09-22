package com.wallet.btcw.network

import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Peer agreement is a selection hint, never a new trusted checkpoint. */
internal class PeerComparison : AutoCloseable {
    private val pool = Executors.newFixedThreadPool(10)
    private val sessions = ConcurrentHashMap.newKeySet<PeerSession>()

    data class Vote(val peer: InetAddress, val hash: String)
    data class Result(val height: Int, val responses: Int, val winner: String?, val supporters: Set<InetAddress>)

    fun compare(candidates: List<InetAddress>, chain: CheckpointHeaderChain): Result {
        val height = chain.downloadedHeight
        // Compare the same height, following linked headers from a known ancestor.
        val anchor = minOf(chain.checkpointAnchoredHeight, height - 1)
        require(anchor >= 0)
        val start = chain.hashAt(anchor)
        val jobs = candidates.distinctBy { it.hostAddress }.shuffled().take(10).map { address -> Callable {
            val session = PeerSession()
            sessions.add(session)
            try {
                val version = session.connect(InetSocketAddress(address, BtcwNetwork.port), height)
                require(version.height >= height)
                var cursor = anchor
                var hash = start
                // Bound work when the local tip is far beyond the latest checkpoint.
                var batches = 0
                while (cursor < height && batches++ < 128) {
                    val headers = BtcwHeader.parseHeaders(session.headers(hash))
                    require(headers.isNotEmpty())
                    for (header in headers) {
                        require(header.previousHash.contentEquals(hash))
                        cursor++
                        BtcwCheckpoints.check(cursor, header.hash)
                        hash = header.hash
                        if (cursor == height) break
                    }
                }
                require(cursor == height)
                Vote(address, hash.reversedArray().joinToString("") { "%02x".format(it) })
            } catch (_: Exception) { null }
            finally { sessions.remove(session); session.close() }
        } }
        return try {
            val votes = pool.invokeAll(jobs, 35, TimeUnit.SECONDS).mapNotNull { future ->
                if (future.isCancelled) null else runCatching { future.get() }.getOrNull()
            }
            choose(height, votes)
        } finally { cancel() }
    }

    fun cancel() { sessions.forEach { it.close() } }
    override fun close() { cancel(); pool.shutdownNow() }

    companion object {
        fun choose(height: Int, votes: List<Vote>): Result {
            val unique = votes.distinctBy { it.peer.hostAddress }
            val groups = unique.groupBy { it.hash }.entries.sortedByDescending { it.value.size }
            val first = groups.firstOrNull()
            // A tie or a lone response provides no useful agreement signal.
            val winner = first?.takeIf { it.value.size >= 2 && (groups.size == 1 || it.value.size > groups[1].value.size) }
            return Result(height, unique.size, winner?.key, winner?.value?.map { it.peer }?.toSet() ?: emptySet())
        }
    }
}
