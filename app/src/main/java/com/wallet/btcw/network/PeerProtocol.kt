package com.wallet.btcw.network

import java.nio.ByteBuffer
import java.nio.ByteOrder

internal object PeerProtocol {
    const val VERSION = 70016
    data class Version(val height: Int, val services: Long)

    fun version(nonce: Long, height: Int): ByteArray {
        val agent = "/BTCW-Android:0.1/".toByteArray(Charsets.US_ASCII)
        return ByteBuffer.allocate(86 + agent.size).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(VERSION); putLong(0) // SPV client: do not advertise block-serving services.
            putLong(System.currentTimeMillis() / 1000)
            repeat(2) { putLong(0); put(ByteArray(16)); putShort(0) }
            putLong(nonce); put(agent.size.toByte()); put(agent); putInt(height); put(0) // No tx relay yet.
        }.array()
    }

    fun readVersion(payload: ByteArray, ourNonce: Long): Version {
        val reader = WireReader(payload)
        val version = reader.uint32()
        require(version in 70001..Int.MAX_VALUE.toLong()) { "Peer protocol is too old" }
        val services = ByteBuffer.wrap(reader.bytes(8)).order(ByteOrder.LITTLE_ENDIAN).long
        reader.bytes(8 + 26 + 26)
        val nonce = ByteBuffer.wrap(reader.bytes(8)).order(ByteOrder.LITTLE_ENDIAN).long
        require(nonce != ourNonce) { "Self connection" }
        reader.bytes(reader.compactSize(256))
        val height = reader.uint32()
        require(height <= Int.MAX_VALUE) { "Invalid peer height" }
        if (reader.remaining != 0) {
            require(reader.remaining == 1 && reader.bytes(1)[0].toInt() in 0..1) { "Invalid version suffix" }
        }
        require(services and (1L or 1024L) != 0L) { "Peer does not serve a chain" }
        return Version(height.toInt(), services)
    }

    fun getHeaders(tip: ByteArray): ByteArray {
        require(tip.size == 32)
        return ByteBuffer.allocate(69).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(VERSION); put(1); put(tip); put(ByteArray(32))
        }.array()
    }
}
