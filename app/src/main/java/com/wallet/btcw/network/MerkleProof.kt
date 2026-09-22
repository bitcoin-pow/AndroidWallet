package com.wallet.btcw.network

/**
 * Checks transaction inclusion only. The root must come from an independently
 * validated best-chain header before its transactions affect a wallet balance.
 * Hash inputs use wire byte order, not explorer display order.
 */
object MerkleProof {
    fun verify(
        transactionHash: ByteArray,
        transactionIndex: Int,
        transactionCount: Int,
        siblings: List<ByteArray>,
        expectedRoot: ByteArray,
    ): Boolean {
        if (transactionHash.size != 32 || expectedRoot.size != 32 ||
            transactionCount <= 0 || transactionIndex !in 0 until transactionCount ||
            siblings.size > 31 || siblings.any { it.size != 32 }) return false
        var hash = transactionHash.copyOf()
        var index = transactionIndex
        var width = transactionCount
        var level = 0
        while (width > 1) {
            if (level >= siblings.size) return false
            val sibling = siblings[level++]
            val duplicated = (index xor 1) >= width
            if (duplicated && !hash.contentEquals(sibling)) return false
            // Equal real children are an ambiguous/mutated Merkle tree.
            if (!duplicated && hash.contentEquals(sibling)) return false
            hash = if (index and 1 == 0) hash256(hash + sibling) else hash256(sibling + hash)
            index /= 2
            width = width / 2 + width % 2
        }
        return level == siblings.size && hash.contentEquals(expectedRoot)
    }
}
