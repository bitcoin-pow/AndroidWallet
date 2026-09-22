package com.wallet.btcw.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.wallet.btcw.R

/** Original wordmark stays on white to preserve its black lettering in either theme. */
@Composable
fun BrandHeader() {
    Surface(color = Color.White, shadowElevation = 1.dp) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Image(painterResource(R.drawable.btcw_wordmark), "BitcoinPoW", contentScale = ContentScale.Fit,
                modifier = Modifier.weight(1f).height(48.dp))
            Spacer(Modifier.width(20.dp))
            Text("WALLET", style = MaterialTheme.typography.labelSmall, color = Color(0xFF526071))
        }
    }
}

@Composable
fun CoinMark(modifier: Modifier = Modifier) {
    Image(painterResource(R.drawable.btcw_coin), contentDescription = null, modifier = modifier.size(44.dp))
}
