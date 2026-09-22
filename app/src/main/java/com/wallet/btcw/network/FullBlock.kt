package com.wallet.btcw.network

import org.bitcoinj.core.Transaction
import org.bitcoinj.params.MainNetParams

internal data class FullBlock(val header: BtcwHeader, val transactions: List<Transaction>) {
    companion object {
        fun parse(payload: ByteArray, expected: BtcwHeader): FullBlock {
            require(payload.size <= PeerMessage.MAX_PAYLOAD)
            val reader = WireReader(payload)
            val header = BtcwHeader.read(reader)
            require(header.hash.contentEquals(expected.hash)) { "Wrong block returned" }
            val count = reader.compactSize(100_000)
            require(count > 0)
            var offset = reader.position
            val transactions = ArrayList<Transaction>(count)
            repeat(count) {
                require(offset < payload.size)
                val tx = Transaction(MainNetParams.get(), payload, offset)
                require(tx.messageSize in 10..(payload.size - offset))
                require(tx.inputs.isNotEmpty() && tx.outputs.isNotEmpty())
                var value = 0L
                tx.outputs.forEach {
                    require(it.value.value in 0..2_100_000_000_000_000L)
                    value = Math.addExact(value, it.value.value)
                    require(value <= 2_100_000_000_000_000L)
                }
                offset += tx.messageSize
                transactions.add(tx)
            }
            require(offset == payload.size) { "Trailing block data" }
            var level = transactions.map { it.txId.reversedBytes }
            while (level.size > 1) {
                level = level.indices.step(2).map { i ->
                    val right = if (i + 1 < level.size) level[i + 1] else level[i]
                    require(i + 1 == level.size || !level[i].contentEquals(right)) { "Mutated Merkle tree" }
                    hash256(level[i] + right)
                }
            }
            require(level.single().contentEquals(header.merkleRoot)) { "Invalid transaction Merkle root" }
            return FullBlock(header, transactions)
        }
    }
}
