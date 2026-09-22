package com.wallet.btcw.ui

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.wallet.btcw.wallet.WalletController
import com.wallet.btcw.wallet.WalletKeys
import com.wallet.btcw.wallet.WalletStage

@Composable
fun WalletSetup(controller: WalletController) {
    val state = controller.state
    val context = LocalContext.current
    BackHandler(state.stage in listOf(WalletStage.Backup, WalletStage.Confirm, WalletStage.Restore)) { controller.lock() }
    Scaffold(topBar = { BrandHeader() }) { insets ->
    Column(Modifier.fillMaxSize().padding(insets).imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        when (state.stage) {
            WalletStage.Welcome -> {
                Spacer(Modifier.height(20.dp))
                CoinMark(Modifier.size(72.dp))
                Text("Your BitcoinPoW.\nYour wallet.", style = MaterialTheme.typography.headlineLarge)
                Text("Create a wallet or restore one from your recovery phrase.")
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Private keys. Protected here.", style = MaterialTheme.typography.titleMedium)
                        Text("Encrypted on your device. Secured with biometrics. Backed up by your recovery phrase.", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Button(onClick = controller::create, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Create wallet") }
                OutlinedButton(onClick = controller::showRestore, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Restore wallet") }
                Text("Your wallet connects directly to BTCW peers. Keep the app open on Wi-Fi for the first scan.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS)) }) { Text("Biometric settings") }
            }
            WalletStage.Backup -> {
                Text("Write down your recovery phrase", style = MaterialTheme.typography.titleLarge)
                Text("Write these 12 words in order and keep them offline. Anyone with them can access your funds. They cannot be shown again after setup.")
                state.words.chunked(3).forEachIndexed { row, words ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        words.forEachIndexed { column, word ->
                            Surface(Modifier.weight(1f), shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceVariant) {
                                Column(Modifier.padding(12.dp)) {
                                    Text("${row * 3 + column + 1}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(word, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
                Text("Record: BTCW · BIP84 · ${WalletKeys.FIRST_ADDRESS_PATH} · no passphrase. Use a new phrase dedicated to BTCW.")
                Text("Leaving the app cancels unfinished setup. Your phrase is not saved yet.")
                Button(onClick = controller::showConfirmation) { Text("I wrote down all 12 words") }
                TextButton(onClick = { controller.lock() }) { Text("Cancel setup") }
            }
            WalletStage.Confirm -> {
                var first by remember { mutableStateOf("") }
                var sixth by remember { mutableStateOf("") }
                var last by remember { mutableStateOf("") }
                Text("Confirm your backup", style = MaterialTheme.typography.titleLarge)
                SecretField(first, { first = it }, "Word 1")
                SecretField(sixth, { sixth = it }, "Word 6")
                SecretField(last, { last = it }, "Word 12")
                Button(onClick = { controller.confirm(listOf(first, sixth, last)) }) { Text("Confirm and secure wallet") }
                TextButton(onClick = { controller.lock() }) { Text("Cancel setup") }
            }
            WalletStage.Restore -> {
                var phrase by remember { mutableStateOf("") }
                var passphrase by remember { mutableStateOf("") }
                Text("Restore a BIP39 wallet", style = MaterialTheme.typography.titleLarge)
                Text("Enter 12, 15, 18, 21, or 24 English words. This restores this app's BIP84 account; it does not import a Core wallet.dat file.")
                SecretField(phrase, { phrase = it }, "Recovery phrase", singleLine = false)
                SecretField(passphrase, { passphrase = it }, "BIP39 passphrase (optional)")
                Text("A different passphrase opens a different wallet; a typo cannot be detected. Leave blank for a wallet created here.")
                Text("Derivation: ${WalletKeys.FIRST_ADDRESS_PATH}. Recovery scans from block 141000. Outputs created before that block are not discovered.")
                Button(onClick = { controller.restore(phrase, passphrase) }, enabled = phrase.isNotBlank()) { Text("Restore and secure wallet") }
                TextButton(onClick = { controller.lock() }) { Text("Cancel") }
            }
            WalletStage.Locked -> {
                Spacer(Modifier.height(32.dp))
                CoinMark(Modifier.size(80.dp).align(Alignment.CenterHorizontally))
                Text("Welcome back", style = MaterialTheme.typography.headlineLarge)
                Text("Your wallet is locked", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Unlock securely to view your balance, receive BTCW or make a payment.")
                Button(onClick = controller::unlock, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Unlock with biometrics") }
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Text("Keep your recovery phrase. Changing biometric enrollment can invalidate this device's encryption key.", Modifier.padding(20.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS)) }) { Text("Biometric settings") }
            }
            WalletStage.Busy -> { CircularProgressIndicator(); Text("Preparing secure wallet…") }
            WalletStage.Ready -> Unit
        }
    }
    }
}

@Composable
private fun SecretField(value: String, change: (String) -> Unit, label: String, singleLine: Boolean = true) {
    OutlinedTextField(value, change, label = { Text(label) }, singleLine = singleLine,
        modifier = Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password))
}
