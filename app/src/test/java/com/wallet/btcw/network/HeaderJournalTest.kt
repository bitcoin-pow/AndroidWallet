package com.wallet.btcw.network

import java.io.File
import java.io.RandomAccessFile
import org.junit.Assert.*
import org.junit.Test

class HeaderJournalTest {
    @Test fun restoresProgressAndTruncatesInterruptedWrite() {
        val file = File.createTempFile("btcw-headers", ".dat")
        try {
            val store = HeaderJournal(file)
            val original = store.restore()
            val header = ByteArray(80)
            original.tipHash.copyInto(header, 4)
            val batch = byteArrayOf(1) + header + byteArrayOf(0)
            original.appendHeaders(batch)
            store.append(batch)
            val validLength = file.length()
            RandomAccessFile(file, "rw").use { it.seek(it.length()); it.writeInt(100); it.write(1) }
            val restored = store.restore()
            assertEquals(1, restored.downloadedHeight)
            assertEquals(0, restored.checkpointAnchoredHeight)
            assertArrayEquals(original.tipHash, restored.tipHash)
            assertEquals(validLength, file.length())
            store.reset()
            assertEquals(0, store.restore().downloadedHeight)
        } finally { file.delete() }
    }

    @Test fun dropsDisconnectedAndOversizedJournalRecords() {
        val file = File.createTempFile("btcw-headers", ".dat")
        try {
            val store = HeaderJournal(file)
            store.append(byteArrayOf(1) + ByteArray(80) + byteArrayOf(0))
            assertEquals(0, store.restore().downloadedHeight)
            assertEquals(0L, file.length())
            RandomAccessFile(file, "rw").use { it.writeInt(Int.MAX_VALUE) }
            assertEquals(0, store.restore().downloadedHeight)
            assertEquals(0L, file.length())
        } finally { file.delete() }
    }
}
