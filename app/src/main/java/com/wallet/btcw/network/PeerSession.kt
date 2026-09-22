package com.wallet.btcw.network

import java.net.InetSocketAddress
import java.net.Socket
import java.security.SecureRandom
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Blocking transport. Run on an IO worker; close() cancels reads and writes. */
internal class PeerSession(private val socket: Socket = Socket()) : AutoCloseable {
    private val deadline = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "btcw-peer-deadline").apply { isDaemon = true }
    }
    private val nonce = SecureRandom().nextLong()
    private var ready = false

    fun connect(address: InetSocketAddress, height: Int): PeerProtocol.Version = timed {
        socket.connect(address, 8_000)
        socket.soTimeout = 15_000
        send("version", PeerProtocol.version(nonce, height))
        var version: PeerProtocol.Version? = null
        repeat(64) {
            val message = PeerMessage.read(socket.getInputStream())
            when (message.command) {
                "version" -> {
                    require(version == null) { "Duplicate peer version" }
                    version = PeerProtocol.readVersion(message.payload, nonce)
                    send("verack")
                }
                "verack" -> {
                    require(version != null && message.payload.isEmpty()) { "Unexpected verack" }
                    ready = true
                    return@timed version!!
                }
                "ping" -> pong(message)
                "reject" -> error("Peer rejected handshake")
            }
        }
        error("Peer handshake message limit exceeded")
    }

    fun headers(tip: ByteArray): ByteArray = timed {
        check(ready)
        send("getheaders", PeerProtocol.getHeaders(tip))
        repeat(128) {
            val message = PeerMessage.read(socket.getInputStream())
            when (message.command) {
                "headers" -> return@timed message.payload
                "ping" -> pong(message)
                "version", "verack", "reject" -> error("Unexpected peer message: ${message.command}")
            }
        }
        error("Header response message limit exceeded")
    }

    fun blocks(headers: List<BtcwHeader>): List<FullBlock> = timed {
        check(ready)
        require(headers.size in 1..8)
        val request = ByteBuffer.allocate(1 + headers.size * 36).order(ByteOrder.LITTLE_ENDIAN)
        request.put(headers.size.toByte())
        headers.forEach { request.putInt(0x40000002); request.put(it.hash) } // MSG_WITNESS_BLOCK
        send("getdata", request.array())
        val remaining = headers.associateBy { it.hash.toList() }.toMutableMap()
        val received = mutableMapOf<List<Byte>, FullBlock>()
        repeat(128) {
            val message = PeerMessage.read(socket.getInputStream())
            when (message.command) {
                "block" -> {
                    val header = BtcwHeader.read(WireReader(message.payload))
                    val expected = remaining.remove(header.hash.toList()) ?: error("Unrequested block")
                    received[header.hash.toList()] = FullBlock.parse(message.payload, expected)
                    if (remaining.isEmpty()) return@timed headers.map { received.getValue(it.hash.toList()) }
                }
                "ping" -> pong(message)
                "notfound", "reject" -> error("Peer cannot provide requested blocks")
            }
        }
        error("Block response message limit exceeded")
    }

    /** A pong proves delivery only; confirmation comes from a later scanned block. */
    fun submitTransaction(raw: ByteArray) = timed {
        check(ready)
        require(raw.size in 10..400_000)
        send("tx", raw)
        val ping = ByteArray(8).also { SecureRandom().nextBytes(it) }
        send("ping", ping)
        repeat(128) {
            val message = PeerMessage.read(socket.getInputStream())
            when (message.command) {
                "pong" -> if (message.payload.contentEquals(ping)) return@timed
                "ping" -> pong(message)
                "reject" -> error("Peer rejected transaction; inputs remain reserved")
            }
        }
        error("Transaction delivery was not acknowledged")
    }

    private fun pong(message: PeerMessage) {
        require(message.payload.size == 8) { "Invalid ping" }
        send("pong", message.payload)
    }

    private fun send(command: String, payload: ByteArray = byteArrayOf()) {
        socket.getOutputStream().apply { write(PeerMessage(command, payload).encode()); flush() }
    }

    private fun <T> timed(action: () -> T): T {
        // Absolute deadline also stops slow trickle reads and blocked writes.
        val timer = deadline.schedule({ runCatching { socket.close() } }, 30, TimeUnit.SECONDS)
        try { return action() } finally { timer.cancel(false) }
    }

    override fun close() {
        runCatching { socket.close() }
        deadline.shutdownNow()
    }
}
