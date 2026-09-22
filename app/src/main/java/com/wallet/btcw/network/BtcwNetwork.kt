package com.wallet.btcw.network

/** Values from the user's BitcoinPoW node, src/kernel/chainparams.cpp. */
object BtcwNetwork {
    const val port = 8555
    const val genesisHash = "00000000e073c081280853d8b51b64037b6a20f9d11d191ed18182c2b55a92d7"
    const val bech32Hrp = "bc"
    val dnsSeeds: List<String> = (1..8).map { "seed$it.bitcoin-pow.org" }
    val messageMagic: ByteArray get() = byteArrayOf(0xf9.toByte(), 0xbf.toByte(), 0xb4.toByte(), 0xd9.toByte())
}
