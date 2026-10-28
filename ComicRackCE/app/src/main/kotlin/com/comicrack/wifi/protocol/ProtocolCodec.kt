package com.comicrack.wifi.protocol

import com.comicrack.reader.BuildConfig
import timber.log.Timber
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Binary protocol codec for WiFi sync with legacy ComicRack CE desktop.
 *
 * CRITICAL: All multi-byte values use BIG-ENDIAN (network byte order) encoding
 * to match the legacy desktop application's binary protocol.
 *
 * String format: 4-byte length prefix (big-endian) followed by UTF-8 bytes.
 */
class ProtocolCodec(
    private val input: InputStream,
    private val output: OutputStream,
) {
    companion object {
        private const val TAG = "ProtocolCodec"
        private const val VERBOSE = true // Set to false to reduce log spam

        // =====================================================================
        // Buffer size constants (bytes)
        // =====================================================================
        private const val INT_BUFFER_SIZE = 4 // 32-bit integer
        private const val LONG_BUFFER_SIZE = 8 // 64-bit long
        private const val STRING_LENGTH_PREFIX_SIZE = 4 // String length prefix

        // =====================================================================
        // Byte value constants
        // =====================================================================
        private const val BYTE_MASK = 0xFF // Unsigned byte mask
        private const val BOOLEAN_TRUE = 1 // Boolean true value
        private const val BOOLEAN_FALSE = 0 // Boolean false value
        private const val EOF_MARKER = -1 // End-of-file marker from InputStream.read()

        // =====================================================================
        // String formatting constants
        // =====================================================================
        private const val HEX_RADIX = 16
        private const val HEX_PADDING_WIDTH = 2
        private const val HEX_PADDING_CHAR = '0'
        private const val STRING_PREVIEW_LENGTH = 50 // Max chars to show in log

        // =====================================================================
        // File size formatting constants (bytes)
        // =====================================================================
        private const val KB_SIZE = 1024L
        private const val MB_SIZE = 1024L * 1024L
    }

    // Byte counters for session statistics
    var totalBytesRead: Long = 0
        private set
    var totalBytesWritten: Long = 0
        private set
    // ==========================================================================
    // Write operations (Big-endian)
    // ==========================================================================

    /**
     * Write a single byte to the output stream.
     */
    fun writeByte(value: Int) {
        output.write(value and BYTE_MASK)
        totalBytesWritten += 1
        val hex = (value and BYTE_MASK).toString(HEX_RADIX)
            .padStart(HEX_PADDING_WIDTH, HEX_PADDING_CHAR)
        logWrite("byte", 1, "0x$hex")
    }

    /**
     * Write a 32-bit integer in big-endian format.
     */
    fun writeInt(value: Int) {
        val buffer =
            ByteBuffer
                .allocate(INT_BUFFER_SIZE)
                .order(ByteOrder.BIG_ENDIAN)
                .putInt(value)
        output.write(buffer.array())
        totalBytesWritten += INT_BUFFER_SIZE
        logWrite("int32", INT_BUFFER_SIZE, "$value (0x${value.toString(HEX_RADIX)})")
    }

    /**
     * Write a 64-bit long in big-endian format.
     */
    fun writeLong(value: Long) {
        val buffer =
            ByteBuffer
                .allocate(LONG_BUFFER_SIZE)
                .order(ByteOrder.BIG_ENDIAN)
                .putLong(value)
        output.write(buffer.array())
        totalBytesWritten += LONG_BUFFER_SIZE
        logWrite("int64", LONG_BUFFER_SIZE, "$value")
    }

    /**
     * Write a string with 4-byte length prefix followed by UTF-8 bytes.
     * This matches the legacy protocol's string format.
     */
    fun writeString(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        writeInt(bytes.size)
        output.write(bytes)
        totalBytesWritten += bytes.size
        val preview = value.take(STRING_PREVIEW_LENGTH)
        val ellipsis = if (value.length > STRING_PREVIEW_LENGTH) "..." else ""
        logWrite("string", STRING_LENGTH_PREFIX_SIZE + bytes.size, "\"$preview\"$ellipsis")
    }

    /**
     * Write raw bytes without any length prefix.
     */
    fun writeBytes(data: ByteArray) {
        output.write(data)
        totalBytesWritten += data.size
        logWrite("bytes", data.size, "${data.size} raw bytes")
    }

    /**
     * Write a boolean as a single byte (1 for true, 0 for false).
     */
    fun writeBoolean(value: Boolean) {
        writeByte(if (value) BOOLEAN_TRUE else BOOLEAN_FALSE)
    }

    // ==========================================================================
    // Read operations (Big-endian)
    // ==========================================================================

    /**
     * Read a single byte as an unsigned value (0-255).
     */
    fun readByte(): Int {
        val value = input.read()
        if (value == EOF_MARKER) {
            throw java.io.EOFException("End of stream while reading byte")
        }
        totalBytesRead += 1
        logRead("byte", 1, "0x${value.toString(HEX_RADIX).padStart(HEX_PADDING_WIDTH, HEX_PADDING_CHAR)}")
        return value
    }

    /**
     * Read a 32-bit integer in big-endian format.
     */
    fun readInt(): Int {
        val bytes = readBytesExact(INT_BUFFER_SIZE)
        val value =
            ByteBuffer
                .wrap(bytes)
                .order(ByteOrder.BIG_ENDIAN)
                .getInt()
        logRead("int32", INT_BUFFER_SIZE, "$value (0x${value.toString(HEX_RADIX)})")
        return value
    }

    /**
     * Read a 64-bit long in big-endian format.
     */
    fun readLong(): Long {
        val bytes = readBytesExact(LONG_BUFFER_SIZE)
        val value =
            ByteBuffer
                .wrap(bytes)
                .order(ByteOrder.BIG_ENDIAN)
                .getLong()
        logRead("int64", LONG_BUFFER_SIZE, "$value")
        return value
    }

    /**
     * Read a string with 4-byte length prefix followed by UTF-8 bytes.
     */
    fun readString(): String {
        val length = readInt()
        if (length < 0) {
            throw ProtocolException("Invalid string length: $length")
        }
        if (length == 0) {
            return ""
        }
        val bytes = readBytesExact(length)
        val value = String(bytes, Charsets.UTF_8)
        val preview = value.take(STRING_PREVIEW_LENGTH)
        val ellipsis = if (value.length > STRING_PREVIEW_LENGTH) "..." else ""
        logRead("string", STRING_LENGTH_PREFIX_SIZE + length, "\"$preview\"$ellipsis")
        return value
    }

    /**
     * Read a specific number of bytes.
     */
    fun readBytes(length: Int): ByteArray {
        val bytes = readBytesExact(length)
        logRead("bytes", length, "$length raw bytes")
        return bytes
    }

    /**
     * Read a boolean (non-zero = true, zero = false).
     */
    fun readBoolean(): Boolean = readByte() != BOOLEAN_FALSE

    // ==========================================================================
    // Helper methods
    // ==========================================================================

    /**
     * Read exactly the specified number of bytes, throwing if not available.
     */
    private fun readBytesExact(length: Int): ByteArray {
        val buffer = ByteArray(length)
        var totalRead = 0
        while (totalRead < length) {
            val bytesRead = input.read(buffer, totalRead, length - totalRead)
            if (bytesRead == EOF_MARKER) {
                throw ProtocolException(
                    "Unexpected end of stream: expected $length bytes, got $totalRead",
                )
            }
            totalRead += bytesRead
        }
        totalBytesRead += length
        return buffer
    }

    /**
     * Get the underlying input stream for direct reading.
     */
    fun readInputStream(): InputStream = input

    /**
     * Flush the output stream.
     */
    fun flush() {
        output.flush()
    }

    /**
     * Reset byte counters (call at start of new session).
     */
    fun resetCounters() {
        totalBytesRead = 0
        totalBytesWritten = 0
    }

    /**
     * Get session statistics summary.
     */
    fun getStats(): String = "Read: ${formatBytes(totalBytesRead)}, Written: ${formatBytes(totalBytesWritten)}"

    // ==========================================================================
    // Logging helpers
    // ==========================================================================

    private fun logRead(
        type: String,
        bytes: Int,
        value: String,
    ) {
        if (BuildConfig.DEBUG && VERBOSE) {
            Timber.tag(TAG).v("← READ  [$type] $bytes bytes: $value")
        }
    }

    private fun logWrite(
        type: String,
        bytes: Int,
        value: String,
    ) {
        if (BuildConfig.DEBUG && VERBOSE) {
            Timber.tag(TAG).v("→ WRITE [$type] $bytes bytes: $value")
        }
    }

    private fun formatBytes(bytes: Long): String =
        when {
            bytes < KB_SIZE -> "$bytes B"
            bytes < MB_SIZE -> "${bytes / KB_SIZE} KB"
            else -> "%.2f MB".format(bytes / (MB_SIZE.toDouble()))
        }
}

/**
 * Exception thrown when protocol encoding/decoding fails.
 */
class ProtocolException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
