package com.wallet.btcw

import androidx.test.platform.app.InstrumentationRegistry
import com.wallet.btcw.wallet.*
import com.wallet.btcw.network.*
import org.bitcoinj.core.*
import org.bitcoinj.crypto.TransactionSignature
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.script.Script
import org.bitcoinj.script.ScriptBuilder
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.net.ServerSocket
import java.net.InetSocketAddress
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Disposable fixture funds and loopback peer only; never broadcasts on mainnet. */
class WalletPipelineTest {
    @Test fun scanBoundaryMigratesOldProgressAndResumesNewProgress() {
        val seed = ByteArray(64) { (it + 2).toByte() }
        val watch = WalletKeys.watchAddresses(seed)
        val directory = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
        val file = File.createTempFile("scan-boundary", ".json", directory)
        try {
            file.writeText(org.json.JSONObject().put("version", 1).put("wallet", watch[0].address)
                .put("height", 500).put("hash", "old-hash")
                .put("outputs", org.json.JSONArray()).put("history", org.json.JSONArray())
                .put("pending", org.json.JSONObject()).toString())
            val start = WalletLedger.SCAN_START_HEIGHT
            val ledger = WalletLedger(file, watch)
            assertEquals(140999, ledger.height)
            assertTrue(ledger.blockHash.isEmpty())
            assertThrows(IllegalArgumentException::class.java) { ledger.apply(start - 1, "too-early", emptyList()) }
            ledger.apply(start, "first-scanned", emptyList())
            ledger.save()
            val resumed = WalletLedger(file, watch)
            assertEquals(start, resumed.height)
            assertEquals("first-scanned", resumed.blockHash)
            resumed.apply(start + 1, "next-block", emptyList())
            resumed.resetForReorg()
            assertEquals(start - 1, WalletLedger(file, watch).height)
        } finally { seed.fill(0); file.delete(); File(file.path + ".bak").delete() }
    }

    @Test fun receiveReviewSignPersistBroadcastAndConfirm() {
        val seed = ByteArray(64) { (it + 1).toByte() }
        val watch = WalletKeys.watchAddresses(seed)
        val directory = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
        val file = File.createTempFile("pipeline", ".json", directory).apply { delete() }
        try {
            val start = WalletLedger.SCAN_START_HEIGHT
            fun h(offset: Int) = start + offset - 1
            var ledger = WalletLedger(file, watch)
            assertEquals(start - 1, ledger.height)
            val funding = Transaction(MainNetParams.get()).apply {
                addInput(Sha256Hash.wrap(ByteArray(32) { 1 }), 0, Script(byteArrayOf()))
                addOutput(Coin.valueOf(100_000), Address.fromString(MainNetParams.get(), watch[0].address))
            }
            ledger.apply(h(1), "block-1", listOf(funding))
            assertEquals(0, ledger.inputs(h(0)).size)
            assertEquals(0L, ledger.state(h(0), true, "unconfirmed").available)
            assertEquals(0L, ledger.state(h(1), false, "scanning").available)
            assertEquals(1, ledger.inputs(h(1)).size)
            assertEquals(100_000L, ledger.state(h(1), true, "test").available)
            val plan = TransactionSigner.plan(ledger.inputs(h(1)), "bc1qcr8te4kr609gcawutmrza0j4xv80jy8z306fyu", 70_000, 2,
                watch.first { it.change && it.index == 0 }.address)
            val signed = TransactionSigner.sign(seed, plan)
            assertEquals(plan.fee, signed.fee)
            ledger.queue(signed)
            assertEquals(0L, ledger.state(h(1), true, "test").available)
            ledger = WalletLedger(file, watch)
            assertEquals(signed.hex, ledger.queued()[signed.txid])
            assertThrows(IllegalArgumentException::class.java) { ledger.queue(signed) }
            loopbackBroadcast(signed.hex.hexBytes(), seed)
            ledger.submitted(signed.txid)
            assertEquals("Submitted to peer; unconfirmed", ledger.state(h(1), true, "test").transfers.first().status)
            val sent = Transaction(MainNetParams.get(), signed.hex.hexBytes())
            ledger.apply(h(2), "block-2", listOf(sent)); ledger.save()
            assertTrue(ledger.queued().isEmpty())
            assertEquals(0L, ledger.state(h(2), true, "test").confirming)
            assertEquals(plan.change, ledger.state(h(2), true, "test").available)
            ledger.resetForReorg("Rechecking after rescan")
            assertEquals(start - 1, ledger.height)
            assertEquals(0L, ledger.state(h(2), false, "reorg").available)
            assertEquals(signed.hex, ledger.queued()[signed.txid])
            assertTrue(WalletLedger(file, watch).queued().containsKey(signed.txid))
        } finally { seed.fill(0); file.delete(); File(file.path + ".bak").delete() }
    }

    private fun loopbackBroadcast(raw: ByteArray, seed: ByteArray) {
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { listener ->
            val executor = Executors.newSingleThreadExecutor()
            val server = executor.submit {
                listener.accept().use { socket ->
                    socket.soTimeout = 5000
                    val input = socket.getInputStream(); val output = socket.getOutputStream()
                    assertEquals("version", PeerMessage.read(input).command)
                    val version = PeerProtocol.version(123, 6)
                    ByteBuffer.wrap(version).order(ByteOrder.LITTLE_ENDIAN).putLong(4, 1033)
                    output.write(PeerMessage("version", version).encode())
                    assertEquals("verack", PeerMessage.read(input).command)
                    output.write(PeerMessage("verack", byteArrayOf()).encode())
                    val message = PeerMessage.read(input)
                    assertEquals("tx", message.command)
                    assertArrayEquals(raw, message.payload)
                    val tx = Transaction(MainNetParams.get(), message.payload)
                    val key = WalletKeys.deriveKey(seed)
                    val signature = TransactionSignature.decodeFromBitcoin(tx.inputs[0].witness.getPush(0), true, true)
                    assertTrue(key.verify(tx.hashForWitnessSignature(0, ScriptBuilder.createP2PKHOutputScript(key.pubKeyHash), Coin.valueOf(100_000), Transaction.SigHash.ALL, false), signature))
                    val ping = PeerMessage.read(input)
                    assertEquals("ping", ping.command)
                    output.write(PeerMessage("pong", ping.payload).encode())
                }
            }
            try {
                PeerSession().use { peer ->
                    peer.connect(InetSocketAddress(listener.inetAddress, listener.localPort), 6)
                    peer.submitTransaction(raw)
                }
                server.get(8, TimeUnit.SECONDS)
            } finally { executor.shutdownNow() }
        }
    }
}
