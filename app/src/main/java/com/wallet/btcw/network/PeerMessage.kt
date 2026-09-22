package com.wallet.btcw.network

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Bitcoin v1 envelope using BTCW mainnet magic. Caller must set socket deadlines. */
class PeerMessage(val command: String, payload: ByteArray) {
    private val body = payload.copyOf()
    val payload: ByteArray get() = body.copyOf()

    init {
        require(command.length in 1..12 && command.all { it.code in 0x20..0x7e })
        require(body.size <= MAX_PAYLOAD)
    }

    fun encode(): ByteArray {
        val header = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
        header.put(BtcwNetwork.messageMagic)
        header.put(command.toByteArray(Charsets.US_ASCII).copyOf(12))
        header.putInt(body.size)
        header.put(hash256(body), 0, 4)
        return header.array() + body
    }

    companion object {
        // Local resource limit, not a statement of BTCW block consensus limits.
        const val MAX_PAYLOAD = 4 * 1024 * 1024

        fun read(input: InputStream): PeerMessage {
            val header = readExactly(input, 24)
            require(header.copyOfRange(0, 4).contentEquals(BtcwNetwork.messageMagic)) { "Wrong network" }
            val commandBytes = header.copyOfRange(4, 16)
            val end = commandBytes.indexOf(0).let { if (it < 0) 12 else it }
            require(commandBytes.drop(end).all { it == 0.toByte() }) { "Invalid command padding" }
            val command = String(commandBytes, 0, end, Charsets.US_ASCII)
            require(end > 0 && commandBytes.take(end).all { it.toInt() in 0x20..0x7e }) { "Invalid command" }
            val length = ByteBuffer.wrap(header, 16, 4).order(ByteOrder.LITTLE_ENDIAN).int
            require(length in 0..MAX_PAYLOAD) { "Oversized peer message" }
            val body = readExactly(input, length)
            require(hash256(body).copyOf(4).contentEquals(header.copyOfRange(20, 24))) { "Invalid checksum" }
            return PeerMessage(command, body)
        }

        private fun readExactly(input: InputStream, count: Int): ByteArray {
            val result = ByteArray(count)
            var offset = 0
            while (offset < count) {
                val read = input.read(result, offset, count - offset)
                require(read > 0) { "Truncated peer message" }
                offset += read
            }
            return result
        }
    }
}
