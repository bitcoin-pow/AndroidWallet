package com.wallet.btcw.wallet

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.os.Handler
import android.os.Looper
import com.wallet.btcw.security.SeedVault
import java.util.concurrent.Executors
import javax.crypto.Cipher
import com.wallet.btcw.network.PeerSync

enum class WalletStage { Welcome, Backup, Confirm, Restore, Busy, Locked, Ready }
data class WalletState(
    val stage: WalletStage,
    val words: List<String> = emptyList(),
    val address: String = "",
    val error: String? = null,
    val watch: List<WatchAddress> = emptyList(),
)

data class SendReview(val recipient: String, val amount: Long, val fee: Long, val change: Long)
data class SendState(val busy: Boolean = false, val review: SendReview? = null, val message: String? = null)

/** Secrets are transient, never saved in Compose state restoration or preferences. */
class WalletController(private val activity: FragmentActivity) : AutoCloseable {
    private val vault = SeedVault(activity)
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var generation = 0
    private var pending: Draft? = null
    private var prompt: BiometricPrompt? = null
    private var peer: PeerSync? = null
    private var approvedPlan: TransactionSigner.Plan? = null
    private var approvedSnapshot: PeerSync.SpendSnapshot? = null
    var sendState by mutableStateOf(SendState())
        private set
    fun attachPeer(value: PeerSync) { peer = value }
    var state by mutableStateOf(WalletState(if (vault.exists) WalletStage.Locked else WalletStage.Welcome))
        private set

    fun create() {
        if (vault.exists || state.stage != WalletStage.Welcome || !biometricsAvailable()) return
        work("Could not generate a recovery phrase.", { WalletKeys.createPhrase() }) {
            state = WalletState(WalletStage.Backup, words = it)
        }
    }

    fun showRestore() {
        if (!vault.exists && state.stage == WalletStage.Welcome && biometricsAvailable()) state = WalletState(WalletStage.Restore)
    }

    fun showConfirmation() {
        if (state.stage == WalletStage.Backup) state = state.copy(stage = WalletStage.Confirm, error = null)
    }

    fun confirm(answers: List<String>) {
        if (state.stage != WalletStage.Confirm) return
        if (answers.size != 3 || listOf(0, 5, 11).map { state.words[it] } != answers.map { it.trim().lowercase(java.util.Locale.ROOT) }) {
            state = state.copy(error = "Those words do not match. Check your written backup.")
            return
        }
        prepareSave(state.words.joinToString(" "), "")
    }

    fun restore(phrase: String, passphrase: String) {
        if (state.stage == WalletStage.Restore) prepareSave(phrase, passphrase)
    }

    private fun prepareSave(phrase: String, passphrase: String) {
        if (vault.exists) return
        work("Could not prepare wallet. Check the phrase's words and checksum, and your biometric settings.", {
            val words = WalletKeys.parsePhrase(phrase)
            val seed = WalletKeys.seed(words, passphrase)
            try { Draft(seed, WalletKeys.watchAddresses(seed), vault.encryptionCipher()) }
            catch (e: Exception) { seed.fill(0); throw e }
        }, discard = { it.seed.fill(0) }) { draft ->
            pending = draft
            authenticate(draft.cipher, "Protect your wallet") { cipher ->
                try {
                    vault.save(draft.seed, cipher)
                    state = WalletState(WalletStage.Ready, address = draft.watch[0].address, watch = draft.watch)
                } catch (error: Exception) {
                    val reason = when (error) {
                        is java.security.GeneralSecurityException -> "Secure encryption failed. Retry biometric authentication."
                        is java.io.IOException -> "The encrypted wallet could not be written to device storage. Check available space."
                        else -> "Wallet save failed (${error.javaClass.simpleName})."
                    }
                    state = idle("$reason Keep your recovery phrase.")
                } finally { draft.seed.fill(0); pending = null }
            }
        }
    }

    fun unlock() {
        if (state.stage != WalletStage.Locked || !biometricsAvailable()) return
        work("Wallet key unavailable or vault damaged. Your recovery phrase is needed to restore on a fresh installation.", {
            vault.prepareUnlock()
        }) { request ->
            authenticate(request.cipher, "Unlock BTCW") { cipher ->
                val seed = try { vault.decrypt(request.encrypted, cipher) } catch (_: Exception) {
                    state = idle("Could not decrypt the wallet. Keep your recovery phrase for recovery.")
                    return@authenticate
                }
                work("Could not derive the receiving address.", {
                    try { WalletKeys.watchAddresses(seed) } finally { seed.fill(0) }
                }) { state = WalletState(WalletStage.Ready, address = it[0].address, watch = it) }
            }
        }
    }

    fun reviewSend(recipient: String, amount: String, feeRate: String) {
        if (state.stage != WalletStage.Ready || sendState.busy) return
        val token = generation
        val addresses = state.watch
        sendState = SendState(busy = true)
        approvedPlan = null; approvedSnapshot = null
        worker.execute {
            try {
                val snapshot = peer?.snapshot() ?: error("Peer sync unavailable")
                require(snapshot.wallet == addresses[0].address)
                val change = addresses.first { it.change && it.index == 0 }.address
                val plan = TransactionSigner.plan(snapshot.inputs, recipient.trim(), TransactionSigner.parseAmount(amount), feeRate.toLong(), change)
                main.post {
                    if (generation == token) {
                        approvedPlan = plan; approvedSnapshot = snapshot
                        sendState = SendState(review = SendReview(plan.recipient, plan.amount, plan.fee, plan.change))
                    }
                }
            } catch (e: Exception) {
                main.post { if (generation == token) sendState = SendState(message = e.message?.take(160) ?: "Could not prepare transaction") }
            }
        }
    }

    fun cancelSend() {
        if (sendState.busy) return
        approvedPlan = null; approvedSnapshot = null; sendState = SendState()
    }

    fun confirmSend() {
        val plan = approvedPlan ?: return
        val snapshot = approvedSnapshot ?: return
        if (state.stage != WalletStage.Ready || sendState.busy) return
        val token = generation
        sendState = sendState.copy(busy = true)
        worker.execute {
            try {
                require(peer?.snapshot()?.tip == snapshot.tip) { "Chain changed; review again" }
                val unlock = vault.prepareUnlock()
                main.post {
                    if (generation != token) return@post
                    authenticate(unlock.cipher, "Authorize BTCW payment") { cipher ->
                        val seed = try { vault.decrypt(unlock.encrypted, cipher) } catch (_: Exception) {
                            sendState = SendState(message = "Could not unlock signing key"); return@authenticate
                        }
                        worker.execute {
                            try {
                                val signed = TransactionSigner.sign(seed, plan)
                                main.post {
                                    if (generation == token) {
                                        val network = peer ?: return@post
                                        network.enqueue(snapshot, signed) { result ->
                                            if (generation == token) sendState = SendState(message = result)
                                        }
                                        approvedPlan = null; approvedSnapshot = null
                                        sendState = SendState(busy = true, message = "Reserving inputs and submitting transaction…")
                                    }
                                }
                            } catch (_: Exception) {
                                main.post { if (generation == token) sendState = SendState(message = "Signing failed. No transaction was sent; review again.") }
                            } finally { seed.fill(0) }
                        }
                    }
                }
            } catch (e: Exception) {
                main.post { if (generation == token) sendState = SendState(message = e.message?.take(160) ?: "Could not authorize payment") }
            }
        }
    }

    private fun authenticate(cipher: Cipher, title: String, success: (Cipher) -> Unit) {
        val token = generation
        val auth = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                if (token != generation) return
                prompt = null
                val authenticated = result.cryptoObject?.cipher
                if (authenticated !== cipher) { lock("Authentication could not unlock the key."); return }
                success(authenticated)
            }
            override fun onAuthenticationError(code: Int, message: CharSequence) {
                if (token == generation) lock("Authentication canceled or unavailable. No wallet was unlocked.")
            }
        })
        prompt = auth
        try {
            auth.authenticate(BiometricPrompt.PromptInfo.Builder().setTitle(title)
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .setNegativeButtonText("Cancel").build(), BiometricPrompt.CryptoObject(cipher))
        } catch (_: Exception) { lock("Biometric authentication is unavailable.") }
    }

    private fun biometricsAvailable(): Boolean {
        val available = BiometricManager.from(activity).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
        if (!available) state = state.copy(error = "Enroll a strong biometric (such as a fingerprint) in Android Settings before creating or unlocking a wallet.")
        return available
    }

    private fun idle(error: String? = null) = WalletState(if (vault.exists) WalletStage.Locked else WalletStage.Welcome, error = error)

    fun lock(error: String? = null) {
        generation++
        prompt?.cancelAuthentication(); prompt = null
        pending?.seed?.fill(0); pending = null
        approvedPlan = null; approvedSnapshot = null; sendState = SendState()
        state = idle(error)
    }

    private fun <T> work(error: String, action: () -> T, discard: (T) -> Unit = {}, success: (T) -> Unit) {
        val token = ++generation
        state = WalletState(WalletStage.Busy)
        worker.execute {
            try {
                val result = action()
                main.post { if (token == generation) success(result) else discard(result) }
            } catch (_: Exception) {
                main.post { if (token == generation) state = idle(error) }
            }
        }
    }

    override fun close() { lock(); worker.shutdown() }
    private data class Draft(val seed: ByteArray, val watch: List<WatchAddress>, val cipher: Cipher)
}
