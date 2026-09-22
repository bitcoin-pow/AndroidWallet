package com.wallet.btcw.security

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import com.wallet.btcw.network.BtcwNetwork
import com.wallet.btcw.wallet.WalletKeys

/** Seed only; mnemonic/passphrase never goes to disk. Auth required for every use. */
class SeedVault internal constructor(private val file: File, private val keyAlias: String) {
    constructor(context: Context) : this(File(context.noBackupFilesDir, "wallet-seed-v1.bin"), "btcw.seed.aes.v1")
    private val atomic = AtomicFile(file)
    val exists: Boolean get() = file.exists() || File(file.path + ".bak").exists()
    private val aad = "BTCW|${BtcwNetwork.genesisHash}|${WalletKeys.FIRST_ADDRESS_PATH}|1".toByteArray()

    fun encryptionCipher(): Cipher {
        check(!exists) { "A wallet already exists" }
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        // An uncommitted setup may leave an orphan key. Never replace a saved wallet's key.
        if (store.containsAlias(keyAlias)) store.deleteEntry(keyAlias)
        val builder = KeyGenParameterSpec.Builder(keyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(true).setInvalidatedByBiometricEnrollment(true)
        if (Build.VERSION.SDK_INT >= 30) builder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
        else {
            @Suppress("DEPRECATION")
            builder.setUserAuthenticationValidityDurationSeconds(-1)
        }
        val key = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(builder.build())
        }.generateKey()
        return Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key) }
    }

    fun save(seed: ByteArray, authenticatedCipher: Cipher) {
        check(!exists)
        require(seed.size == 64)
        val iv = authenticatedCipher.iv
        // updateAAD touches Keystore too. Calling it before the biometric prompt
        // caches an unauthenticated-operation error, even if authentication later succeeds.
        authenticatedCipher.updateAAD(aad)
        val encrypted = authenticatedCipher.doFinal(seed)
        require(iv.size == 12 && encrypted.size == 80)
        val stream = atomic.startWrite()
        try {
            stream.write(byteArrayOf(1) + iv + encrypted)
            atomic.finishWrite(stream)
        } catch (e: Exception) { atomic.failWrite(stream); throw e }
    }

    data class Unlock(val cipher: Cipher, val encrypted: ByteArray)

    fun prepareUnlock(): Unlock {
        val bytes = atomic.openRead().use { input ->
            val result = ByteArray(93)
            var offset = 0
            while (offset < result.size) {
                val count = input.read(result, offset, result.size - offset)
                require(count > 0) { "Truncated seed vault" }
                offset += count
            }
            require(input.read() == -1 && result[0] == 1.toByte()) { "Invalid seed vault" }
            result
        }
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val key = store.getKey(keyAlias, null) as? SecretKey ?: error("Wallet key unavailable")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, bytes.copyOfRange(1, 13)))
        }
        return Unlock(cipher, bytes.copyOfRange(13, 93))
    }

    fun decrypt(encrypted: ByteArray, authenticatedCipher: Cipher): ByteArray {
        authenticatedCipher.updateAAD(aad)
        val seed = authenticatedCipher.doFinal(encrypted)
        if (seed.size != 64) {
            seed.fill(0)
            error("Invalid seed size")
        }
        return seed
    }

}
