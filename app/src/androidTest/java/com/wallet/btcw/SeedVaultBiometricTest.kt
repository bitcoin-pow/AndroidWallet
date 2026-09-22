package com.wallet.btcw

import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.wallet.btcw.security.SeedVault
import org.junit.Test
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import java.io.File
import java.security.KeyStore
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.crypto.Cipher

/** Opt-in: send emulator fingerprint touches during the two system prompts. */
class SeedVaultBiometricTest {
    @Test fun authenticatedSaveAndReopenRoundTrip() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("vaultBiometricTest") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val id = UUID.randomUUID().toString()
        val file = File(context.cacheDir, "vault-test-$id")
        val alias = "btcw.test.$id"
        val vault = SeedVault(file, alias)
        val seed = ByteArray(64) { it.toByte() }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                val encryption = vault.encryptionCipher()
                authenticate(scenario, encryption) { vault.save(seed, it) }
                assertTrue(vault.exists)
                val unlock = SeedVault(file, alias).prepareUnlock()
                authenticate(scenario, unlock.cipher) { cipher ->
                    val decoded = vault.decrypt(unlock.encrypted, cipher)
                    try { assertArrayEquals(seed, decoded) } finally { decoded.fill(0) }
                }
            }
        } finally {
            seed.fill(0)
            file.delete(); File(file.path + ".new").delete(); File(file.path + ".bak").delete()
            KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(alias) }
        }
    }

    private fun authenticate(scenario: ActivityScenario<MainActivity>, cipher: Cipher, action: (Cipher) -> Unit) {
        val done = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>()
        scenario.onActivity { activity ->
            val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    try { action(result.cryptoObject!!.cipher!!) }
                    catch (error: Throwable) { failure.set(error) }
                    finally { done.countDown() }
                }
                override fun onAuthenticationError(code: Int, message: CharSequence) {
                    failure.set(AssertionError("Biometric test error $code")); done.countDown()
                }
            })
            prompt.authenticate(BiometricPrompt.PromptInfo.Builder().setTitle("BTCW isolated vault test")
                .setNegativeButtonText("Cancel").build(), BiometricPrompt.CryptoObject(cipher))
        }
        assertTrue("Biometric test timed out", done.await(35, TimeUnit.SECONDS))
        failure.get()?.let { throw AssertionError("Authenticated vault operation failed", it) }
    }
}
