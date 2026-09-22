package com.wallet.btcw

import android.view.WindowManager
import androidx.biometric.BiometricManager
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsDisplayed
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wallet.btcw.security.SeedVault
import com.wallet.btcw.wallet.WalletKeys
import org.junit.Assert.*
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WalletDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun bip84DerivationWorksOnAndroid() {
        val words = WalletKeys.parsePhrase("abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about")
        val seed = WalletKeys.seed(words)
        try { assertEquals("bc1qcr8te4kr609gcawutmrza0j4xv80jy8z306fyu", WalletKeys.receivingAddress(seed)) }
        finally { seed.fill(0) }
    }

    @Test fun onboardingIsSecureAndRefusesMissingBiometrics() {
        assumeFalse(SeedVault(compose.activity).exists)
        assumeTrue(BiometricManager.from(compose.activity).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) != BiometricManager.BIOMETRIC_SUCCESS)
        compose.onNodeWithText("Create wallet").assertIsDisplayed().performClick()
        compose.onNodeWithText("Enroll a strong biometric (such as a fingerprint) in Android Settings before creating or unlocking a wallet.").assertIsDisplayed()
        assertFalse(SeedVault(compose.activity).exists)
        assertTrue(compose.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
    }
}
