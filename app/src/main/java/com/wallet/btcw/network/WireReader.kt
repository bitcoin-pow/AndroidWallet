package com.wallet.btcw.network

/** Bounded, canonical decoding of untrusted peer payloads. */
internal class WireReader(private val payload: ByteArray) {
    var position = 0
        private set
    val remaining: Int get() = payload.size - position

    fun bytes(count: Int): ByteArray {
        require(count >= 0 && count <= remaining) { "Truncated peer message" }
        return payload.copyOfRange(position, position + count).also { position += count }
    }

    fun uint32(): Long = littleEndian(4)

    private fun littleEndian(count: Int): Long = bytes(count).foldIndexed(0L) { index, result, byte ->
        result or ((byte.toLong() and 255) shl (index * 8))
    }

    fun compactSize(limit: Int): Int {
        val tag = littleEndian(1).toInt()
        val value = when (tag) {
            253 -> littleEndian(2).also { require(it >= 253) { "Noncanonical CompactSize" } }
            254 -> littleEndian(4).also { require(it > 65535) { "Noncanonical CompactSize" } }
            255 -> littleEndian(8).also { require(it > 0xffffffffL) { "Invalid CompactSize" } }
            else -> tag.toLong()
        }
        require(value in 0..limit.toLong()) { "Peer count exceeds limit" }
        return value.toInt()
    }
}
