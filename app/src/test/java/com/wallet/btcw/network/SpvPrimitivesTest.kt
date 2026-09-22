package com.wallet.btcw.network

import org.junit.Assert.*
import org.junit.Test

class SpvPrimitivesTest {
    @Test fun parsesLegacyAndAllStakeHeaderForms() {
        for (nonce in listOf(0L, 0xfeedbeefL, 0xfeedbee1L, 0xfeedbee2L)) {
            val base = ByteArray(80)
            repeat(4) { base[76 + it] = (nonce ushr (8 * it)).toByte() }
            val extension = if (nonce == 0L) byteArrayOf() else ByteArray(36) + byteArrayOf(70) + ByteArray(70)
            val encoded = base + extension
            val parsed = BtcwHeader.parseHeaders(byteArrayOf(1) + encoded + byteArrayOf(0)).single()
            assertArrayEquals(encoded, parsed.serialized)
            assertArrayEquals(hash256(encoded), parsed.hash)
            assertEquals(nonce != 0L, parsed.isStake)
        }
    }

    @Test fun rejectsMalformedHeaderMessages() {
        val valid = byteArrayOf(1) + ByteArray(80) + byteArrayOf(0)
        for (payload in listOf(
            valid.dropLast(1).toByteArray(), valid + byteArrayOf(0),
            byteArrayOf(253.toByte(), 1, 0) + valid.drop(1),
            byteArrayOf(1) + ByteArray(80) + byteArrayOf(1),
            byteArrayOf(253.toByte(), 0xd1.toByte(), 7),
        )) {
            assertThrows(IllegalArgumentException::class.java) { BtcwHeader.parseHeaders(payload) }
        }
    }

    @Test fun verifiesMerkleBranchAndRejectsTampering() {
        val a = hash256(byteArrayOf(1))
        val b = hash256(byteArrayOf(2))
        val root = hash256(a + b)
        assertTrue(MerkleProof.verify(a, 0, 2, listOf(b), root))
        assertTrue(MerkleProof.verify(b, 1, 2, listOf(a), root))
        assertFalse(MerkleProof.verify(a, 1, 2, listOf(b), root))
        assertFalse(MerkleProof.verify(a, 0, 2, emptyList(), root))
        assertFalse(MerkleProof.verify(a, 0, 2, listOf(b, b), root))
        assertFalse(MerkleProof.verify(a, 0, 2, listOf(a), hash256(a + a)))
    }

    @Test fun verifiesOddWidthTreeAndSingleLeaf() {
        val a = hash256(byteArrayOf(1))
        val b = hash256(byteArrayOf(2))
        val c = hash256(byteArrayOf(3))
        val ab = hash256(a + b)
        val root = hash256(ab + hash256(c + c))
        assertTrue(MerkleProof.verify(c, 2, 3, listOf(c, ab), root))
        assertFalse(MerkleProof.verify(c, 2, 3, listOf(a, ab), root))
        assertTrue(MerkleProof.verify(a, 0, 1, emptyList(), a))
        assertFalse(MerkleProof.verify(a, 1, 1, emptyList(), a))
    }
}
