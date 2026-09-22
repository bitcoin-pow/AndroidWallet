package com.wallet.btcw

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.*
import com.wallet.btcw.ui.WalletApp
import com.wallet.btcw.ui.theme.BTCWTheme
import com.wallet.btcw.wallet.*
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*

class SendScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun settingsRescanButtonRequestsRescan() {
        var requested = false
        compose.setContent { BTCWTheme { WalletApp(onRescan = { requested = true }) } }
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Rescan from block 141000").performScrollTo().performClick()
        assertTrue(requested)
    }

    @Test fun cannotReviewBeforeScanCompletes() {
        compose.setContent { BTCWTheme { WalletApp() } }
        compose.onAllNodesWithText("Send")[0].performClick()
        compose.onNodeWithText("Review transaction").performScrollTo().assertIsNotEnabled()
    }

    @Test fun reviewShowsFeeAndRequiresExplicitSendAction() {
        var confirmed = false
        compose.setContent {
            BTCWTheme {
                WalletApp(funds = FundsState(available = 100_000, ready = true),
                    send = SendState(review = SendReview("bc1qcr8te4kr609gcawutmrza0j4xv80jy8z306fyu", 70_000, 300, 29_700)),
                    onConfirm = { confirmed = true })
            }
        }
        compose.onAllNodesWithText("Send")[0].performClick()
        assertFalse(confirmed)
        compose.onNodeWithText("Fee: 0.000003 BTCW", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Authenticate and send").performScrollTo().performClick()
        assertTrue(confirmed)
    }
}
