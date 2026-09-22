package com.wallet.btcw.wallet

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import org.bitcoinj.core.SegwitAddress
import org.bitcoinj.params.MainNetParams

object ReceiveQr {
    /** Raw address avoids falsely advertising a Bitcoin payment URI for BTCW. */
    fun encode(address: String): BitMatrix {
        val parsed = SegwitAddress.fromBech32(MainNetParams.get(), address)
        require(parsed.witnessVersion == 0 && parsed.witnessProgram.size == 20)
        return QRCodeWriter().encode(address, BarcodeFormat.QR_CODE, 512, 512, mapOf(
            EncodeHintType.MARGIN to 4,
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
        ))
    }
}
