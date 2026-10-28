package com.comicrack.wifi.protocol

import com.comicrack.wifi.SyncIdentity
import com.comicrack.wifi.file.FileManager
import com.comicrack.wifi.progress.SyncProgressTracker
import timber.log.Timber

/**
 * Handles incoming protocol commands and produces responses.
 *
 * This class processes all 14 protocol commands from the legacy desktop
 * and generates appropriate responses according to the wire protocol.
 */
class CommandHandler(
    private val fileManager: FileManager,
    private val progressTracker: SyncProgressTracker,
    private val repository: com.comicrack.data.preferences.AppPreferencesRepository,
    private val deviceKey: String,
    private val deviceName: String,
    private val onCompleted: () -> Unit,
) {
    private var isAborted = false

    companion object {
        private const val TAG = "CommandHandler"

        // ======================================================================
        // Regex match group indices
        // ======================================================================
        private const val REGEX_GROUP_INDEX = 1

        // ======================================================================
        // Read percentage calculation constants
        // ======================================================================
        private const val READ_PERCENTAGE_SCALE = 100
        private const val READ_PERCENTAGE_OFFSET = 1
        private const val READ_COMPLETION_THRESHOLD = 95

        // ======================================================================
        // Default values for missing XML fields
        // ======================================================================
        private const val DEFAULT_PAGE_VALUE = 0
        private const val DEFAULT_PAGE_COUNT_VALUE = 0

        // ======================================================================
        // Coerce boundaries for read percentage
        // ======================================================================
        private const val MIN_READ_PERCENTAGE = 1
        private const val MAX_READ_PERCENTAGE = 100

        /**
         * Validate filename to prevent path traversal attacks.
         * Rejects filenames containing ".." or starting with "/" or "\".
         *
         * @param filename The filename to validate
         * @return true if filename is safe, false if it contains path traversal patterns
         */
        fun isValidFilename(filename: String): Boolean {
            if (filename.isEmpty()) return false
            if (filename.contains("..")) return false
            if (filename.startsWith("/") || filename.startsWith("\\")) return false
            // Check for other common path traversal patterns
            if (filename.contains(":/")) return false // Windows absolute paths like C:/
            return true
        }
    }

    /**
     * Request sync to be aborted.
     */
    fun requestAbort() {
        isAborted = true
    }

    /**
     * Reset abort status for new sync session.
     */
    fun resetAbort() {
        isAborted = false
    }

    /**
     * Result of handling a command - may request additional action from server.
     */
    sealed class HandleResult {
        object None : HandleResult()

        data class SendBeaconTo(
            val port: Int,
        ) : HandleResult()
    }

    /**
     * Handle a command and write response to codec.
     * Returns a result that may request additional action from the server.
     */
    suspend fun handle(
        command: Command,
        codec: ProtocolCodec,
    ): HandleResult {
        val opcodeStr = String.format("%02X", command.opcode)
        Timber.tag(TAG).i("PROTOCOL [%s] ← %s", opcodeStr, command::class.simpleName)

        val commandResult = dispatchCommand(command, codec)

        codec.flush()
        return commandResult
    }

    /**
     * Dispatch command to appropriate handler using functional dispatch pattern.
     * Reduces cyclomatic complexity by extracting the large when expression.
     */
    private suspend fun dispatchCommand(command: Command, codec: ProtocolCodec): HandleResult {
        return when (command) {
            is Command.ListFiles -> handleCommandListFiles(codec)
            is Command.ReadFile -> handleCommandReadFile(command, codec)
            is Command.FreeSpace -> handleCommandFreeSpace(codec)
            is Command.FileExists -> handleCommandFileExists(command, codec)
            is Command.DeleteFile -> handleCommandDeleteFile(command, codec)
            is Command.WriteFile -> handleCommandWriteFile(command, codec)
            is Command.Start -> handleCommandStart(command)
            is Command.Completed -> handleCommandCompleted(command)
            is Command.ProgressUpdate -> handleCommandProgressUpdate(command)
            is Command.Info -> handleCommandInfo(codec)
            is Command.ReadMultiFile -> handleCommandReadMultiFile(command, codec)
            is Command.CheckAbort -> handleCommandCheckAbort(codec)
            is Command.ClientPong -> handleCommandClientPong()
            is Command.ServerAvailable -> handleServerAvailable(command)
        }
    }

    // Dispatch wrappers that add HandleResult.None boxing
    private suspend fun handleCommandListFiles(codec: ProtocolCodec) =
        run { handleListFiles(codec); HandleResult.None }

    private suspend fun handleCommandReadFile(command: Command.ReadFile, codec: ProtocolCodec) =
        run { handleReadFile(command, codec); HandleResult.None }

    private suspend fun handleCommandFreeSpace(codec: ProtocolCodec) =
        run { handleFreeSpace(codec); HandleResult.None }

    private suspend fun handleCommandFileExists(command: Command.FileExists, codec: ProtocolCodec) =
        run { handleFileExists(command, codec); HandleResult.None }

    private suspend fun handleCommandDeleteFile(command: Command.DeleteFile, codec: ProtocolCodec) =
        run { handleDeleteFile(command, codec); HandleResult.None }

    private suspend fun handleCommandWriteFile(command: Command.WriteFile, codec: ProtocolCodec) =
        run { handleWriteFile(command, codec); HandleResult.None }

    private fun handleCommandStart(command: Command.Start) =
        run { handleStart(command); HandleResult.None }

    private fun handleCommandCompleted(command: Command.Completed) =
        run { handleCompleted(command); HandleResult.None }

    private fun handleCommandProgressUpdate(command: Command.ProgressUpdate) =
        run { handleProgressUpdate(command); HandleResult.None }

    private suspend fun handleCommandInfo(codec: ProtocolCodec) =
        run { handleInfo(codec); HandleResult.None }

    private suspend fun handleCommandReadMultiFile(
        command: Command.ReadMultiFile,
        codec: ProtocolCodec,
    ) = run { handleReadMultiFile(command, codec); HandleResult.None }

    private suspend fun handleCommandCheckAbort(codec: ProtocolCodec) =
        run { handleCheckAbort(codec); HandleResult.None }

    private fun handleCommandClientPong() =
        run { handleClientPong(); HandleResult.None }

    // ==========================================================================
    // File operation handlers
    // ==========================================================================

    private suspend fun handleListFiles(codec: ProtocolCodec) {
        try {
            val allFiles = fileManager.listFiles()
            val fileList = allFiles.joinToString("\n")
            codec.writeString(fileList)
            Timber.tag(TAG).i("PROTOCOL [00] → ListFiles: returned ${allFiles.size} files")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "PROTOCOL [00] → ListFiles: failed to list files")
            codec.writeString("") // Empty list on error
        }
    }

    private suspend fun handleReadFile(
        command: Command.ReadFile,
        codec: ProtocolCodec,
    ) {
        // Validate filename to prevent path traversal
        if (!isValidFilename(command.filename)) {
            Timber.tag(TAG).w("PROTOCOL [01] → ReadFile: rejected invalid filename: ${command.filename}")
            codec.writeLong(0L)
            return
        }

        try {
            val fileContent = fileManager.readFile(command.filename)
            if (fileContent != null) {
                codec.writeLong(fileContent.size.toLong())
                codec.writeBytes(fileContent)
                Timber.tag(TAG).i("PROTOCOL [01] → ReadFile: sent ${fileContent.size} bytes for ${command.filename}")
            } else {
                codec.writeLong(0L)
                Timber.tag(TAG).w("PROTOCOL [01] → ReadFile: file not found: ${command.filename}")
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "PROTOCOL [01] → ReadFile: failed to read ${command.filename}")
            codec.writeLong(0L)
        }
    }

    private suspend fun handleFreeSpace(codec: ProtocolCodec) {
        try {
            val freeSpace = fileManager.getFreeSpace()
            codec.writeLong(freeSpace)
            Timber.tag(TAG).i("PROTOCOL [02] → FreeSpace: $freeSpace bytes")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "PROTOCOL [02] → FreeSpace: failed to get free space")
            codec.writeLong(0L)
        }
    }

    private suspend fun handleFileExists(
        command: Command.FileExists,
        codec: ProtocolCodec,
    ) {
        // Validate filename to prevent path traversal
        if (!isValidFilename(command.filename)) {
            Timber.tag(TAG).w("PROTOCOL [03] → FileExists: rejected invalid filename: ${command.filename}")
            codec.writeBoolean(false)
            return
        }

        try {
            val exists = fileManager.fileExists(command.filename)
            codec.writeBoolean(exists)
            Timber.tag(TAG).i("PROTOCOL [03] → FileExists: ${command.filename} = $exists")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "PROTOCOL [03] → FileExists: failed to check ${command.filename}")
            codec.writeBoolean(false)
        }
    }

    private suspend fun handleDeleteFile(
        command: Command.DeleteFile,
        codec: ProtocolCodec,
    ) {
        // Validate filename to prevent path traversal
        if (!isValidFilename(command.filename)) {
            Timber.tag(TAG).w("PROTOCOL [04] → DeleteFile: rejected invalid filename: ${command.filename}")
            codec.writeBoolean(false)
            return
        }

        try {
            val success = fileManager.deleteFile(command.filename)
            codec.writeBoolean(success)
            Timber.tag(TAG).i("PROTOCOL [04] → DeleteFile: ${command.filename} = $success")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "PROTOCOL [04] → DeleteFile: failed to delete ${command.filename}")
            codec.writeBoolean(false)
        }
    }

    private suspend fun handleWriteFile(
        command: Command.WriteFile,
        codec: ProtocolCodec,
    ) {
        // Validate filename to prevent path traversal
        if (!isValidFilename(command.filename)) {
            Timber.tag(TAG).w("PROTOCOL [05] → WriteFile: rejected invalid filename: ${command.filename}")
            // Must consume the incoming bytes even on rejection
            LimitedInputStream(codec, command.size).readBytes()
            codec.writeBoolean(false)
            return
        }

        try {
            // Early return for INI file handling
            if (command.filename.equals(SyncIdentity.INI_FILENAME, ignoreCase = true)) {
                handleWriteFileIni(command, codec)
                return
            }

            // Early return for XML sidecar files with debug logging
            if (command.filename.endsWith(".xml")) {
                handleWriteFileXml(command, codec)
                return
            }

            // Default: stream directly from codec to file manager
            val success = fileManager.writeFile(
                command.filename,
                command.size,
                LimitedInputStream(codec, command.size),
            )
            codec.writeBoolean(success)
            Timber.tag(TAG).i("PROTOCOL [05] → WriteFile: ${command.filename} (${command.size} bytes) = $success")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "PROTOCOL [05] → WriteFile: failed to write ${command.filename}")
            codec.writeBoolean(false)
        }
    }

    /**
     * Handle writing INI configuration file with device name update.
     */
    private suspend fun handleWriteFileIni(command: Command.WriteFile, codec: ProtocolCodec) {
        val stream = LimitedInputStream(codec, command.size)
        val contentBytes = stream.readBytes()
        val content = contentBytes.toString(Charsets.UTF_8)

        Timber.tag(TAG).i("WriteFile: Desktop updating ${SyncIdentity.INI_FILENAME} (${command.size} bytes)")

        // Check for device name change
        val nameRegex = Regex("""(?i)Name=(.*)""")
        val newName = nameRegex.find(content)?.groupValues?.get(1)?.trim()
        if (!newName.isNullOrEmpty() && newName != deviceName) {
            Timber.tag(TAG).i("WriteFile: Desktop changed Name: '$deviceName' -> '$newName'")
            repository.setWifiSyncDeviceName(newName)
        }

        // Persist to physical disk
        val success = fileManager.writeFile(
            command.filename,
            command.size,
            contentBytes.inputStream(),
        )
        codec.writeBoolean(success)
    }

    /**
     * Handle writing XML sidecar file with read status logging.
     */
    private suspend fun handleWriteFileXml(command: Command.WriteFile, codec: ProtocolCodec) {
        val stream = LimitedInputStream(codec, command.size)
        val contentBytes = stream.readBytes()
        val xmlContent = String(contentBytes, Charsets.UTF_8)

        // Extract and log metadata
        logXmlReadStatus(command.filename, xmlContent)

        // Write the file
        val success = fileManager.writeFile(
            command.filename,
            command.size,
            contentBytes.inputStream(),
        )
        codec.writeBoolean(success)
        Timber.tag(TAG).i("PROTOCOL [05] → WriteFile: ${command.filename} (${command.size} bytes) = $success")
    }

    /**
     * Extract XML metadata and log read status calculations.
     * Reduces nesting by extracting regex matching and logging logic.
     */
    private fun logXmlReadStatus(filename: String, xmlContent: String) {
        val lastPageReadMatch = Regex("""<LastPageRead>(\d+)</LastPageRead>""").find(xmlContent)
        val pageCountMatch = Regex("""<PageCount>(\d+)</PageCount>""").find(xmlContent)
        val currentPageMatch = Regex("""<CurrentPage>(\d+)</CurrentPage>""").find(xmlContent)
        val openCountMatch = Regex("""<OpenCount>(\d+)</OpenCount>""").find(xmlContent)

        Timber.tag(TAG).d("PROTOCOL [05] XML DEBUG INCOMING from Desktop for $filename:")
        val lastPageVal = lastPageReadMatch?.groupValues?.get(REGEX_GROUP_INDEX)
            ?: "NOT PRESENT (defaults to 0)"
        Timber.tag(TAG).d("  LastPageRead: $lastPageVal")
        val pageCountVal = pageCountMatch?.groupValues?.get(REGEX_GROUP_INDEX)
            ?: "NOT PRESENT"
        Timber.tag(TAG).d("  PageCount: $pageCountVal")
        val currentPageVal = currentPageMatch?.groupValues?.get(REGEX_GROUP_INDEX)
            ?: "NOT PRESENT (defaults to 0)"
        Timber.tag(TAG).d("  CurrentPage: $currentPageVal")
        val openCountVal = openCountMatch?.groupValues?.get(REGEX_GROUP_INDEX)
            ?: "NOT PRESENT (defaults to 0)"
        Timber.tag(TAG).d("  OpenCount: $openCountVal")

        // Calculate read percentage
        val lastPageRead = lastPageReadMatch?.groupValues
            ?.get(REGEX_GROUP_INDEX)?.toIntOrNull() ?: DEFAULT_PAGE_VALUE
        val pageCount = pageCountMatch?.groupValues
            ?.get(REGEX_GROUP_INDEX)?.toIntOrNull() ?: DEFAULT_PAGE_COUNT_VALUE
        val readPercentage = computeReadPercentage(lastPageRead, pageCount)
        val hasBeenRead = readPercentage >= READ_COMPLETION_THRESHOLD
        Timber.tag(TAG).d("  CALCULATED: ReadPercentage=$readPercentage%, HasBeenRead=$hasBeenRead")
    }

    /**
     * Compute read percentage from page numbers.
     * Formula: (lastPageRead + 1) * 100 / pageCount, coerced to [1, 100]
     */
    private fun computeReadPercentage(lastPageRead: Int, pageCount: Int): Int {
        if (pageCount <= 0 || lastPageRead <= 0) return DEFAULT_PAGE_VALUE
        return ((lastPageRead + READ_PERCENTAGE_OFFSET) * READ_PERCENTAGE_SCALE / pageCount)
            .coerceIn(MIN_READ_PERCENTAGE, MAX_READ_PERCENTAGE)
    }

    // ==========================================================================
    // Sync lifecycle handlers
    // ==========================================================================

    private fun handleStart(command: Command.Start) {
        Timber.tag(TAG).i("PROTOCOL [06] ← Start: ${command.message}")
        progressTracker.reset()
    }

    private fun handleCompleted(command: Command.Completed) {
        Timber.tag(TAG).i("PROTOCOL [07] ← Completed: ${command.message}")
        progressTracker.complete()
        onCompleted()
    }

    private fun handleProgressUpdate(command: Command.ProgressUpdate) {
        progressTracker.updateProgress(command.percent)
        Timber.tag(TAG).i("PROTOCOL [08] ← ProgressUpdate: ${command.percent}%")
    }

    private fun handleInfo(codec: ProtocolCodec) {
        try {
            // Desktop sends its sync version first
            val desktopVersion = codec.readInt()
            Timber.tag(TAG).i("PROTOCOL [09] ↔ Info: Handshake. Desktop version: $desktopVersion")

            // Respond with our info: licensed, version, key, flag
            codec.writeBoolean(true) // Licensed (Full edition)
            codec.writeInt(SyncIdentity.SYNC_VERSION) // Version Code (MUST match comicrack.ini)
            codec.writeString(deviceKey) // Unique Device Key
            codec.writeBoolean(false) // Flag (Unknown, sending false)

            codec.flush()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "PROTOCOL [09] → Info: handshake failed")
            throw e // Re-throw as handshake failure is critical
        }
    }

    // ==========================================================================
    // Multi-file and status handlers
    // ==========================================================================

    private suspend fun handleReadMultiFile(
        command: Command.ReadMultiFile,
        codec: ProtocolCodec,
    ) {
        try {
            for (filename in command.filenames) {
                processMultiFile(filename, codec)
            }
            Timber.tag(TAG).i("PROTOCOL [0A] → ReadMultiFile: sent ${command.filenames.size} files")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "PROTOCOL [0A] → ReadMultiFile: failed during multi-file read")
            throw e // Re-throw as partial reads corrupt protocol state
        }
    }

    /**
     * Process and stream a single file for multi-file read operation.
     * Extracted to reduce loop nesting depth.
     */
    private suspend fun processMultiFile(filename: String, codec: ProtocolCodec) {
        // Validate filename to prevent path traversal
        if (!isValidFilename(filename)) {
            Timber.tag(TAG).w("PROTOCOL [0A] → ReadMultiFile: rejected invalid filename: $filename")
            codec.writeLong(0L)
            return
        }

        val data = fileManager.readFile(filename) ?: run {
            codec.writeLong(0L)
            return
        }

        codec.writeLong(data.size.toLong())
        codec.writeBytes(data)
        codec.flush() // Crucial for multi-file streaming

        // Log XML metadata for debug diagnostics
        if (filename.endsWith(".xml")) {
            val xmlContent = String(data, Charsets.UTF_8)
            Timber.tag(TAG).d("PROTOCOL [0A] XML DEBUG for $filename:")
            logXmlReadStatusForMultiFile(xmlContent)
        }
    }

    /**
     * Log XML read status for multi-file operation (slightly different format than write operation).
     */
    private fun logXmlReadStatusForMultiFile(xmlContent: String) {
        val lastPageReadMatch = Regex("""<LastPageRead>(\d+)</LastPageRead>""").find(xmlContent)
        val pageCountMatch = Regex("""<PageCount>(\d+)</PageCount>""").find(xmlContent)
        val currentPageMatch = Regex("""<CurrentPage>(\d+)</CurrentPage>""").find(xmlContent)
        val openCountMatch = Regex("""<OpenCount>(\d+)</OpenCount>""").find(xmlContent)

        val lastPageVal = lastPageReadMatch?.groupValues?.get(REGEX_GROUP_INDEX)
            ?: "NOT PRESENT (defaults to 0)"
        Timber.tag(TAG).d("  LastPageRead: $lastPageVal")
        val pageCountVal = pageCountMatch?.groupValues?.get(REGEX_GROUP_INDEX)
            ?: "NOT PRESENT"
        Timber.tag(TAG).d("  PageCount: $pageCountVal")
        val currentPageVal = currentPageMatch?.groupValues?.get(REGEX_GROUP_INDEX)
            ?: "NOT PRESENT (defaults to 0)"
        Timber.tag(TAG).d("  CurrentPage: $currentPageVal")
        val openCountVal = openCountMatch?.groupValues?.get(REGEX_GROUP_INDEX)
            ?: "NOT PRESENT (defaults to 0)"
        Timber.tag(TAG).d("  OpenCount: $openCountVal")

        val lastPageRead = lastPageReadMatch?.groupValues
            ?.get(REGEX_GROUP_INDEX)?.toIntOrNull() ?: DEFAULT_PAGE_VALUE
        val pageCount = pageCountMatch?.groupValues
            ?.get(REGEX_GROUP_INDEX)?.toIntOrNull() ?: DEFAULT_PAGE_COUNT_VALUE
        val readPercentage = computeReadPercentage(lastPageRead, pageCount)
        val hasBeenRead = readPercentage >= READ_COMPLETION_THRESHOLD
        Timber.tag(TAG).d("  CALCULATED: ReadPercentage=$readPercentage%, HasBeenRead=$hasBeenRead")
    }

    private fun handleCheckAbort(codec: ProtocolCodec) {
        codec.writeBoolean(isAborted)
        Timber.tag(TAG).i("PROTOCOL [0B] → CheckAbort: $isAborted")
    }

    private fun handleClientPong() {
        Timber.tag(TAG).i("PROTOCOL [0C] ← ClientPong")
        // No response needed
    }

    private fun handleServerAvailable(command: Command.ServerAvailable): HandleResult {
        Timber.tag(TAG).i("PROTOCOL [0D] ← ServerAvailable: control port ${command.controlPort}")
        // Desktop expects beacon to be sent to its control port (NOT back on this connection!)
        // Return the port so the server can send the beacon on a new connection
        return HandleResult.SendBeaconTo(command.controlPort)
    }
}

/**
 * Input stream that reads exactly a limited number of bytes from a ProtocolCodec.
 */
private class LimitedInputStream(
    private val codec: ProtocolCodec,
    private val limit: Long,
) : java.io.InputStream() {
    private var bytesRead: Long = 0

    override fun read(): Int {
        if (bytesRead >= limit) {
            return -1
        }
        val byte = codec.readByte()
        bytesRead++
        return byte
    }

    override fun read(
        b: ByteArray,
        off: Int,
        len: Int,
    ): Int {
        if (bytesRead >= limit) {
            return -1
        }
        val remaining = (limit - bytesRead).coerceAtMost(len.toLong()).toInt()
        val read = codec.readInputStream().read(b, off, remaining)
        if (read != -1) {
            bytesRead += read
        }
        return read
    }

    override fun available(): Int = (limit - bytesRead).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
}
