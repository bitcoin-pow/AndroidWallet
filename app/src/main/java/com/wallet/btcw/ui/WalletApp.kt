package com.wallet.btcw.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.wallet.btcw.network.BtcwNetwork
import com.wallet.btcw.network.BtcwCheckpoints
import com.wallet.btcw.network.SyncStatus
import androidx.compose.ui.platform.LocalContext
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.wallet.btcw.wallet.WalletKeys
import com.wallet.btcw.wallet.FundsState
import com.wallet.btcw.wallet.SendState
import java.math.BigDecimal

private enum class Screen(val label: String) {
    Home("Home"), Send("Send"), Receive("Receive"),
    Transactions("Activity"), Settings("Settings")
}

@Composable
fun WalletApp(sync: SyncStatus = SyncStatus(), receivingAddress: String = "",
    funds: FundsState = FundsState(), send: SendState = SendState(),
    onReview: (String, String, String) -> Unit = { _, _, _ -> }, onConfirm: () -> Unit = {},
    onCancelSend: () -> Unit = {}, onLock: () -> Unit = {}, onRescan: () -> Unit = {}) {
    val context = LocalContext.current
    var selected by rememberSaveable { mutableStateOf(Screen.Home.name) }
    var recipient by rememberSaveable { mutableStateOf("") }
    var amount by rememberSaveable { mutableStateOf("") }
    var feeRate by rememberSaveable { mutableStateOf("2") }
    val screen = Screen.valueOf(selected)
    BackHandler(screen != Screen.Home) { selected = Screen.Home.name }
    Scaffold(topBar = { BrandHeader() }, bottomBar = {
        NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
            Screen.entries.forEach { destination ->
                NavigationBarItem(
                    selected = screen == destination,
                    onClick = { selected = destination.name },
                    icon = { WalletNavIcon(destination.name) },
                    label = { Text(destination.label, maxLines = 1) },
                )
            }
        }
    }) { insets ->
        Column(
            Modifier.fillMaxSize().padding(insets).imePadding().verticalScroll(rememberScrollState()).padding(24.dp).widthIn(max = 640.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            when (screen) {
                Screen.Home -> {
                    Text("Your wallet", style = MaterialTheme.typography.headlineSmall)
                    Card(shape = RoundedCornerShape(28.dp)) {
                        Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF182B44), Color(0xFF29425D)))).padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("AVAILABLE BALANCE", style = MaterialTheme.typography.labelSmall, color = Color(0xFFD1DDEB))
                                CoinMark()
                            }
                            Text(if (funds.ready) btc(funds.available) else "Scanning…", style = MaterialTheme.typography.headlineLarge, color = Color.White)
                            Text("BTCW · BitcoinPoW", style = MaterialTheme.typography.labelLarge, color = Color(0xFFFFBC70))
                            HorizontalDivider(color = Color(0xFF4B6077))
                            Text("${btc(funds.confirming)} BTCW awaiting confirmations", style = MaterialTheme.typography.bodyMedium, color = Color(0xFFD1DDEB))
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { selected = Screen.Send.name }, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text("Send") }
                        OutlinedButton(onClick = { selected = Screen.Receive.name }, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text("Receive") }
                    }
                    InfoCard("Wallet scan", "${funds.message}\nBlocks ${funds.scannedHeight} / ${funds.chainHeight}")
                    if (!funds.ready && funds.chainHeight > 0) LinearProgressIndicator(progress = { (funds.scannedHeight.toFloat() / funds.chainHeight).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                    SyncCard(sync)
                    Text("Protected on this device with biometric authentication.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Screen.Send -> {
                    Text("Send BTCW", style = MaterialTheme.typography.headlineSmall)
                    Text("Choose an amount. Review every detail before sending.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(value = recipient, onValueChange = { recipient = it; onCancelSend() }, enabled = !send.busy,
                        label = { Text("Recipient address") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = amount, onValueChange = { amount = it; onCancelSend() }, enabled = !send.busy,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        label = { Text("Amount in BTCW") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = feeRate, onValueChange = { feeRate = it; onCancelSend() }, enabled = !send.busy,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        label = { Text("Fee rate (satoshis per virtual byte)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Text("Available: ${btc(funds.available)} BTCW. Only spend BitcoinPoW here, even though Bitcoin uses the same address format.")
                    if (!funds.ready) Text("Finish the wallet scan and connect to a peer before sending.")
                    send.message?.let { Text(it) }
                    if (send.busy) CircularProgressIndicator()
                    Button(onClick = { onReview(recipient, amount, feeRate) }, enabled = funds.ready && funds.available > 0 && !send.busy,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Review transaction") }
                    send.review?.let { review ->
                        InfoCard("Review BTCW payment", "To: ${review.recipient}\nAmount: ${btc(review.amount)} BTCW\nFee: ${btc(review.fee)} BTCW\nTotal: ${btc(review.amount + review.fee)} BTCW\nChange back to wallet: ${btc(review.change)} BTCW")
                        Button(onClick = onConfirm, enabled = funds.ready && !send.busy) { Text("Authenticate and send") }
                        TextButton(onClick = onCancelSend, enabled = !send.busy) { Text("Cancel payment") }
                    }
                }
                Screen.Receive -> {
                    Text("Receive BTCW", style = MaterialTheme.typography.headlineSmall)
                    Text("Your address. Ready to share.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            CoinMark()
                            if (receivingAddress.isNotBlank()) ReceiveQrImage(receivingAddress)
                            Text("BITCOINPOW NETWORK", color = Color(0xFF526071), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    SelectionContainer {
                        Text(receivingAddress.ifBlank { "Unlock a wallet to view its address." }, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
                    }
                    Button(enabled = receivingAddress.isNotBlank(), onClick = {
                        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                            .setPrimaryClip(ClipData.newPlainText("BTCW receiving address", receivingAddress))
                    }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Copy address") }
                    Text("Incoming payments appear after a block is scanned. Spending requires a completed scan and 1 confirmation.")
                    Text("Only receive BitcoinPoW here. BTCW and Bitcoin share address formats.")
                }
                Screen.Transactions -> {
                    Text("Transactions", style = MaterialTheme.typography.headlineSmall)
                    Text("Your BitcoinPoW activity, in one place.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (funds.transfers.isEmpty()) InfoCard("No transactions found yet", funds.message)
                    funds.transfers.forEach { transfer ->
                        val confirmations = if (transfer.height > 0) maxOf(0, funds.chainHeight - transfer.height + 1) else 0
                        InfoCard("${btc(transfer.amount)} BTCW", "${transfer.status}\n$confirmations confirmations\n${transfer.txid}")
                    }
                }
                Screen.Settings -> {
                    Text("Settings", style = MaterialTheme.typography.headlineSmall)
                    Text("Your wallet, security and connection.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    InfoCard("Network", "BitcoinPoW mainnet · Port ${BtcwNetwork.port}")
                    InfoCard("Trusted checkpoints", "Uses the node's ${BtcwCheckpoints.heights.size} mainnet checkpoints through height ${BtcwCheckpoints.latestHeight}.")
                    SyncCard(sync)
                    OutlinedButton(onClick = onRescan, enabled = !send.busy,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Rescan from block 141000") }
                    Text("Rebuild your balance and transaction history. Sending pauses until the scan finishes.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    InfoCard("Seed protection", "AES-GCM encryption with Android Keystore. Biometric authentication is required to use the key. The app locks when it goes into the background.")
                    InfoCard("Recovery format", "BIP39 English phrase · BIP84 · ${WalletKeys.FIRST_ADDRESS_PATH}. Back up the phrase and any restore passphrase offline.")
                    Button(onClick = onLock) { Text("Lock wallet") }
                    InfoCard("SPV verification", "Uses the node's historical checkpoints, block Merkle roots, and signature work/difficulty checks. This is a lightweight client; it does not execute every full-node UTXO consensus rule. Initial scanning downloads blocks without retaining unrelated transactions.")
                }
            }
        }
    }
}

private fun btc(satoshis: Long): String = BigDecimal.valueOf(satoshis, 8).stripTrailingZeros().toPlainString()

@Composable
private fun SyncCard(sync: SyncStatus) {
    InfoCard("Peer sync", buildString {
        append(sync.message)
        append("\n${sync.peerAgreement}")
        sync.peer?.let { append("\nPeer: $it") }
        append("\nHeaders downloaded: ${sync.downloaded}")
        append("\nCheckpoint-anchored height: ${sync.anchored}")
        sync.peerHeight?.let { append("\nPeer-reported height: $it") }
    })
}

@Composable
private fun InfoCard(title: String, message: String) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
