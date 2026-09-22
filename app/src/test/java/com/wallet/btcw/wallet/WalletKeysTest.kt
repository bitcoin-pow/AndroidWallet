package com.wallet.btcw.wallet

import org.junit.Assert.*
import org.junit.Test

class WalletKeysTest {
    private val phrase = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"

    @Test fun matchesBip39SeedVector() {
        val seed = WalletKeys.seed(WalletKeys.parsePhrase(phrase), "TREZOR")
        try {
            assertEquals("c55257c360c07c72029aebc1b53c05ed0362ada38ead3e3e9efa3708e53495531f09a6987599d18264c1e1c92f2cf141630c7a3c4ab7c81b2f001698e7463b04",
                seed.joinToString("") { "%02x".format(it.toInt() and 255) })
        } finally { seed.fill(0) }
    }

    @Test fun matchesBip84ReceivingAddressVectors() {
        val seed = WalletKeys.seed(WalletKeys.parsePhrase(phrase))
        try {
            assertEquals("bc1qcr8te4kr609gcawutmrza0j4xv80jy8z306fyu", WalletKeys.receivingAddress(seed))
            assertEquals("bc1qnjg0jd8228aq7egyzacy8cys3knf9xvrerkf9g", WalletKeys.receivingAddress(seed, 1))
        } finally { seed.fill(0) }
    }

    @Test fun rejectsChecksumUnknownWordAndWrongLength() {
        for (bad in listOf(List(12) { "abandon" }.joinToString(" "), phrase.replace("about", "notaword"), "abandon about")) {
            assertThrows(Exception::class.java) { WalletKeys.parsePhrase(bad) }
        }
    }

    @Test fun restoreNormalizesWhitespaceAndPassphraseUnicode() {
        assertEquals(WalletKeys.parsePhrase(phrase), WalletKeys.parsePhrase("  " + phrase.uppercase().replace(" ", "\n ") + "  "))
        val words = WalletKeys.parsePhrase(phrase)
        val a = WalletKeys.seed(words, "é")
        val b = WalletKeys.seed(words, "e\u0301")
        try { assertArrayEquals(a, b) } finally { a.fill(0); b.fill(0) }
    }

    @Test fun createsValidIndependentPhrases() {
        val first = WalletKeys.createPhrase()
        val second = WalletKeys.createPhrase()
        assertEquals(12, first.size)
        assertEquals(first, WalletKeys.parsePhrase(first.joinToString(" ")))
        assertEquals(second, WalletKeys.parsePhrase(second.joinToString(" ")))
        assertNotEquals(first, second)
    }
}
