package com.comicrack.wifi.protocol

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

/**
 * Sealed class representing all 14 WiFi sync protocol commands.
 *
 * These commands match the legacy ComicRack CE desktop protocol exactly.
 * Each command has an opcode used in the binary wire protocol.
 */
sealed class Command(
    val opcode: Int,
) {
    companion object {
        // Protocol opcodes - must match legacy desktop exactly
        const val OPCODE_LIST_FILES = 0
        const val OPCODE_READ_FILE = 1
        const val OPCODE_FREE_SPACE = 2
        const val OPCODE_FILE_EXISTS = 3
        const val OPCODE_DELETE_FILE = 4
        const val OPCODE_WRITE_FILE = 5
        const val OPCODE_START = 6
        const val OPCODE_COMPLETED = 7
        const val OPCODE_PROGRESS_UPDATE = 8
        const val OPCODE_INFO = 9
        const val OPCODE_READ_MULTI_FILE = 10
        const val OPCODE_CHECK_ABORT = 11
        const val OPCODE_CLIENT_PONG = 12
        const val OPCODE_SERVER_AVAILABLE = 13

        /**
         * Parse a command from the protocol codec.
         * Reads opcode byte first, then any command-specific data.
         *
         * @throws ProtocolException if opcode is unknown
         */
        fun parseCommand(codec: ProtocolCodec): Command {
            val opcode = codec.readByte()
            return when (opcode) {
                OPCODE_LIST_FILES -> ListFiles
                OPCODE_READ_FILE -> ReadFile(codec.readString())
                OPCODE_FREE_SPACE -> FreeSpace
                OPCODE_FILE_EXISTS -> FileExists(codec.readString())
                OPCODE_DELETE_FILE -> DeleteFile(codec.readString())
                OPCODE_WRITE_FILE ->
                    WriteFile(
                        filename = codec.readString(),
                        size = codec.readLong(),
                    )
                OPCODE_START -> Start(codec.readString())
                OPCODE_COMPLETED -> Completed(codec.readString())
                OPCODE_PROGRESS_UPDATE -> ProgressUpdate(codec.readByte())
                OPCODE_INFO -> Info
                OPCODE_READ_MULTI_FILE -> {
                    val count = codec.readInt()
                    val filenames = (0 until count).map { codec.readString() }
                    com.comicrack.wifi.server.SyncServer.Companion.logProtocol(
                        "ReadMultiFile: count=$count, files=$filenames",
                    )
                    ReadMultiFile(filenames)
                }
                OPCODE_CHECK_ABORT -> CheckAbort
                OPCODE_CLIENT_PONG -> ClientPong
                OPCODE_SERVER_AVAILABLE -> ServerAvailable(codec.readInt())
                else -> throw ProtocolException("Unknown command opcode: $opcode")
            }
        }
    }

    // ==========================================================================
    // File operation commands
    // ==========================================================================

    /**
     * Request list of all sync files on device.
     * Response: newline-separated list of filenames as length-prefixed string.
     */
    data object ListFiles : Command(OPCODE_LIST_FILES)

    /**
     * Request to read a specific file.
     * Response: file size (long) followed by file data.
     */
    data class ReadFile(
        val filename: String,
    ) : Command(OPCODE_READ_FILE)

    /**
     * Request available storage space on device.
     * Response: free space in bytes (long).
     */
    data object FreeSpace : Command(OPCODE_FREE_SPACE)

    /**
     * Check if a file exists on device.
     * Response: boolean (1 byte, non-zero = exists).
     */
    data class FileExists(
        val filename: String,
    ) : Command(OPCODE_FILE_EXISTS)

    /**
     * Delete a file from device.
     * Response: boolean (1 byte, non-zero = success).
     */
    data class DeleteFile(
        val filename: String,
    ) : Command(OPCODE_DELETE_FILE)

    /**
     * Write a file to device. File data follows immediately after this command.
     * Response: boolean (1 byte, non-zero = success).
     */
    data class WriteFile(
        val filename: String,
        val size: Long,
    ) : Command(OPCODE_WRITE_FILE)

    // ==========================================================================
    // Sync lifecycle commands
    // ==========================================================================

    /**
     * Sync session started.
     * No response required.
     */
    data class Start(
        val message: String,
    ) : Command(OPCODE_START)

    /**
     * Sync session completed.
     * No response required.
     */
    data class Completed(
        val message: String,
    ) : Command(OPCODE_COMPLETED)

    /**
     * Progress update during sync.
     * No response required.
     */
    data class ProgressUpdate(
        val percent: Int,
    ) : Command(OPCODE_PROGRESS_UPDATE)

    /**
     * Exchange device/server info.
     * Response: sync version (int), then reads licensed (bool), version (int), key (string), flag (bool).
     */
    data object Info : Command(OPCODE_INFO)

    // ==========================================================================
    // Multi-file and status commands
    // ==========================================================================

    /**
     * Read multiple files in batch.
     * Response: for each file, size (long) followed by data.
     */
    data class ReadMultiFile(
        val filenames: List<String>,
    ) : Command(OPCODE_READ_MULTI_FILE)

    /**
     * Check if sync should be aborted.
     * Response: boolean (1 byte, non-zero = abort requested).
     */
    data object CheckAbort : Command(OPCODE_CHECK_ABORT)

    /**
     * Client heartbeat/pong response.
     * No response required.
     */
    data object ClientPong : Command(OPCODE_CLIENT_PONG)

    /**
     * Server announcing availability with control port.
     * No response required.
     */
    data class ServerAvailable(
        val controlPort: Int,
    ) : Command(OPCODE_SERVER_AVAILABLE)
}
