package com.wallet.btcw.network

/** Mainnet checkpointData, including historical_checkpoint, from the pinned node. */
object BtcwCheckpoints {
    const val sourceRevision = "409eee719df3bc7c397f840ed75c173e6d02e3b5"
    const val latestHeight = 141410

    // Display-order hashes. Keep private so callers cannot mutate the trust anchors.
    private val hashes = mapOf(
        0 to BtcwNetwork.genesisHash,
        10 to "000000000000a6616dfa3698c990302319934e577f3979f91f3d09a0c4de7bb8",
        11 to "d8cb951c9415ccd3602093f79457d428a6da00cd3f36ad4cde525d62cff800ce",
        1680 to "52f2b5a19992773d7b9d2710190073fdc5eecf70b5d9e8c0181a1437bdec9123",
        2240 to "b93b591092bf48ba55aae9a10ee23f25372a0c08e5e0b409ec0be7ef30b2de0c",
        2552 to "84dde0a5f9f45ce338c9d7375ba802480093b450bbc933b6f861e89096a91331",
        2860 to "61aa93b3169664541fb33c9705827ba423dcf2cacb25c1845199ecedaaace999",
        6060 to "ce046f1a67958b6d4acbc2a0ed6d194d7e4ed231232a6a146b7ff8d94a8b45c3",
        10515 to "dd8ff6535d448ecc560882d95ead78272c1a7a6da6b86150e762b33b5c6f8650",
        14180 to "ef939cd95e947bd72357b754dd75503347b80fbb68a6435b0da7ecdea1da3ae2",
        21111 to "7067cd7104e32708f4094cf626c4afcdee8af1a76a5969daa95122b2c0346e5a",
        23334 to "6ee07434a179056a258e3b0bb12e5f5e4c914f6ea77ed396fbe32962663aded6",
        30550 to "a4e1ee471f362b5e00ea20c575755f1dff00d39e2efb13dd6bf861d371436ce1",
        34001 to "68be69b19c98c0ef9e4a5cbafcc897e09ea0826057eb7535fa497977273db889",
        36650 to "932565bccbffafce8818b56cdf277eac6247e2cfdee10579eb2d6be9f44d834e",
        36780 to "efe930a3c76caf787e6cfc6719954dc0ff51a53d222eae693da73e5145c1388e",
        38520 to "109aec0e58a01a3389f8a09593d7fd85d0c1c96215d64b490c7a539ae036aa18",
        40540 to "6e1c0c9c9de8e01e662a28fc482dd35fbb04da616e0a23328ab9c41158cdc317",
        70384 to "b67a0d7fa4037f3611b113772fb66cae23114e17954416c139b48b8e6ec7747b",
        115136 to "4dfa9376844cb6d5a9b9021b37dfc88c552108ba0e9358c7d8a95e130b02f4fe",
        115137 to "6c1a7097b6629c996b22b080a72033a41f55ab05e9e5466183f1e8f1b74d4965",
        latestHeight to "05d553c0600bdeff22592f1331c8bc9fd534c35cd75a892c32055dea914cd00e",
    )

    val heights: List<Int> get() = hashes.keys.sorted()
    fun displayHash(height: Int): String? = hashes[height]

    /** Throws on mismatch; returns false at non-checkpoint heights (not verified). */
    fun check(height: Int, wireHash: ByteArray): Boolean {
        require(height >= 0 && wireHash.size == 32) { "Invalid checkpoint input" }
        val expected = hashes[height] ?: return false
        require(wireHash.contentEquals(toWireHash(expected))) { "BTCW checkpoint mismatch at height $height" }
        return true
    }

    internal fun toWireHash(displayHash: String): ByteArray =
        displayHash.chunked(2).map { it.toInt(16).toByte() }.reversed().toByteArray()
}
