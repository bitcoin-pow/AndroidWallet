package com.wallet.btcw.wallet

import org.bitcoinj.core.*
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.script.ScriptBuilder
import java.math.BigDecimal

/** Inputs must come from the completed wallet scan, not arbitrary peer claims. */
internal object TransactionSigner {
    private val network = MainNetParams.get()
    private const val MAX_MONEY = 2_100_000_000_000_000L
    private const val DUST = 546L
    data class Input(val previousTransactionHex: String, val outputIndex: Int, val addressIndex: Int = 0, val change: Boolean = false)
    data class Signed(val hex: String, val txid: String, val amount: Long, val fee: Long, val change: Long, val virtualSize: Int)
    data class Plan(val recipient: String, val amount: Long, val feeRate: Long, val fee: Long, val change: Long, val inputs: List<Input>, val changeAddress: String)

    fun parseAmount(text: String): Long {
        val normalized = text.trim()
        require(Regex("(?:0|[1-9][0-9]*)(?:\\.[0-9]{1,8})?").matches(normalized)) { "Use an amount with at most 8 decimal places" }
        return BigDecimal(normalized).movePointRight(8).longValueExact().also {
            require(it in DUST..MAX_MONEY) { "Amount is below dust or above the wallet limit" }
        }
    }

    private fun output(item: Input): TransactionOutput {
        require(item.outputIndex >= 0 && item.addressIndex >= 0)
        require(item.previousTransactionHex.length in 20..800_000 && item.previousTransactionHex.length % 2 == 0)
        val raw = item.previousTransactionHex.hexBytes()
        val previous = Transaction(network, raw)
        require(previous.messageSize == raw.size && item.outputIndex < previous.outputs.size)
        require(!previous.isCoinBase) { "Mining outputs are not supported" }
        require(previous.outputs.firstOrNull()?.let { it.value.value == 0L && it.scriptBytes.isEmpty() } != true) { "Coinstake outputs are not supported" }
        return previous.getOutput(item.outputIndex.toLong()).also { require(it.value.value in 1..MAX_MONEY) }
    }

    fun plan(available: List<Input>, recipient: String, amount: Long, feeRate: Long, changeAddress: String): Plan {
        require(amount in DUST..MAX_MONEY && feeRate in 1..1000) { "Invalid amount or fee rate (1–1000 sat/vB)" }
        require(available.size in 1..100) { "No spendable inputs, or more than 100 inputs" }
        val destination = Address.fromString(network, recipient)
        val outputScript = ScriptBuilder.createOutputScript(destination)
        val changeScript = ScriptBuilder.createOutputScript(Address.fromString(network, changeAddress))
        val seen = hashSetOf<String>()
        val candidates = available.map { input ->
            val previous = output(input)
            require(seen.add("${previous.parentTransactionHash}:${input.outputIndex}")) { "Duplicate input" }
            input to previous.value.value
        }.sortedByDescending { it.second }
        val selected = mutableListOf<Input>()
        var total = 0L; var fee = 0L; var change = 0L
        for ((input, value) in candidates) {
            selected.add(input); total = Math.addExact(total, value); require(total <= MAX_MONEY)
            fun estimate(includeChange: Boolean): Long {
                val base = 10 + selected.size * 41 + 9 + outputScript.program.size + if (includeChange) 9 + changeScript.program.size else 0
                return ((base * 4 + 2 + selected.size * 109 + 3) / 4).toLong() * feeRate
            }
            val changeFee = estimate(true)
            if (total >= amount + changeFee + DUST) { fee = changeFee; change = total - amount - fee; break }
            if (total >= amount + estimate(false)) { fee = total - amount; break }
        }
        require(fee > 0 && total >= amount + fee) { "Insufficient funds including fee" }
        require(fee <= 1_000_000L) { "Fee exceeds wallet safety limit" }
        return Plan(recipient, amount, feeRate, fee, change, selected.toList(), changeAddress)
    }

    fun sign(seed: ByteArray, available: List<Input>, recipient: String, amount: Long, feeRate: Long): Signed {
        val changeKey = WalletKeys.deriveKey(seed, 0, true)
        val changeAddress = SegwitAddress.fromHash(network, changeKey.pubKeyHash).toString()
        return sign(seed, plan(available, recipient, amount, feeRate, changeAddress))
    }

    fun sign(seed: ByteArray, plan: Plan): Signed {
        val changeKey = WalletKeys.deriveKey(seed, 0, true)
        require(plan.changeAddress == SegwitAddress.fromHash(network, changeKey.pubKeyHash).toString())
        // Recompute the plan to prevent a caller from hiding fees or changing reviewed outputs.
        require(plan == plan(plan.inputs, plan.recipient, plan.amount, plan.feeRate, plan.changeAddress))
        val selected = plan.inputs.map { input ->
            val previous = output(input)
            val key = WalletKeys.deriveKey(seed, input.addressIndex, input.change)
            val owned = ScriptBuilder.createOutputScript(SegwitAddress.fromHash(network, key.pubKeyHash))
            require(previous.scriptBytes.contentEquals(owned.program)) { "Input does not belong to this wallet" }
            previous to key
        }
        val tx = Transaction(network).apply { setVersion(2); addOutput(Coin.valueOf(plan.amount), Address.fromString(network, plan.recipient)) }
        if (plan.change > 0) tx.addOutput(Coin.valueOf(plan.change), Address.fromString(network, plan.changeAddress))
        selected.forEach { tx.addInput(it.first) }
        selected.forEachIndexed { index, (previous, key) ->
            val scriptCode = ScriptBuilder.createP2PKHOutputScript(key.pubKeyHash)
            val signature = tx.calculateWitnessSignature(index, key, scriptCode, previous.value, Transaction.SigHash.ALL, false)
            tx.getInput(index.toLong()).witness = TransactionWitness.redeemP2WPKH(signature, key)
        }
        tx.verify()
        require(plan.fee >= tx.vsize * plan.feeRate)
        return Signed(tx.bitcoinSerialize().toHex(), tx.txId.toString(), plan.amount, plan.fee, plan.change, tx.vsize)
    }
}
