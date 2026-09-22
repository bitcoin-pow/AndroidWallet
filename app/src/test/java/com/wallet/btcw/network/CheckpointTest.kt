package com.wallet.btcw.network

import org.junit.Assert.*
import org.junit.Test

class CheckpointTest {
    @Test fun matchesEveryCheckpointInPinnedNodeSource() {
        val source = javaClass.getResourceAsStream("/mainnet-checkpoints.cpp.txt")!!.bufferedReader().use { it.readText() }
        val pattern = Regex("""\{\s*(\d+),\s*uint256S\("(?:0x)?([0-9a-f]{64})"\)""")
        val expected = pattern.findAll(source).associate { it.groupValues[1].toInt() to it.groupValues[2] } +
            (0 to BtcwNetwork.genesisHash)
        assertEquals(22, expected.size)
        assertEquals(expected.keys.sorted(), BtcwCheckpoints.heights)
        expected.forEach { (height, hash) ->
            assertEquals(hash, BtcwCheckpoints.displayHash(height))
            val wire = hash.chunked(2).reversed().map { it.toInt(16).toByte() }.toByteArray()
            assertTrue(BtcwCheckpoints.check(height, wire))
            assertThrows(IllegalArgumentException::class.java) { BtcwCheckpoints.check(height, wire.reversedArray()) }
        }
        assertEquals(expected.keys.max(), BtcwCheckpoints.latestHeight)
    }

    @Test fun nonCheckpointHeightsAreNotReportedAsVerified() {
        assertFalse(BtcwCheckpoints.check(1, ByteArray(32)))
        assertFalse(BtcwCheckpoints.check(141411, ByteArray(32)))
        assertThrows(IllegalArgumentException::class.java) { BtcwCheckpoints.check(-1, ByteArray(32)) }
        assertThrows(IllegalArgumentException::class.java) { BtcwCheckpoints.check(1, ByteArray(31)) }
    }

    @Test fun stagesLinkedHeadersButDoesNotTrustDescendants() {
        val chain = CheckpointHeaderChain()
        chain.appendHeaders(batch(chain.tipHash, 9))
        assertEquals(9, chain.downloadedHeight)
        assertEquals(0, chain.checkpointAnchoredHeight)
        val tip = chain.tipHash
        chain.tipHash.fill(0)
        assertArrayEquals(tip, chain.tipHash)
        assertThrows(IllegalArgumentException::class.java) { chain.appendHeaders(batch(tip, 1)) }
        assertEquals(9, chain.downloadedHeight)
        assertArrayEquals(tip, chain.tipHash)
    }

    @Test fun checkpointFailureRollsBackEntireBatch() {
        val chain = CheckpointHeaderChain()
        val genesis = chain.tipHash
        assertThrows(IllegalArgumentException::class.java) { chain.appendHeaders(batch(genesis, 10)) }
        assertEquals(0, chain.downloadedHeight)
        assertEquals(0, chain.checkpointAnchoredHeight)
        assertArrayEquals(genesis, chain.tipHash)
        assertThrows(IllegalArgumentException::class.java) { chain.appendHeaders(batch(ByteArray(32), 1)) }
        chain.appendHeaders(byteArrayOf(0))
        assertEquals(0, chain.downloadedHeight)
    }

    private fun batch(parent: ByteArray, count: Int): ByteArray {
        var previous = parent
        var result = byteArrayOf(count.toByte())
        repeat(count) {
            val header = ByteArray(80)
            previous.copyInto(header, 4)
            result += header + byteArrayOf(0)
            previous = hash256(header)
        }
        return result
    }
}
