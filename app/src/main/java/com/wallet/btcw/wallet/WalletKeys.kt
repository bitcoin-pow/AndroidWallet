package com.wallet.btcw.wallet

import org.bitcoinj.crypto.MnemonicCode
import org.bitcoinj.crypto.HDKeyDerivation
import org.bitcoinj.crypto.ChildNumber
import org.bitcoinj.crypto.DeterministicKey
import org.bitcoinj.core.SegwitAddress
import org.bitcoinj.params.MainNetParams
import java.security.SecureRandom
import java.text.Normalizer
import java.util.Locale

object WalletKeys {
    const val FIRST_ADDRESS_PATH = "m/84'/0'/0'/0/0"

    fun createPhrase(): List<String> {
        val entropy = ByteArray(16).also { SecureRandom().nextBytes(it) }
        return try { MnemonicCode.INSTANCE.toMnemonic(entropy) } finally { entropy.fill(0) }
    }

    fun parsePhrase(text: String): List<String> {
        val words = Normalizer.normalize(text, Normalizer.Form.NFKD).trim().lowercase(Locale.ROOT)
            .split(Regex("\\s+"))
        require(words.size in listOf(12, 15, 18, 21, 24)) { "Invalid phrase length" }
        MnemonicCode.INSTANCE.check(words)
        return words
    }

    fun seed(words: List<String>, passphrase: String = ""): ByteArray {
        MnemonicCode.INSTANCE.check(words)
        return MnemonicCode.toSeed(words, Normalizer.normalize(passphrase, Normalizer.Form.NFKD))
    }

    fun receivingAddress(seed: ByteArray, index: Int = 0): String {
        val key = deriveKey(seed, index)
        // BTCW and Bitcoin share address encoding, not chain identity.
        return SegwitAddress.fromHash(MainNetParams.get(), key.pubKeyHash).toString()
    }

    fun watchAddresses(seed: ByteArray): List<WatchAddress> = listOf(false, true).flatMap { change ->
        (0 until 20).map { index ->
            val key = deriveKey(seed, index, change)
            WatchAddress(SegwitAddress.fromHash(MainNetParams.get(), key.pubKeyHash).toString(), index, change)
        }
    }

    internal fun deriveKey(seed: ByteArray, index: Int = 0, change: Boolean = false): DeterministicKey {
        require(seed.size == 64 && index >= 0)
        var key = HDKeyDerivation.createMasterPrivateKey(seed)
        for (child in listOf(ChildNumber(84, true), ChildNumber(0, true), ChildNumber(0, true),
            ChildNumber(if (change) 1 else 0, false), ChildNumber(index, false))) {
            key = HDKeyDerivation.deriveChildKey(key, child)
        }
        return key
    }
}
