package com.wallet.btcw.wallet

import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Assert.*
import org.junit.Test

class ReceiveQrTest {
    @Test fun qrDecodesToExactReceivingAddress() {
        val address = "bc1qcr8te4kr609gcawutmrza0j4xv80jy8z306fyu"
        val matrix = ReceiveQr.encode(address)
        val pixels = IntArray(matrix.width * matrix.height) { i -> if (matrix[i % matrix.width, i / matrix.width]) 0xff000000.toInt() else 0xffffffff.toInt() }
        val decoded = QRCodeReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(matrix.width, matrix.height, pixels))))
        assertEquals(address, decoded.text)
        assertFalse(matrix[0, 0])
    }

    @Test fun rejectsInvalidOrWrongNetworkAddress() {
        assertThrows(Exception::class.java) { ReceiveQr.encode("not an address") }
        assertThrows(Exception::class.java) { ReceiveQr.encode("tb1qcr8te4kr609gcawutmrza0j4xv80jy8z306fyu") }
    }
}
