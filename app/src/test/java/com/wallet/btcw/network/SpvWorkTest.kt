package com.wallet.btcw.network

import com.wallet.btcw.wallet.hexBytes
import org.junit.Assert.*
import org.junit.Test
import java.math.BigInteger

class SpvWorkTest {
    @Test fun decodesHistoricalNoncanonicalSequenceLength() {
        // Public mainnet block 141415 signature, excluding its final eight nonce bytes.
        val bytes = "303d021f36c4bcd738ba0e8511ac4b80d45489e7526440b9940f0a242b5fb9c21ea9aa021ec12169d6bcd8ac4cbb68aa944275de142fde558000c6a078af08b5bd7f08c4798377".hexBytes()
        val signature = SpvWork.legacySignature(bytes)
        assertEquals(BigInteger("36c4bcd738ba0e8511ac4b80d45489e7526440b9940f0a242b5fb9c21ea9aa", 16), signature.r)
        assertEquals(240, signature.s.bitLength())
        assertThrows(IllegalArgumentException::class.java) { SpvWork.legacySignature(bytes.copyOf(20)) }
    }

    @Test fun compactTargetRoundTripAndBounds() {
        for (bits in listOf(0x1d00ffffL, 0x1c123456L, 0x03012345L)) assertEquals(bits, SpvWork.compact(SpvWork.target(bits)))
        for (bits in listOf(0L, 0x1d80ffffL, 0x23000001L, 0x2100ffffL)) {
            assertThrows(IllegalArgumentException::class.java) { SpvWork.target(bits) }
        }
    }
}
