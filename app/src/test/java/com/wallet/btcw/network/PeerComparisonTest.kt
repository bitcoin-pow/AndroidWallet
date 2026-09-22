package com.wallet.btcw.network

import java.net.InetAddress
import org.junit.Assert.*
import org.junit.Test

class PeerComparisonTest {
    private fun vote(peer: Int, hash: String) = PeerComparison.Vote(
        InetAddress.getByAddress(byteArrayOf(192.toByte(), 0, 2, peer.toByte())), hash)

    @Test fun choosesUniqueMostFrequentHash() {
        val result = PeerComparison.choose(150000, (1..10).map { vote(it, if (it <= 6) "a" else "b") })
        assertEquals("a", result.winner)
        assertEquals(6, result.supporters.size)
        assertEquals(10, result.responses)
        assertEquals(150000, result.height)
    }
    @Test fun doesNotCountSamePeerTwice() {
        val result = PeerComparison.choose(1, listOf(vote(1, "a"), vote(1, "a"), vote(2, "b")))
        assertNull(result.winner)
        assertEquals(2, result.responses)
    }
    @Test fun tieSingleResponseAndUnavailablePeersHaveNoWinner() {
        for (votes in listOf(emptyList(), listOf(vote(1, "a")), listOf(vote(1, "a"), vote(2, "a"), vote(3, "b"), vote(4, "b")))) {
            assertNull(PeerComparison.choose(1, votes).winner)
        }
    }
    @Test fun canUseAgreementWhenFewerThanTenRespond() {
        assertEquals("a", PeerComparison.choose(1, listOf(vote(1, "a"), vote(2, "a"), vote(3, "b"))).winner)
    }
}
