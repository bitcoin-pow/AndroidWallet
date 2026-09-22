package com.wallet.btcw

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsDisplayed
import com.wallet.btcw.ui.WalletApp
import com.wallet.btcw.ui.theme.BTCWTheme
import org.junit.Rule
import org.junit.Test

class ReceiveScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun receiveScreenShowsQrForUnlockedAddress() {
        compose.setContent {
            BTCWTheme { WalletApp(receivingAddress = "bc1qcr8te4kr609gcawutmrza0j4xv80jy8z306fyu") }
        }
        compose.onAllNodesWithText("Receive")[0].performClick()
        compose.onNodeWithContentDescription("BTCW receiving address QR code").assertIsDisplayed()
    }
}
