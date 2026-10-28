package com.comicrack.wifi.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Tests for Command sealed class - verifies all 14 protocol commands
 * are correctly defined and can be parsed from the wire protocol.
 */
class CommandTest {
    // ==========================================================================
    // Opcode constant tests
    // ==========================================================================

    @Test
    fun `opcode constants match protocol specification`() {
        assertEquals(0, Command.OPCODE_LIST_FILES)
        assertEquals(1, Command.OPCODE_READ_FILE)
        assertEquals(2, Command.OPCODE_FREE_SPACE)
        assertEquals(3, Command.OPCODE_FILE_EXISTS)
        assertEquals(4, Command.OPCODE_DELETE_FILE)
        assertEquals(5, Command.OPCODE_WRITE_FILE)
        assertEquals(6, Command.OPCODE_START)
        assertEquals(7, Command.OPCODE_COMPLETED)
        assertEquals(8, Command.OPCODE_PROGRESS_UPDATE)
        assertEquals(9, Command.OPCODE_INFO)
        assertEquals(10, Command.OPCODE_READ_MULTI_FILE)
        assertEquals(11, Command.OPCODE_CHECK_ABORT)
        assertEquals(12, Command.OPCODE_CLIENT_PONG)
        assertEquals(13, Command.OPCODE_SERVER_AVAILABLE)
    }

    // ==========================================================================
    // Command parsing tests - each opcode maps to correct command type
    // ==========================================================================

    @Test
    fun `parseCommand reads ListFiles command (opcode 0)`() {
        val input = createInputStream(byteArrayOf(0x00))
        val codec = ProtocolCodec(input, ByteArrayOutputStream())

        val command = Command.parseCommand(codec)

        assertTrue(command is Command.ListFiles)
    }

    @Test
    fun `parseCommand reads ReadFile command with filename (opcode 1)`() {
        // Opcode 1 + length-prefixed string "test.cbp"
        val filename = "test.cbp"
        val filenameBytes = filename.toByteArray(Charsets.UTF_8)
        val data = byteArrayOf(0x01) + intToBytes(filenameBytes.size) + filenameBytes
        val input = createInputStream(data)
        val codec = ProtocolCodec(input, ByteArrayOutputStream())

        val command = Command.parseCommand(codec)

        assertTrue(command is Command.ReadFile)
        assertEquals(filename, (command as Command.ReadFile).filename)
    }

    @Test
    fun `parseCommand reads FreeSpace command (opcode 2)`() {
        val input = createInputStream(byteArrayOf(0x02))
        val codec = ProtocolCodec(input, ByteArrayOutputStream())

        val command = Command.parseCommand(codec)

        assertTrue(command is Command.FreeSpace)
    }

    @Test
    fun `parseCommand reads FileExists command with filename (opcode 3)`() {
        val filename = "comic.cbp"
        val filenameBytes = filename.toByteArray(Charsets.UTF_8)
        val data = byteArrayOf(0x03) + intToBytes(filenameBytes.size) + filenameBytes
        val input = createInputStream(data)
        val codec = ProtocolCodec(input, ByteArrayOutputStream())

        val command = Command.parseCommand(codec)

        assertTrue(command is Command.FileExists)
        assertEquals(filename, (command as Command.FileExists).filename)
    }

    @Test
    fun `parseCommand reads DeleteFile command with filename (opcode 4)`() {
        val filename = "old.cbp"
        val filenameBytes = filename.toByteArray(Charsets.UTF_8)
        val data = byteArrayOf(0x04) + intToBytes(filenameBytes.size) + filenameBytes
        val input = createInputStream(data)
        val codec = ProtocolCodec(input, ByteArrayOutputStream())

        val command = Command.parseCommand(codec)

        assertTrue(command is Command.DeleteFile)
        assertEquals(filename, (command as Command.DeleteFile).filename)
    }

    @Test
    fun `parseCommand reads WriteFile command with filename and size (opcode 5)`() {
        val filename = "new.cbp"
        val fileSize = 1024L * 1024L // 1 MB
        val filenameBytes = filename.toByteArray(Charsets.UTF_8)
        val data = byteArrayOf(0x05) + intToBytes(filenameBytes.size) + filenameBytes + longToBytes(fileSize)
        val input = createInputStream(data)
        val codec = ProtocolCodec(input, ByteArrayOutputStream())

        val command = Command.parseCommand(codec)

        assertTrue(command is Command.WriteFile)
        val writeFile = command as Command.WriteFile
        assertEquals(filename, writeFile.filename)
        assertEquals(fileSize, writeFile.size)
    }

    @Test
    fun `parseCommand reads Start command with message (opcode 6)`() {
        val message = "Starting sync"
        val messageBytes = message.toByteArray(Charsets.UTF_8)
        val data = byteArrayOf(0x06) + intToBytes(messageBytes.size) + messageBytes
        val input = createInputStream(data)
        val codec = ProtocolCodec(input, ByteArrayOutputStream())

        val command = Command.parseCommand(codec)

        assertTrue(command is Command.Start)
        assertEquals(message, (command as Command.Start).message)
    }

    @Test
    fun `parseCommand reads Completed command with message (opcode 7)`() {
        val message = "Sync completed"
        val messageBytes = message.toByteArray(Charsets.UTF_8)
        val data = byteArrayOf(0x07) + intToBytes(messageBytes.size) + messageBytes
        val input = createInputStream(data)
        val codec = ProtocolCodec(input, ByteArrayOutputStream())

        val command = Command.parseCommand(codec)

        assertTrue(command is Command.Completed)
        assertEquals(message, (command as Command.Completed).message)
    }

    @Test
    fun `parseCommand reads ProgressUpdate command with percent (opcode 8)`() {
        val percent = 75
        val data = byteArrayOf(0x08, percent.toByte())
        val input = createInputStream(data)
        val codec = ProtocolCodec(input, ByteArrayOutputStream())

        val command = Command.parseCommand(codec)

        assertTrue(command is Command.ProgressUpdate)
        assertEquals(percent, (command as Command.ProgressUpdate).percent)
    }

    @Test
    fun `parseCommand reads Info command (opcode 9)`() {
        val input = createInputStream(byteArrayOf(0x09))
        val codec = ProtocolCodec(input, ByteArrayOutputStream())

        val command = Command.parseCommand(codec)

        assertTrue(command is Command.Info)
    }

    @Test
    fun `parseCommand reads ReadMultiFile command with file list (opcode 10)`() {
        val filenames = listOf("file1.cbp", "file2.cbp", "file3.cbp")
        val output = ByteArrayOutputStream()
        output.write(0x0A) // opcode 10
        // Write count as 4-byte big-endian int
        output.write(intToBytes(filenames.size))
        // Write each filename as length-prefixed string
        for (filename in filenames) {
            val bytes = filename.toByteArray(Charsets.UTF_8)
            output.write(intToBytes(bytes.size))
            output.write(bytes)
        }
        val input = createInputStream(output.toByteArray())
        val codec = ProtocolCodec(input, ByteArrayOutputStream())

        val command = Command.parseCommand(codec)

        assertTrue(command is Command.ReadMultiFile)
        val readMultiFile = command as Command.ReadMultiFile
        assertEquals(3, readMultiFile.filenames.size)
        assertEquals(filenames, readMultiFile.filenames)
    }

    @Test
    fun `parseCommand reads CheckAbort command (opcode 11)`() {
        val input = createInputStream(byteArrayOf(0x0B))
        val codec = ProtocolCodec(input, ByteArrayOutputStream())

        val command = Command.parseCommand(codec)

        assertTrue(command is Command.CheckAbort)
    }

    @Test
    fun `parseCommand reads ClientPong command (opcode 12)`() {
        val input = createInputStream(byteArrayOf(0x0C))
        val codec = ProtocolCodec(input, ByteArrayOutputStream())

        val command = Command.parseCommand(codec)

        assertTrue(command is Command.ClientPong)
    }

    @Test
    fun `parseCommand reads ServerAvailable command with port (opcode 13)`() {
        val controlPort = 7614
        val data = byteArrayOf(0x0D) + intToBytes(controlPort)
        val input = createInputStream(data)
        val codec = ProtocolCodec(input, ByteArrayOutputStream())

        val command = Command.parseCommand(codec)

        assertTrue(command is Command.ServerAvailable)
        assertEquals(controlPort, (command as Command.ServerAvailable).controlPort)
    }

    @Test(expected = ProtocolException::class)
    fun `parseCommand throws exception for unknown opcode`() {
        val input = createInputStream(byteArrayOf(0xFF.toByte()))
        val codec = ProtocolCodec(input, ByteArrayOutputStream())

        Command.parseCommand(codec)
    }

    // ==========================================================================
    // Command opcode property tests
    // ==========================================================================

    @Test
    fun `each command type has correct opcode property`() {
        assertEquals(0, Command.ListFiles.opcode)
        assertEquals(1, Command.ReadFile("test").opcode)
        assertEquals(2, Command.FreeSpace.opcode)
        assertEquals(3, Command.FileExists("test").opcode)
        assertEquals(4, Command.DeleteFile("test").opcode)
        assertEquals(5, Command.WriteFile("test", 100).opcode)
        assertEquals(6, Command.Start("msg").opcode)
        assertEquals(7, Command.Completed("msg").opcode)
        assertEquals(8, Command.ProgressUpdate(50).opcode)
        assertEquals(9, Command.Info.opcode)
        assertEquals(10, Command.ReadMultiFile(listOf("a", "b")).opcode)
        assertEquals(11, Command.CheckAbort.opcode)
        assertEquals(12, Command.ClientPong.opcode)
        assertEquals(13, Command.ServerAvailable(7614).opcode)
    }

    // ==========================================================================
    // Helper functions
    // ==========================================================================

    private fun createInputStream(data: ByteArray): ByteArrayInputStream = ByteArrayInputStream(data)

    private fun intToBytes(value: Int): ByteArray =
        byteArrayOf(
            (value shr 24).toByte(),
            (value shr 16).toByte(),
            (value shr 8).toByte(),
            value.toByte(),
        )

    private fun longToBytes(value: Long): ByteArray =
        byteArrayOf(
            (value shr 56).toByte(),
            (value shr 48).toByte(),
            (value shr 40).toByte(),
            (value shr 32).toByte(),
            (value shr 24).toByte(),
            (value shr 16).toByte(),
            (value shr 8).toByte(),
            value.toByte(),
        )
}
