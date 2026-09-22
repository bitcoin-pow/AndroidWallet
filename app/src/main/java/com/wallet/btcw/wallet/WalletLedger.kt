package com.wallet.btcw.wallet

import android.util.AtomicFile
import org.bitcoinj.core.Address
import org.bitcoinj.core.Transaction
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.script.ScriptBuilder
import org.json.JSONObject
import org.json.JSONArray
import java.io.File

data class WatchAddress(val address: String, val index: Int, val change: Boolean)
data class WalletTransfer(val txid: String, val amount: Long, val height: Int, val status: String)
data class FundsState(
    val scannedHeight: Int = 0, val chainHeight: Int = 0, val available: Long = 0,
    val confirming: Long = 0, val transfers: List<WalletTransfer> = emptyList(),
    val message: String = "Unlock to scan transactions", val ready: Boolean = false,
)

/** Worker-confined, replayable public wallet data. Private keys never enter this store. */
internal class WalletLedger(private val file: File, val watch: List<WatchAddress>) {
    companion object {
        const val SCAN_START_HEIGHT = 141000
        const val SPEND_CONFIRMATIONS = 1
    }
    private val outputs = linkedMapOf<String, Owned>()
    private val history = linkedMapOf<String, WalletTransfer>()
    private val pending = linkedMapOf<String, String>()
    private val outgoing = linkedMapOf<String, String>()
    private val reserved = hashSetOf<String>()
    // The next block to apply is inclusive of the configured scan boundary.
    var height = SCAN_START_HEIGHT - 1; private set
    var blockHash = ""; private set
    private data class Owned(val tx: String, val output: Int, val value: Long, val height: Int, val index: Int, val change: Boolean)
    private val scripts = watch.associateBy {
        ScriptBuilder.createOutputScript(Address.fromString(MainNetParams.get(), it.address)).program.toHex()
    }

    init {
        require(watch.isNotEmpty())
        if (file.exists()) {
            require(file.length() <= 32L * 1024 * 1024) { "Wallet journal too large" }
            val json = JSONObject(AtomicFile(file).openRead().bufferedReader().use { it.readText() })
            require(json.getInt("version") == 1 && json.getString("wallet") == watch[0].address)
            height = json.getInt("height"); blockHash = json.getString("hash")
            val rows = json.getJSONArray("outputs")
            for (i in 0 until rows.length()) {
                val row = rows.getJSONObject(i)
                outputs[row.getString("id")] = Owned(row.getString("tx"), row.getInt("vout"), row.getLong("value"), row.getInt("height"), row.getInt("index"), row.getBoolean("change"))
            }
            val events = json.getJSONArray("history")
            for (i in 0 until events.length()) {
                val row = events.getJSONObject(i)
                val event = WalletTransfer(row.getString("id"), row.getLong("amount"), row.getInt("height"), row.getString("status"))
                history[event.txid] = event
            }
            val sends = json.getJSONObject("pending")
            sends.keys().forEach { id -> pending[id] = sends.getString(id) }
            val allSends = json.optJSONObject("outgoing") ?: sends
            allSends.keys().forEach { id -> outgoing[id] = allSends.getString(id) }
            pending.values.forEach { raw -> transaction(raw).inputs.forEach { reserved.add("${it.outpoint.hash}:${it.outpoint.index}") } }
            // Rebuild old scan state under the new boundary, preserving signed sends.
            if (json.optInt("scanStart", 1) != SCAN_START_HEIGHT || height < SCAN_START_HEIGHT - 1) resetForReorg()
        }
    }

    fun apply(blockHeight: Int, hash: String, transactions: List<Transaction>) {
        require(blockHeight == height + 1)
        for (tx in transactions) {
            val id = tx.txId.toString()
            var incoming = 0L; var outgoing = 0L
            for (input in tx.inputs) {
                val outpoint = "${input.outpoint.hash}:${input.outpoint.index}"
                val conflicts = pending.filter { (pendingId, raw) -> pendingId != id && transaction(raw).inputs.any { "${it.outpoint.hash}:${it.outpoint.index}" == outpoint } }.keys
                for (conflict in conflicts) {
                    pending.remove(conflict)
                    history[conflict]?.let { history[conflict] = it.copy(status = "Conflicted by a confirmed transaction") }
                }
                outputs.remove(outpoint)?.let { outgoing = Math.addExact(outgoing, it.value) }
            }
            val mining = tx.isCoinBase || tx.outputs.firstOrNull()?.let { it.value.value == 0L && it.scriptBytes.isEmpty() } == true
            tx.outputs.forEachIndexed { index, output ->
                scripts[output.scriptBytes.toHex()]?.let { owner ->
                    incoming = Math.addExact(incoming, output.value.value)
                    // Mining rewards require additional BTCW maturity policy. Do not offer them to the signer.
                    if (!mining) outputs["$id:$index"] = Owned(tx.bitcoinSerialize().toHex(), index, output.value.value, blockHeight, owner.index, owner.change)
                }
            }
            if (incoming != 0L || outgoing != 0L || id in pending) {
                history[id] = WalletTransfer(id, incoming - outgoing, blockHeight, if (mining) "Mining output (not spendable here)" else "In block")
            }
            pending.remove(id)
        }
        height = blockHeight; blockHash = hash
        reserved.clear()
        pending.values.forEach { raw -> transaction(raw).inputs.forEach { reserved.add("${it.outpoint.hash}:${it.outpoint.index}") } }
    }

    fun inputs(tip: Int): List<TransactionSigner.Input> = outputs.filter { (id, coin) ->
        id !in reserved && tip - coin.height + 1 >= SPEND_CONFIRMATIONS
    }.values.map { TransactionSigner.Input(it.tx, it.output, it.index, it.change) }

    fun state(tip: Int, caughtUp: Boolean, message: String): FundsState {
        var available = 0L; var confirming = 0L
        outputs.forEach { (id, coin) ->
            if (id !in reserved) {
                if (tip - coin.height + 1 >= SPEND_CONFIRMATIONS) available = Math.addExact(available, coin.value)
                else confirming = Math.addExact(confirming, coin.value)
            }
        }
        return FundsState(height, tip, if (caughtUp) available else 0, confirming, history.values.toList().takeLast(100).reversed(), message, caughtUp)
    }

    fun queue(signed: TransactionSigner.Signed) {
        val tx = transaction(signed.hex)
        require(tx.txId.toString() == signed.txid)
        tx.inputs.forEach {
            val id = "${it.outpoint.hash}:${it.outpoint.index}"
            require(id in outputs && id !in reserved) { "Inputs changed; review again" }
        }
        val oldReserved = reserved.toSet()
        val oldHistory = history[signed.txid]
        val oldPending = pending[signed.txid]
        val oldOutgoing = outgoing[signed.txid]
        tx.inputs.forEach { reserved.add("${it.outpoint.hash}:${it.outpoint.index}") }
        pending[signed.txid] = signed.hex
        outgoing[signed.txid] = signed.hex
        history[signed.txid] = WalletTransfer(signed.txid, -signed.amount - signed.fee, 0, "Queued; not confirmed")
        try { save() } catch (error: Exception) {
            reserved.clear(); reserved.addAll(oldReserved)
            if (oldPending == null) pending.remove(signed.txid) else pending[signed.txid] = oldPending
            if (oldOutgoing == null) outgoing.remove(signed.txid) else outgoing[signed.txid] = oldOutgoing
            if (oldHistory == null) history.remove(signed.txid) else history[signed.txid] = oldHistory
            throw error
        } // Reserve durably BEFORE any network submission.
    }

    fun queued(): Map<String, String> = pending.toMap()
    fun submitted(id: String) {
        history[id]?.let { if (it.height == 0) history[id] = it.copy(status = "Submitted to peer; unconfirmed") }
        save()
    }
    fun resetForReorg(reason: String = "Rechecking after chain change") {
        outputs.clear()
        history.entries.removeAll { it.value.height > 0 && it.key !in outgoing }
        outgoing.keys.forEach { id -> history[id]?.let { history[id] = it.copy(height = 0, status = reason) } }
        pending.clear(); pending.putAll(outgoing)
        reserved.clear()
        pending.values.forEach { raw -> transaction(raw).inputs.forEach { reserved.add("${it.outpoint.hash}:${it.outpoint.index}") } }
        height = SCAN_START_HEIGHT - 1; blockHash = ""
        save() // Pending spends stay reserved until the new chain is scanned.
    }

    fun save() {
        require(outputs.size <= 10000 && history.size <= 10000 && outgoing.size <= 10000) { "Wallet history limit reached" }
        val json = JSONObject().put("version", 1).put("wallet", watch[0].address).put("height", height).put("hash", blockHash)
            .put("scanStart", SCAN_START_HEIGHT)
        json.put("outputs", JSONArray().apply { outputs.forEach { (id, coin) -> put(JSONObject().put("id", id).put("tx", coin.tx).put("vout", coin.output).put("value", coin.value).put("height", coin.height).put("index", coin.index).put("change", coin.change)) } })
        json.put("history", JSONArray().apply { history.values.forEach { put(JSONObject().put("id", it.txid).put("amount", it.amount).put("height", it.height).put("status", it.status)) } })
        json.put("pending", JSONObject(pending.toMap()))
        json.put("outgoing", JSONObject(outgoing.toMap()))
        val bytes = json.toString().toByteArray()
        require(bytes.size <= 32 * 1024 * 1024)
        val atomic = AtomicFile(file); val stream = atomic.startWrite()
        try { stream.write(bytes); atomic.finishWrite(stream) } catch (e: Exception) { atomic.failWrite(stream); throw e }
    }

    private fun transaction(hex: String) = Transaction(MainNetParams.get(), hex.hexBytes())
}

internal fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it.toInt() and 255) }
internal fun String.hexBytes(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
