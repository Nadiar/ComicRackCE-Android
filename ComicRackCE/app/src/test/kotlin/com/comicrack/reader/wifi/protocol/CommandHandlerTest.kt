package com.comicrack.wifi.protocol

import com.comicrack.wifi.SyncIdentity
import com.comicrack.wifi.file.FileManager
import com.comicrack.wifi.progress.SyncProgressTracker
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Tests for CommandHandler - verifies correct response generation
 * for all 14 protocol commands.
 */
class CommandHandlerTest {
    private lateinit var fileManager: FileManager
    private lateinit var progressTracker: SyncProgressTracker
    private lateinit var handler: CommandHandler
    private var completedCalled = false

    @Before
    fun setUp() {
        fileManager = mock()
        progressTracker = mock()
        completedCalled = false
        handler =
            CommandHandler(
                fileManager = fileManager,
                progressTracker = progressTracker,
                repository = mock(),
                deviceKey = "test-device-key",
                deviceName = "test-device",
                onCompleted = { completedCalled = true },
            )
    }

    // ==========================================================================
    // ListFiles command tests
    // ==========================================================================

    @Test
    fun `handleListFiles returns file list as newline-separated string`() =
        runTest {
            val files = listOf("comic1.cbp", "comic2.cbp", "comic3.cbp")
            whenever(fileManager.listFiles()).thenReturn(files)

            val output = ByteArrayOutputStream()
            val codec = ProtocolCodec(ByteArrayInputStream(byteArrayOf()), output)

            handler.handle(Command.ListFiles, codec)

            // Verify output contains length-prefixed string
            val expectedString = files.joinToString("\n")
            val resultCodec = ProtocolCodec(ByteArrayInputStream(output.toByteArray()), ByteArrayOutputStream())
            val result = resultCodec.readString()
            assertEquals(expectedString, result)
        }

    @Test
    fun `handleListFiles returns empty string when no files`() =
        runTest {
            whenever(fileManager.listFiles()).thenReturn(emptyList())

            val output = ByteArrayOutputStream()
            val codec = ProtocolCodec(ByteArrayInputStream(byteArrayOf()), output)

            handler.handle(Command.ListFiles, codec)

            val resultCodec = ProtocolCodec(ByteArrayInputStream(output.toByteArray()), ByteArrayOutputStream())
            val result = resultCodec.readString()
            assertEquals("", result)
        }

    // ==========================================================================
    // ReadFile command tests
    // ==========================================================================

    @Test
    fun `handleReadFile returns file size and data`() =
        runTest {
            val filename = "test.cbp"
            val fileData = "file content here".toByteArray()
            whenever(fileManager.readFile(filename)).thenReturn(fileData)

            val output = ByteArrayOutputStream()
            val codec = ProtocolCodec(ByteArrayInputStream(byteArrayOf()), output)

            handler.handle(Command.ReadFile(filename), codec)

            val resultCodec = ProtocolCodec(ByteArrayInputStream(output.toByteArray()), ByteArrayOutputStream())
            val size = resultCodec.readLong()
            val data = resultCodec.readBytes(size.toInt())

            assertEquals(fileData.size.toLong(), size)
            assertArrayEquals(fileData, data)
        }

    @Test
    fun `handleReadFile returns zero size for missing file`() =
        runTest {
            val filename = "missing.cbp"
            whenever(fileManager.readFile(filename)).thenReturn(null)

            val output = ByteArrayOutputStream()
            val codec = ProtocolCodec(ByteArrayInputStream(byteArrayOf()), output)

            handler.handle(Command.ReadFile(filename), codec)

            val resultCodec = ProtocolCodec(ByteArrayInputStream(output.toByteArray()), ByteArrayOutputStream())
            val size = resultCodec.readLong()
            assertEquals(0L, size)
        }

    // ==========================================================================
    // FreeSpace command tests
    // ==========================================================================

    @Test
    fun `handleFreeSpace returns available space in bytes`() =
        runTest {
            val freeSpace = 1024L * 1024L * 1024L // 1 GB
            whenever(fileManager.getFreeSpace()).thenReturn(freeSpace)

            val output = ByteArrayOutputStream()
            val codec = ProtocolCodec(ByteArrayInputStream(byteArrayOf()), output)

            handler.handle(Command.FreeSpace, codec)

            val resultCodec = ProtocolCodec(ByteArrayInputStream(output.toByteArray()), ByteArrayOutputStream())
            val result = resultCodec.readLong()
            assertEquals(freeSpace, result)
        }

    // ==========================================================================
    // FileExists command tests
    // ==========================================================================

    @Test
    fun `handleFileExists returns true when file exists`() =
        runTest {
            val filename = "exists.cbp"
            whenever(fileManager.fileExists(filename)).thenReturn(true)

            val output = ByteArrayOutputStream()
            val codec = ProtocolCodec(ByteArrayInputStream(byteArrayOf()), output)

            handler.handle(Command.FileExists(filename), codec)

            val resultCodec = ProtocolCodec(ByteArrayInputStream(output.toByteArray()), ByteArrayOutputStream())
            val result = resultCodec.readBoolean()
            assertTrue(result)
        }

    @Test
    fun `handleFileExists returns false when file does not exist`() =
        runTest {
            val filename = "missing.cbp"
            whenever(fileManager.fileExists(filename)).thenReturn(false)

            val output = ByteArrayOutputStream()
            val codec = ProtocolCodec(ByteArrayInputStream(byteArrayOf()), output)

            handler.handle(Command.FileExists(filename), codec)

            val resultCodec = ProtocolCodec(ByteArrayInputStream(output.toByteArray()), ByteArrayOutputStream())
            val result = resultCodec.readBoolean()
            assertEquals(false, result)
        }

    // ==========================================================================
    // DeleteFile command tests
    // ==========================================================================

    @Test
    fun `handleDeleteFile returns success status`() =
        runTest {
            val filename = "delete.cbp"
            whenever(fileManager.deleteFile(filename)).thenReturn(true)

            val output = ByteArrayOutputStream()
            val codec = ProtocolCodec(ByteArrayInputStream(byteArrayOf()), output)

            handler.handle(Command.DeleteFile(filename), codec)

            val resultCodec = ProtocolCodec(ByteArrayInputStream(output.toByteArray()), ByteArrayOutputStream())
            val result = resultCodec.readBoolean()
            assertTrue(result)
        }

    // ==========================================================================
    // WriteFile command tests
    // ==========================================================================

    @Test
    fun `handleWriteFile writes file and returns success`() =
        runTest {
            val filename = "new.cbp"
            val fileSize = 100L
            val fileData = ByteArray(100) { it.toByte() }
            whenever(fileManager.writeFile(any(), any(), any())).thenReturn(true)

            // Input contains the file data that would be read
            val input = ByteArrayInputStream(fileData)
            val output = ByteArrayOutputStream()
            val codec = ProtocolCodec(input, output)

            handler.handle(Command.WriteFile(filename, fileSize), codec)

            verify(fileManager).writeFile(any(), any(), any())

            val resultCodec = ProtocolCodec(ByteArrayInputStream(output.toByteArray()), ByteArrayOutputStream())
            val result = resultCodec.readBoolean()
            assertTrue(result)
        }

    // ==========================================================================
    // Start command tests
    // ==========================================================================

    @Test
    fun `handleStart logs message without response`() =
        runTest {
            val output = ByteArrayOutputStream()
            val codec = ProtocolCodec(ByteArrayInputStream(byteArrayOf()), output)

            handler.handle(Command.Start("Sync starting"), codec)

            // Start command has no response
            assertEquals(0, output.size())
        }

    // ==========================================================================
    // Completed command tests
    // ==========================================================================

    @Test
    fun `handleCompleted triggers callback`() =
        runTest {
            val output = ByteArrayOutputStream()
            val codec = ProtocolCodec(ByteArrayInputStream(byteArrayOf()), output)

            handler.handle(Command.Completed("Sync done"), codec)

            assertTrue(completedCalled)
        }

    // ==========================================================================
    // ProgressUpdate command tests
    // ==========================================================================

    @Test
    fun `handleProgressUpdate updates tracker`() =
        runTest {
            val output = ByteArrayOutputStream()
            val codec = ProtocolCodec(ByteArrayInputStream(byteArrayOf()), output)

            handler.handle(Command.ProgressUpdate(75), codec)

            verify(progressTracker).updateProgress(75)
        }

    // ==========================================================================
    // Info command tests
    // ==========================================================================

    @Test
    fun `handleInfo reads desktop version and replies with device identity`() =
        runTest {
            // Desktop sends its sync version (int)
            val desktopRequest = ByteArrayOutputStream()
            ProtocolCodec(ByteArrayInputStream(byteArrayOf()), desktopRequest).writeInt(100)

            val input = ByteArrayInputStream(desktopRequest.toByteArray())
            val output = ByteArrayOutputStream()
            val codec = ProtocolCodec(input, output)

            handler.handle(Command.Info, codec)

            // Device replies: licensed (bool), version (int), key (string), flag (bool)
            val resultCodec = ProtocolCodec(ByteArrayInputStream(output.toByteArray()), ByteArrayOutputStream())
            assertTrue(resultCodec.readBoolean())
            assertEquals(SyncIdentity.SYNC_VERSION, resultCodec.readInt())
            assertEquals("test-device-key", resultCodec.readString())
            assertFalse(resultCodec.readBoolean())
        }

    // ==========================================================================
    // ReadMultiFile command tests
    // ==========================================================================

    @Test
    fun `handleReadMultiFile returns multiple files`() =
        runTest {
            val filenames = listOf("file1.cbp", "file2.cbp")
            val file1Data = "content1".toByteArray()
            val file2Data = "content2".toByteArray()
            whenever(fileManager.readFile("file1.cbp")).thenReturn(file1Data)
            whenever(fileManager.readFile("file2.cbp")).thenReturn(file2Data)

            val output = ByteArrayOutputStream()
            val codec = ProtocolCodec(ByteArrayInputStream(byteArrayOf()), output)

            handler.handle(Command.ReadMultiFile(filenames), codec)

            val resultCodec = ProtocolCodec(ByteArrayInputStream(output.toByteArray()), ByteArrayOutputStream())

            // First file
            val size1 = resultCodec.readLong()
            val data1 = resultCodec.readBytes(size1.toInt())
            assertEquals(file1Data.size.toLong(), size1)
            assertArrayEquals(file1Data, data1)

            // Second file
            val size2 = resultCodec.readLong()
            val data2 = resultCodec.readBytes(size2.toInt())
            assertEquals(file2Data.size.toLong(), size2)
            assertArrayEquals(file2Data, data2)
        }

    // ==========================================================================
    // CheckAbort command tests
    // ==========================================================================

    @Test
    fun `handleCheckAbort returns abort status`() =
        runTest {
            val output = ByteArrayOutputStream()
            val codec = ProtocolCodec(ByteArrayInputStream(byteArrayOf()), output)

            handler.handle(Command.CheckAbort, codec)

            val resultCodec = ProtocolCodec(ByteArrayInputStream(output.toByteArray()), ByteArrayOutputStream())
            val isAborted = resultCodec.readBoolean()
            assertEquals(false, isAborted) // Default is not aborted
        }

    @Test
    fun `handleCheckAbort returns true after abort requested`() =
        runTest {
            handler.requestAbort()

            val output = ByteArrayOutputStream()
            val codec = ProtocolCodec(ByteArrayInputStream(byteArrayOf()), output)

            handler.handle(Command.CheckAbort, codec)

            val resultCodec = ProtocolCodec(ByteArrayInputStream(output.toByteArray()), ByteArrayOutputStream())
            val isAborted = resultCodec.readBoolean()
            assertTrue(isAborted)
        }

    // ==========================================================================
    // ClientPong command tests
    // ==========================================================================

    @Test
    fun `handleClientPong has no response`() =
        runTest {
            val output = ByteArrayOutputStream()
            val codec = ProtocolCodec(ByteArrayInputStream(byteArrayOf()), output)

            handler.handle(Command.ClientPong, codec)

            assertEquals(0, output.size())
        }

    // ==========================================================================
    // ServerAvailable command tests
    // ==========================================================================

    @Test
    fun `handleServerAvailable has no response`() =
        runTest {
            val output = ByteArrayOutputStream()
            val codec = ProtocolCodec(ByteArrayInputStream(byteArrayOf()), output)

            handler.handle(Command.ServerAvailable(7614), codec)

            assertEquals(0, output.size())
        }
}
