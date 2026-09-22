package com.wallet.btcw.network

import org.junit.Assert.*
import org.junit.Test

class PeerMessageTest {
    @Test fun readsConsecutiveFramesWithoutConsumingNextMessage() {
        val stream = (PeerMessage("ping", ByteArray(8) { it.toByte() }).encode() +
            PeerMessage("verack", byteArrayOf()).encode()).inputStream()
        val ping = PeerMessage.read(stream)
        assertEquals("ping", ping.command)
        assertArrayEquals(ByteArray(8) { it.toByte() }, ping.payload)
        assertEquals("verack", PeerMessage.read(stream).command)
        assertEquals(-1, stream.read())
    }

    @Test fun rejectsWrongNetworkChecksumPaddingOversizeAndTruncation() {
        val valid = PeerMessage("ping", ByteArray(8)).encode()
        val malformed = listOf(
            valid.copyOf().also { it[0] = 0 },
            valid.copyOf().also { it[20] = (it[20].toInt() xor 1).toByte() },
            valid.copyOf().also { it[15] = 1 },
            valid.copyOf().also { it[19] = 0x7f },
            valid.copyOf(23), valid.copyOf(valid.size - 1),
        )
        malformed.forEach { frame ->
            assertThrows(IllegalArgumentException::class.java) { PeerMessage.read(frame.inputStream()) }
        }
    }

    @Test fun matchesKnownEmptyPayloadChecksum() {
        val encoded = PeerMessage("verack", byteArrayOf()).encode()
        assertArrayEquals(byteArrayOf(0x5d, 0xf6.toByte(), 0xe0.toByte(), 0xe2.toByte()), encoded.copyOfRange(20, 24))
    }
}
