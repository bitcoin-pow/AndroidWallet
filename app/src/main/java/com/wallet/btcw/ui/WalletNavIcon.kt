package com.wallet.btcw.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp

@Composable
internal fun WalletNavIcon(name: String) {
    val color = LocalContentColor.current
    Canvas(Modifier.size(24.dp)) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(color, Offset(x1, y1), Offset(x2, y2), 1.8f, StrokeCap.Round)
            when (name) {
                "Home" -> {
                    val path = Path().apply { moveTo(3f, 10f); lineTo(12f, 3f); lineTo(21f, 10f); moveTo(5f, 9f); lineTo(5f, 21f); lineTo(10f, 21f); lineTo(10f, 15f); lineTo(14f, 15f); lineTo(14f, 21f); lineTo(19f, 21f); lineTo(19f, 9f) }
                    drawPath(path, color, style = Stroke(1.8f, cap = StrokeCap.Round))
                }
                "Send" -> { line(5f, 19f, 19f, 5f); line(8f, 5f, 19f, 5f); line(19f, 5f, 19f, 16f) }
                "Receive" -> { line(19f, 5f, 5f, 19f); line(5f, 8f, 5f, 19f); line(5f, 19f, 16f, 19f) }
                "Transactions" -> {
                    for (y in listOf(6f, 12f, 18f)) { drawCircle(color, 1f, Offset(4f, y)); line(9f, y, 21f, y) }
                }
                else -> {
                    for ((y, x) in listOf(6f to 8f, 12f to 16f, 18f to 10f)) {
                        line(3f, y, x - 3f, y); line(x + 3f, y, 21f, y)
                        drawCircle(color, 2.5f, Offset(x, y), style = Stroke(1.8f))
                    }
                }
            }
        }
    }
}
