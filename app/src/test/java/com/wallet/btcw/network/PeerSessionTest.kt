package com.wallet.btcw.network

import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class PeerSessionTest {
    @Test fun handshakePingAndConsecutiveHeaderRequestsOverTcp() {
        withPeer({ socket ->
            assertEquals("version", PeerMessage.read(socket.getInputStream()).command)
            send(socket, "version", servingVersion())
            assertEquals("verack", PeerMessage.read(socket.getInputStream()).command)
            send(socket, "verack")
            val request = PeerMessage.read(socket.getInputStream())
            assertEquals("getheaders", request.command)
            assertArrayEquals(PeerProtocol.getHeaders(ByteArray(32)), request.payload)
            val nonce = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
            send(socket, "ping", nonce)
            val pong = PeerMessage.read(socket.getInputStream())
            assertEquals("pong", pong.command)
            assertArrayEquals(nonce, pong.payload)
            send(socket, "headers", byteArrayOf(1) + ByteArray(80) + byteArrayOf(0))
            assertEquals("getheaders", PeerMessage.read(socket.getInputStream()).command)
            send(socket, "headers", byteArrayOf(0))
        }) { client, address ->
            assertEquals(100, client.connect(address, 0).height)
            assertEquals(1, BtcwHeader.parseHeaders(client.headers(ByteArray(32))).size)
            assertTrue(BtcwHeader.parseHeaders(client.headers(ByteArray(32))).isEmpty())
        }
    }

    @Test fun rejectsVerackBeforeVersion() {
        withPeer({ socket ->
            PeerMessage.read(socket.getInputStream())
            send(socket, "verack")
        }) { client, address ->
            assertThrows(IllegalArgumentException::class.java) { client.connect(address, 0) }
        }
    }

    @Test fun rejectsSelfConnectionAndNonServingPeer() {
        assertThrows(IllegalArgumentException::class.java) { PeerProtocol.readVersion(servingVersion(), 42) }
        assertThrows(IllegalArgumentException::class.java) { PeerProtocol.readVersion(PeerProtocol.version(42, 0), 43) }
        assertThrows(IllegalArgumentException::class.java) { PeerProtocol.readVersion(servingVersion().copyOf(40), 43) }
    }

    @Test fun closeUnblocksPendingHeaderRead() {
        val ready = java.util.concurrent.CountDownLatch(1)
        withPeer({ socket ->
            PeerMessage.read(socket.getInputStream())
            send(socket, "version", servingVersion())
            PeerMessage.read(socket.getInputStream())
            send(socket, "verack")
            PeerMessage.read(socket.getInputStream())
            ready.countDown()
            assertEquals(-1, socket.getInputStream().read())
        }) { client, address ->
            client.connect(address, 0)
            val executor = Executors.newSingleThreadExecutor()
            try {
                val read = executor.submit<Boolean> {
                    try { client.headers(ByteArray(32)); false } catch (_: Exception) { true }
                }
                assertTrue(ready.await(3, TimeUnit.SECONDS))
                client.close()
                assertTrue(read.get(3, TimeUnit.SECONDS))
            } finally { executor.shutdownNow() }
        }
    }

    private fun servingVersion(): ByteArray = PeerProtocol.version(42, 100).also {
        ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).putLong(4, 1)
    }

    private fun send(socket: Socket, command: String, payload: ByteArray = byteArrayOf()) {
        socket.getOutputStream().write(PeerMessage(command, payload).encode())
    }

    private fun withPeer(server: (Socket) -> Unit, client: (PeerSession, InetSocketAddress) -> Unit) {
        ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress()).use { listener ->
            val executor = Executors.newSingleThreadExecutor()
            val job = executor.submit { listener.accept().use { it.soTimeout = 4000; server(it) } }
            try {
                PeerSession().use { client(it, InetSocketAddress(listener.inetAddress, listener.localPort)) }
                job.get(5, TimeUnit.SECONDS)
            } finally { executor.shutdownNow() }
        }
    }
}
