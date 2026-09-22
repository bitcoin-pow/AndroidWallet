package com.wallet.btcw

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import android.view.WindowManager
import android.view.View
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import com.wallet.btcw.wallet.FundsState
import com.wallet.btcw.network.PeerSync
import com.wallet.btcw.network.SyncStatus
import androidx.compose.ui.tooling.preview.Preview
import com.wallet.btcw.ui.theme.BTCWTheme
import com.wallet.btcw.ui.WalletApp
import com.wallet.btcw.ui.WalletSetup
import com.wallet.btcw.wallet.WalletController
import com.wallet.btcw.wallet.WalletStage

class MainActivity : FragmentActivity() {
    private val syncStatus = mutableStateOf(SyncStatus())
    private lateinit var peerSync: PeerSync
    private lateinit var wallet: WalletController
    private val fundsState = mutableStateOf(FundsState())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        window.decorView.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        enableEdgeToEdge(statusBarStyle = androidx.activity.SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT))
        wallet = WalletController(this)
        peerSync = PeerSync(noBackupFilesDir, onFunds = { fundsState.value = it }) { syncStatus.value = it }
        wallet.attachPeer(peerSync)
        setContent {
            BTCWTheme {
                LaunchedEffect(wallet.state.watch) { if (wallet.state.watch.isNotEmpty()) peerSync.watchWallet(wallet.state.watch) }
                if (wallet.state.stage == WalletStage.Ready) {
                    WalletApp(sync = syncStatus.value, receivingAddress = wallet.state.address,
                        funds = fundsState.value, send = wallet.sendState,
                        onReview = wallet::reviewSend, onConfirm = wallet::confirmSend,
                        onCancelSend = wallet::cancelSend, onLock = { wallet.lock() },
                        onRescan = { wallet.cancelSend(); peerSync.rescanWallet() })
                } else {
                    WalletSetup(wallet)
                }
            }
        }
    }

    override fun onStart() { super.onStart(); peerSync.start() }
    override fun onStop() { wallet.lock(); peerSync.stop(); super.onStop() }
    override fun onDestroy() { wallet.close(); peerSync.close(); super.onDestroy() }
}

@Preview(showBackground = true)
@Composable
fun WalletPreview() {
    BTCWTheme {
        WalletApp()
    }
}
