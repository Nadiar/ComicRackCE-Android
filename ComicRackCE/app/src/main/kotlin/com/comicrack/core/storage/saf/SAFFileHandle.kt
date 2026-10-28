package com.comicrack.core.storage.saf

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

import android.os.ParcelFileDescriptor
import okio.FileHandle
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Okio FileHandle backed by a ParcelFileDescriptor from SAF.
 *
 * This provides random-access read/write capabilities for SAF files.
 */
internal class SAFFileHandle(
    private val pfd: ParcelFileDescriptor,
    readWrite: Boolean,
) : FileHandle(readWrite) {
    private val isReadWrite = readWrite

    private val fd = pfd.fileDescriptor

    override fun protectedSize(): Long = pfd.statSize

    override fun protectedRead(
        fileOffset: Long,
        array: ByteArray,
        arrayOffset: Int,
        byteCount: Int,
    ): Int {
        val fis = FileInputStream(fd)
        fis.channel.position(fileOffset)
        return fis.read(array, arrayOffset, byteCount)
    }

    override fun protectedWrite(
        fileOffset: Long,
        array: ByteArray,
        arrayOffset: Int,
        byteCount: Int,
    ) {
        check(isReadWrite) { "FileHandle is read-only" }
        val fos = FileOutputStream(fd)
        fos.channel.position(fileOffset)
        fos.write(array, arrayOffset, byteCount)
    }

    override fun protectedFlush() {
        if (isReadWrite) {
            pfd.fileDescriptor.sync()
        }
    }

    override fun protectedResize(size: Long) {
        check(isReadWrite) { "FileHandle is read-only" }
        val fos = FileOutputStream(fd)
        fos.channel.truncate(size)
    }

    override fun protectedClose() {
        pfd.close()
    }
}
