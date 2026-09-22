package com.wallet.btcw.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.wallet.btcw.wallet.ReceiveQr

@Composable
fun ReceiveQrImage(address: String) {
    val bitmap = remember(address) {
        val matrix = ReceiveQr.encode(address)
        val pixels = IntArray(matrix.width * matrix.height) { index ->
            if (matrix[index % matrix.width, index / matrix.width]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
        }
        Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.ARGB_8888).asImageBitmap()
    }
    Image(bitmap, "BTCW receiving address QR code", modifier = Modifier
        .sizeIn(maxWidth = 280.dp, maxHeight = 280.dp).aspectRatio(1f).background(Color.White),
        filterQuality = FilterQuality.None)
}
