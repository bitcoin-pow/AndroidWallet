package com.wallet.btcw.network

import java.security.MessageDigest

internal fun hash256(bytes: ByteArray): ByteArray {
    val digest = MessageDigest.getInstance("SHA-256")
    return digest.digest(digest.digest(bytes))
}

/** Wire decoding only: a parsed header has NOT passed consensus validation. */
class BtcwHeader private constructor(private val encoded: ByteArray) {
    val hash: ByteArray get() = hash256(encoded)
    val previousHash: ByteArray get() = encoded.copyOfRange(4, 36)
    val merkleRoot: ByteArray get() = encoded.copyOfRange(36, 68)
    val isStake: Boolean get() = encoded.size > 80
    val serialized: ByteArray get() = encoded.copyOf()
    val time: Long get() = WireReader(encoded.copyOfRange(68, 72)).uint32()
    val bits: Long get() = WireReader(encoded.copyOfRange(72, 76)).uint32()
    val nonce: Long get() = WireReader(encoded.copyOfRange(76, 80)).uint32()
    val signingBytes: ByteArray get() = encoded.copyOfRange(0, if (isStake) 116 else 80)
    val signature: ByteArray get() = if (isStake) encoded.copyOfRange(117, encoded.size) else byteArrayOf()

    companion object {
        private val stakeNonces = setOf(0xfeedbeefL, 0xfeedbee1L, 0xfeedbee2L)

        /** BTCW headers extend Bitcoin's 80 bytes with an outpoint and signature. */
        internal fun read(reader: WireReader): BtcwHeader {
            val base = reader.bytes(80)
            val nonce = WireReader(base.copyOfRange(76, 80)).uint32()
            if (nonce !in stakeNonces) return BtcwHeader(base)
            val outpoint = reader.bytes(36)
            // All accepted signature forms fit in 84 bytes; do not allocate arbitrary peer lengths.
            val signatureLength = reader.compactSize(84)
            val signature = reader.bytes(signatureLength)
            return BtcwHeader(base + outpoint + byteArrayOf(signatureLength.toByte()) + signature)
        }

        fun parseHeaders(payload: ByteArray): List<BtcwHeader> {
            val reader = WireReader(payload)
            val count = reader.compactSize(2000)
            val headers = List(count) {
                read(reader).also {
                    require(reader.compactSize(0) == 0) { "Header transaction count must be zero" }
                }
            }
            require(reader.remaining == 0) { "Trailing headers data" }
            return headers
        }
    }
}
