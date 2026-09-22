package com.wallet.btcw.network

import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.bitcoinj.core.ECKey
import org.bitcoinj.core.Sha256Hash

/** Lightweight work verification; like SPV, does not execute the complete UTXO consensus. */
internal object SpvWork {
    private val limit = BigInteger("00000000ffffffffffffffffffffffffffffffffffffffffffffffffffffffff", 16)
    private val modulus = BigInteger.ONE.shiftLeft(256)
    private fun number(bytes: ByteArray) = BigInteger(1, bytes.reversedArray())
    fun target(bits: Long): BigInteger {
        val size = (bits ushr 24).toInt()
        val word = bits and 0x7fffff
        require(bits and 0x800000 == 0L && word > 0 && size <= 34)
        val value = BigInteger.valueOf(word).shiftLeft(8 * (size - 3))
        require(value.signum() > 0 && value <= limit)
        return value
    }
    fun compact(value: BigInteger): Long {
        var size = (value.bitLength() + 7) / 8
        var word = value.shiftRight(8 * (size - 3)).toLong()
        if (word and 0x800000 != 0L) { word = word ushr 8; size++ }
        return (size.toLong() shl 24) or (word and 0x7fffff)
    }

    fun requiredBits(height: Int, chain: HeaderHistory): Long {
        val lastHeight = height - 1
        if (height >= 144444) {
            val transition = lastHeight < 144587
            val anchorHeight = if (transition) 144443 else 144587
            val anchor = chain.headerAt(anchorHeight)
            var reference = target(anchor.bits)
            if (transition) reference = (reference * BigInteger.valueOf(10000)).min(limit)
            val elapsed = chain.headerAt(lastHeight).time - anchor.time + 600
            val error = elapsed - 600L * (lastHeight - anchorHeight + 1)
            val exponent = BigInteger.valueOf(error).multiply(BigInteger.valueOf(65536)).divide(BigInteger.valueOf(43200)).toLong()
            if (exponent <= -256L * 65536) return compact(BigInteger.ONE)
            if (exponent >= 256L * 65536) return compact(limit)
            val shifts = Math.floorDiv(exponent, 65536).toInt()
            val fraction = BigInteger.valueOf(Math.floorMod(exponent, 65536).toLong())
            val factor = BigInteger.valueOf(65536) + (BigInteger.valueOf(195766423245049) * fraction +
                BigInteger.valueOf(971821376) * fraction.pow(2) + BigInteger.valueOf(5127) * fraction.pow(3) +
                BigInteger.ONE.shiftLeft(47)).shiftRight(48)
            return compact((reference * factor).shiftLeft(shifts - 16).max(BigInteger.ONE).min(limit))
        }
        require(lastHeight >= 45)
        var previousTime = chain.headerAt(lastHeight - 45).time
        var weighted = 0L
        var recent = 0L
        var sum = BigInteger.ZERO
        for (i in 1..45) {
            val header = chain.headerAt(lastHeight - 45 + i)
            val time = maxOf(header.time, previousTime + 1)
            val delta = minOf(3600, time - previousTime)
            previousTime = time
            weighted += delta * i
            sum += target(header.bits) / BigInteger.valueOf(621000L * 45)
            if (i > 42) recent += delta
        }
        val previous = target(chain.headerAt(lastHeight).bits)
        var next = sum * BigInteger.valueOf(weighted)
        next = next.min(previous * BigInteger.valueOf(150) / BigInteger.valueOf(100))
        next = next.max(previous * BigInteger.valueOf(67) / BigInteger.valueOf(100))
        if (recent < 480) next = previous * BigInteger.valueOf(100) / BigInteger.valueOf(106)
        return compact(next.min(limit))
    }

    fun verify(height: Int, chain: HeaderHistory, block: FullBlock, nowSeconds: Long = System.currentTimeMillis() / 1000) {
        if (height <= chain.checkpointAnchoredHeight) return
        require(chain.checkpointAnchoredHeight >= BtcwCheckpoints.latestHeight) { "Historical checkpoint not reached" }
        val header = block.header
        require(header.isStake && header.nonce == 0xfeedbee2L)
        require(header.bits == requiredBits(height, chain)) { "Incorrect BTCW difficulty" }
        val median = (height - 11 until height).map { chain.headerAt(it).time }.sorted()[5]
        require(header.time > median && header.time <= nowSeconds + 7200) { "Invalid block time" }
        require(block.transactions.size >= 2)
        val stake = block.transactions[1]
        require(stake.inputs.isNotEmpty() && stake.outputs.size >= 2)
        require(stake.outputs[0].value.value == 0L && stake.outputs[0].scriptBytes.isEmpty())
        val outpoint = header.signingBytes.copyOfRange(80, 116)
        require(outpoint.copyOfRange(0, 32).contentEquals(stake.inputs[0].outpoint.hash.reversedBytes))
        require(WireReader(outpoint.copyOfRange(32, 36)).uint32() == stake.inputs[0].outpoint.index)
        val script = stake.outputs[1].scriptBytes
        require((script.size == 35 && script[0] == 33.toByte()) || (script.size == 67 && script[0] == 65.toByte()))
        require(script.last() == 0xac.toByte())
        val key = ECKey.fromPublicOnly(script.copyOfRange(1, script.size - 1))
        val signature = header.signature
        val workLimit = target(header.bits) * BigInteger.valueOf(10_000_000)
        val unsignedHash = hash256(header.signingBytes)
        if (height >= 144444) {
            require(signature.size == 70 || signature.size == 71)
            val decoded = ECKey.ECDSASignature.decodeFromDER(signature)
            require(decoded.isCanonical && decoded.encodeToDER().contentEquals(signature))
            require(key.verify(Sha256Hash.wrap(unsignedHash), decoded) && number(hash256(signature)) <= workLimit)
            require(stake.outputs.drop(1).filter { it.value.value > 0 }.all { it.scriptBytes.contentEquals(script) })
        } else {
            require(signature.size in 60..84)
            val der = signature.copyOfRange(0, signature.size - 8)
            val nonceBytes = signature.copyOfRange(signature.size - 8, signature.size)
            val nonce = BigInteger(1, nonceBytes)
            val message = (number(unsignedHash) + nonce).mod(modulus).toByteArray().takeLast(32).toByteArray()
            val padded = ByteArray(32); message.copyInto(padded, 32 - message.size)
            val signedHash = padded.reversedArray()
            val work = nonceBytes.reversedArray() + byteArrayOf(der.size.toByte()) + der
            require(key.verify(Sha256Hash.wrap(signedHash), legacySignature(der).toCanonicalised())) { "Invalid block signature" }
            require(number(hash256(work)) <= workLimit) { "Insufficient block signature work" }
        }
    }

    /** Matches src/pubkey.cpp's historical lax parser, not the post-fork DER rules. */
    internal fun legacySignature(bytes: ByteArray): ECKey.ECDSASignature {
        var position = 0
        fun read(): Int { require(position < bytes.size) { "Truncated signature" }; return bytes[position++].toInt() and 255 }
        require(read() == 0x30)
        val sequenceLength = read()
        if (sequenceLength and 128 != 0) {
            val count = sequenceLength - 128
            require(count <= bytes.size - position)
            position += count
        } // Historical node deliberately ignores the declared sequence length.
        fun scalar(): BigInteger {
            require(read() == 2)
            var size = read()
            if (size and 128 != 0) {
                var count = size - 128
                require(count <= bytes.size - position)
                while (count > 0 && bytes[position] == 0.toByte()) { position++; count-- }
                require(count < 4)
                size = 0
                repeat(count) { size = (size shl 8) or read() }
            }
            require(size <= bytes.size - position)
            val value = if (size == 0) BigInteger.ZERO else BigInteger(1, bytes.copyOfRange(position, position + size))
            position += size
            require(value.signum() > 0 && value < ECKey.CURVE.n) { "Invalid signature scalar" }
            return value
        }
        val r = scalar(); val s = scalar()
        return ECKey.ECDSASignature(r, s)
    }
}
