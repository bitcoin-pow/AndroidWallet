package com.wallet.btcw.network

import com.wallet.btcw.wallet.hexBytes
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class FullBlockTest {
    private fun fixtureHistory(): HeaderHistory {
        val headers = javaClass.getResourceAsStream("/btcw-headers-141369-141415.txt")!!.bufferedReader().useLines { lines ->
            lines.associate { line ->
                val parts = line.split(':')
                parts[0].toInt() to BtcwHeader.read(WireReader(parts[1].hexBytes()))
            }
        }
        return object : HeaderHistory {
            override val checkpointAnchoredHeight = 141410
            override fun headerAt(height: Int) = headers.getValue(height)
        }
    }

    @Test fun verifiesRealBtcwBlockMerkleDifficultyAndLegacyWork() {
        val history = fixtureHistory()
        val bytes = javaClass.getResourceAsStream("/btcw-block-141415.bin")!!.readBytes()
        val block = FullBlock.parse(bytes, history.headerAt(141415))
        assertEquals(history.headerAt(141415).bits, SpvWork.requiredBits(141415, history))
        SpvWork.verify(141415, history, block)
        val corrupt = bytes.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        assertThrows(Exception::class.java) { FullBlock.parse(corrupt, history.headerAt(141415)) }
        assertThrows(Exception::class.java) { FullBlock.parse(bytes.copyOf(bytes.size - 1), history.headerAt(141415)) }
        assertThrows(Exception::class.java) { FullBlock.parse(bytes, history.headerAt(141414)) }
        assertThrows(IllegalArgumentException::class.java) { SpvWork.verify(141415, history, block, 0) }
    }

    @Test fun asertActivationAndHandoffKeepTheReferenceTarget() {
        fun header(time: Long, bits: Long): BtcwHeader {
            val raw = ByteBuffer.allocate(80).order(ByteOrder.LITTLE_ENDIAN)
            raw.position(68); raw.putInt(time.toInt()); raw.putInt(bits.toInt())
            return BtcwHeader.read(WireReader(raw.array()))
        }
        val history = object : HeaderHistory {
            override val checkpointAnchoredHeight = 141410
            override fun headerAt(height: Int) = header(height * 600L, 0x1b123456L)
        }
        val expectedActivation = SpvWork.compact((SpvWork.target(0x1b123456L) * java.math.BigInteger.valueOf(10000))
            .min(java.math.BigInteger("00000000ffffffffffffffffffffffffffffffffffffffffffffffffffffffff", 16)))
        assertEquals(expectedActivation, SpvWork.requiredBits(144444, history))
        assertEquals(0x1b123456L, SpvWork.requiredBits(144588, history))
        assertEquals(0x1b123456L, SpvWork.requiredBits(144600, history))
    }
}
