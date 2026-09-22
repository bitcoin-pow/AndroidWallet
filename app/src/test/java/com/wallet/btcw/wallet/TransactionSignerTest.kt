package com.wallet.btcw.wallet

import org.bitcoinj.core.*
import org.bitcoinj.crypto.TransactionSignature
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.script.ScriptBuilder
import org.bitcoinj.script.Script
import org.junit.Assert.*
import org.junit.Test

class TransactionSignerTest {
    private val seed = ByteArray(64) { (it + 1).toByte() }
    private val network = MainNetParams.get()
    private val recipient = "bc1qcr8te4kr609gcawutmrza0j4xv80jy8z306fyu"

    private fun funding(value: Long, tag: Byte = 1): Transaction {
        val tx = Transaction(network)
        tx.addInput(Sha256Hash.wrap(ByteArray(32) { tag }), 0, Script(byteArrayOf()))
        tx.addOutput(Coin.valueOf(value), Address.fromString(network, WalletKeys.receivingAddress(seed)))
        return tx
    }
    private fun input(tx: Transaction) = TransactionSigner.Input(tx.bitcoinSerialize().joinToString("") { "%02x".format(it.toInt() and 255) }, 0)
    private fun decode(hex: String) = Transaction(network, hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray())

    @Test fun signsEveryInputAndPreservesValue() {
        val a = funding(50_000)
        val b = funding(50_000, 2)
        val signed = TransactionSigner.sign(seed, listOf(input(a), input(b)), recipient, 80_000, 2)
        val tx = decode(signed.hex)
        assertEquals(2, tx.inputs.size)
        assertEquals(100_000L, tx.outputs.sumOf { it.value.value } + signed.fee)
        assertEquals(80_000L, tx.outputs[0].value.value)
        val key = WalletKeys.deriveKey(seed)
        val code = ScriptBuilder.createP2PKHOutputScript(key.pubKeyHash)
        tx.inputs.forEachIndexed { index, txInput ->
            val signature = TransactionSignature.decodeFromBitcoin(txInput.witness.getPush(0), true, true)
            val hash = tx.hashForWitnessSignature(index, code, Coin.valueOf(50_000), Transaction.SigHash.ALL, false)
            assertTrue(key.verify(hash, signature))
            val wrongAmount = tx.hashForWitnessSignature(index, code, Coin.valueOf(50_001), Transaction.SigHash.ALL, false)
            assertFalse(key.verify(wrongAmount, signature))
        }
        assertEquals(signed.txid, tx.txId.toString())
        assertTrue(signed.fee >= tx.vsize * 2)
        assertTrue(signed.change >= 546)
    }

    @Test fun rejectsDuplicateUnownedAndInsufficientInputs() {
        val source = input(funding(50_000))
        assertThrows(IllegalArgumentException::class.java) { TransactionSigner.sign(seed, listOf(source, source), recipient, 10_000, 1) }
        assertThrows(IllegalArgumentException::class.java) { TransactionSigner.sign(ByteArray(64) { 99 }, listOf(source), recipient, 10_000, 1) }
        assertThrows(IllegalArgumentException::class.java) { TransactionSigner.sign(seed, listOf(source), recipient, 50_000, 1) }
    }

    @Test fun parsesExactAmountsAndRejectsDustOrExcessPrecision() {
        assertEquals(123456789L, TransactionSigner.parseAmount("1.23456789"))
        for (bad in listOf("-1", "1e8", "0.00000001", "0.123456789", "21000001", "NaN")) {
            assertThrows(Exception::class.java) { TransactionSigner.parseAmount(bad) }
        }
    }

    @Test fun avoidsDustChangeAndRejectsWrongNetwork() {
        val result = TransactionSigner.sign(seed, listOf(input(funding(10_500))), recipient, 10_000, 1)
        assertEquals(0L, result.change)
        assertEquals(500L, result.fee)
        assertEquals(1, decode(result.hex).outputs.size)
        assertThrows(Exception::class.java) {
            TransactionSigner.sign(seed, listOf(input(funding(50_000))), "tb1qcr8te4kr609gcawutmrza0j4xv80jy8z306fyu", 10_000, 1)
        }
    }
}
