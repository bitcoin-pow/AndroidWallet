package com.wallet.btcw.network

import java.io.File
import java.io.RandomAccessFile

/** Replayable bounded header batches, kept in app-private no-backup storage. */
internal class HeaderJournal(private val file: File) {
    fun restore(): CheckpointHeaderChain {
        val chain = CheckpointHeaderChain()
        if (!file.exists()) return chain
        RandomAccessFile(file, "rw").use { journal ->
            if (journal.length() > MAX_BYTES) { journal.setLength(0); return chain }
            while (journal.filePointer < journal.length()) {
                val start = journal.filePointer
                try {
                    val size = journal.readInt()
                    require(size in 1..MAX_BATCH && size <= journal.length() - journal.filePointer)
                    val payload = ByteArray(size).also { journal.readFully(it) }
                    chain.appendHeaders(payload)
                } catch (_: IllegalArgumentException) {
                    journal.setLength(start)
                    break
                } catch (_: java.io.EOFException) {
                    journal.setLength(start)
                    break
                }
            }
        }
        return chain
    }

    fun append(payload: ByteArray) {
        require(payload.size in 1..MAX_BATCH)
        RandomAccessFile(file, "rw").use {
            check(it.length() + 4 + payload.size <= MAX_BYTES) { "Header storage limit reached" }
            it.seek(it.length()); it.writeInt(payload.size); it.write(payload); it.fd.sync()
        }
    }

    fun reset() { RandomAccessFile(file, "rw").use { it.setLength(0); it.fd.sync() } }

    companion object {
        private const val MAX_BATCH = 405_000
        private const val MAX_BYTES = 128L * 1024 * 1024
    }
}
