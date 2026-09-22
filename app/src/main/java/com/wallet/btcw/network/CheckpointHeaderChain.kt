package com.wallet.btcw.network

/**
 * Single-branch download staging, starting immediately after mainnet genesis.
 * A matched checkpoint anchors its linked ancestors, never its descendants.
 * This is NOT a consensus validator, persistent store, or best-chain selector.
 * Use from a single synchronization worker. A failed batch leaves state intact.
 */
interface HeaderHistory {
    val checkpointAnchoredHeight: Int
    fun headerAt(height: Int): BtcwHeader
}

class CheckpointHeaderChain : HeaderHistory {
    private val headers = ArrayList<BtcwHeader>()
    override fun headerAt(height: Int): BtcwHeader = headers[height - 1]
    fun hashAt(height: Int): ByteArray = if (height == 0) BtcwCheckpoints.toWireHash(BtcwNetwork.genesisHash) else headerAt(height).hash
    var downloadedHeight: Int = 0
        private set
    override var checkpointAnchoredHeight: Int = 0
        private set
    private var tip = BtcwCheckpoints.toWireHash(BtcwNetwork.genesisHash)
    val tipHash: ByteArray get() = tip.copyOf()

    fun appendHeaders(payload: ByteArray) {
        val headers = BtcwHeader.parseHeaders(payload)
        var height = downloadedHeight
        var anchor = checkpointAnchoredHeight
        var previous = tip
        for (header in headers) {
            require(height < Int.MAX_VALUE) { "Header height overflow" }
            require(header.previousHash.contentEquals(previous)) { "Disconnected BTCW header at height ${height + 1}" }
            height++
            val hash = header.hash
            if (BtcwCheckpoints.check(height, hash)) anchor = height
            previous = hash
        }
        downloadedHeight = height
        checkpointAnchoredHeight = anchor
        tip = previous
        this.headers.addAll(headers)
    }
}
