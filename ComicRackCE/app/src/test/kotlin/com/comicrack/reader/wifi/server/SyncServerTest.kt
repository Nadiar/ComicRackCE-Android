package com.comicrack.wifi.server

import com.comicrack.wifi.protocol.Command
import com.comicrack.wifi.protocol.ProtocolCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Tests for SyncServer - verifies TCP server connection handling.
 *
 * Note: Actual socket server operations require Android instrumentation tests.
 * These unit tests focus on session management and state handling.
 */
class SyncServerTest {
    // ==========================================================================
    // Server state tests
    // ==========================================================================

    @Test
    fun `SyncServerState idle is initial state`() {
        val state = SyncServerState.Idle
        assertFalse(state.isRunning)
        assertFalse(state.hasActiveSession)
    }

    @Test
    fun `SyncServerState listening indicates server is running`() {
        val state = SyncServerState.Listening(port = 7614)
        assertTrue(state.isRunning)
        assertFalse(state.hasActiveSession)
        assertEquals(7614, state.port)
    }

    @Test
    fun `SyncServerState syncing indicates active session`() {
        val state =
            SyncServerState.Syncing(
                clientAddress = "192.168.1.100",
                progress = 50,
            )
        assertTrue(state.isRunning)
        assertTrue(state.hasActiveSession)
        assertEquals("192.168.1.100", state.clientAddress)
        assertEquals(50, state.progress)
    }

    @Test
    fun `SyncServerState error contains message`() {
        val state = SyncServerState.Error("Connection refused")
        assertFalse(state.isRunning)
        assertFalse(state.hasActiveSession)
        assertEquals("Connection refused", state.message)
    }

    // ==========================================================================
    // Session config tests
    // ==========================================================================

    @Test
    fun `SyncSessionConfig has correct defaults`() {
        val config = SyncSessionConfig()
        assertEquals(7614, config.port)
        assertEquals(30_000, config.socketTimeout)
        assertEquals(64 * 1024, config.bufferSize)
    }

    @Test
    fun `SyncSessionConfig accepts custom values`() {
        val config =
            SyncSessionConfig(
                port = 8080,
                socketTimeout = 60_000,
                bufferSize = 128 * 1024,
            )
        assertEquals(8080, config.port)
        assertEquals(60_000, config.socketTimeout)
        assertEquals(128 * 1024, config.bufferSize)
    }

    // ==========================================================================
    // Command parsing integration tests
    // ==========================================================================

    @Test
    fun `parseCommand reads ListFiles correctly`() {
        val input = byteArrayOf(0x00) // OPCODE_LIST_FILES
        val codec = ProtocolCodec(ByteArrayInputStream(input), ByteArrayOutputStream())
        val command = Command.parseCommand(codec)
        assertTrue(command is Command.ListFiles)
    }

    @Test
    fun `parseCommand reads Start correctly`() {
        val output = ByteArrayOutputStream()
        val writeCodec = ProtocolCodec(ByteArrayInputStream(byteArrayOf()), output)
        writeCodec.writeByte(Command.OPCODE_START)
        writeCodec.writeString("Sync starting")

        val readCodec = ProtocolCodec(ByteArrayInputStream(output.toByteArray()), ByteArrayOutputStream())
        val command = Command.parseCommand(readCodec)

        assertTrue(command is Command.Start)
        assertEquals("Sync starting", (command as Command.Start).message)
    }

    @Test
    fun `parseCommand reads WriteFile correctly`() {
        val output = ByteArrayOutputStream()
        val writeCodec = ProtocolCodec(ByteArrayInputStream(byteArrayOf()), output)
        writeCodec.writeByte(Command.OPCODE_WRITE_FILE)
        writeCodec.writeString("test.cbp")
        writeCodec.writeLong(1024)

        val readCodec = ProtocolCodec(ByteArrayInputStream(output.toByteArray()), ByteArrayOutputStream())
        val command = Command.parseCommand(readCodec)

        assertTrue(command is Command.WriteFile)
        val writeFile = command as Command.WriteFile
        assertEquals("test.cbp", writeFile.filename)
        assertEquals(1024L, writeFile.size)
    }

    @Test
    fun `parseCommand reads ProgressUpdate correctly`() {
        val input =
            byteArrayOf(
                Command.OPCODE_PROGRESS_UPDATE.toByte(),
                75, // 75% progress
            )
        val codec = ProtocolCodec(ByteArrayInputStream(input), ByteArrayOutputStream())
        val command = Command.parseCommand(codec)

        assertTrue(command is Command.ProgressUpdate)
        assertEquals(75, (command as Command.ProgressUpdate).percent)
    }

    // ==========================================================================
    // Connection result tests
    // ==========================================================================

    @Test
    fun `SyncResult success indicates completed sync`() {
        val result =
            SyncResult.Success(
                filesReceived = 10,
                bytesSynced = 1024 * 1024,
            )
        assertTrue(result.isSuccess)
        assertEquals(10, result.filesReceived)
        assertEquals(1024 * 1024, result.bytesSynced)
    }

    @Test
    fun `SyncResult failure contains error`() {
        val result = SyncResult.Failure("Connection lost")
        assertFalse(result.isSuccess)
        assertEquals("Connection lost", result.error)
    }

    @Test
    fun `SyncResult aborted indicates user abort`() {
        val result = SyncResult.Aborted
        assertFalse(result.isSuccess)
    }
}
