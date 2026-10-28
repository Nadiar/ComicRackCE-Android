package com.comicrack.reader.cache

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Tests for TempFileManager resource management.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TempFileManagerTest {
    private lateinit var context: Context
    private lateinit var tempFileManager: TempFileManager
    private val testFiles = mutableListOf<File>()

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        tempFileManager = TempFileManager(context)
    }

    @After
    fun tearDown() {
        // Clean up test files
        testFiles.forEach { file ->
            if (file.exists()) {
                file.delete()
            }
        }
        testFiles.clear()
    }

    @Test
    fun `TempFileManager initializes with context`() {
        val manager = TempFileManager(context)
        // Should not throw exception
        assertNotNull(manager)
    }

    @Test
    fun `registerTempFile tracks file path`() {
        val testFile = File(context.cacheDir, "test_temp.cbz")
        testFile.createNewFile()
        testFiles.add(testFile)

        tempFileManager.registerTempFile(testFile.absolutePath)

        // Verify file was registered by cleaning up
        assertTrue(testFile.exists())
        tempFileManager.cleanup()
        assertFalse(testFile.exists())
    }

    @Test
    fun `cleanup deletes registered files`() {
        val testFile1 = File(context.cacheDir, "temp1.cbz")
        val testFile2 = File(context.cacheDir, "temp2.cbz")
        testFile1.createNewFile()
        testFile2.createNewFile()
        testFiles.add(testFile1)
        testFiles.add(testFile2)

        tempFileManager.registerTempFile(testFile1.absolutePath)
        tempFileManager.registerTempFile(testFile2.absolutePath)

        assertTrue(testFile1.exists())
        assertTrue(testFile2.exists())

        tempFileManager.cleanup()

        assertFalse(testFile1.exists())
        assertFalse(testFile2.exists())
    }

    @Test
    fun `cleanup handles non-existent files gracefully`() {
        val nonExistentPath = "/tmp/does_not_exist.cbz"

        tempFileManager.registerTempFile(nonExistentPath)

        // Should not throw exception
        tempFileManager.cleanup()
    }

    @Test
    fun `cleanup handles already deleted files`() {
        val testFile = File(context.cacheDir, "will_be_deleted.cbz")
        testFile.createNewFile()
        testFiles.add(testFile)

        tempFileManager.registerTempFile(testFile.absolutePath)

        // Delete file manually before cleanup
        testFile.delete()
        assertFalse(testFile.exists())

        // Should not throw exception
        tempFileManager.cleanup()
    }

    @Test
    fun `cleanup can be called multiple times safely`() {
        val testFile = File(context.cacheDir, "multi_cleanup.cbz")
        testFile.createNewFile()
        testFiles.add(testFile)

        tempFileManager.registerTempFile(testFile.absolutePath)

        tempFileManager.cleanup()
        assertFalse(testFile.exists())

        // Second cleanup should not throw
        tempFileManager.cleanup()
    }

    @Test
    fun `registerTempFile handles same path multiple times`() {
        val testFile = File(context.cacheDir, "duplicate.cbz")
        testFile.createNewFile()
        testFiles.add(testFile)

        tempFileManager.registerTempFile(testFile.absolutePath)
        tempFileManager.registerTempFile(testFile.absolutePath)

        tempFileManager.cleanup()
        assertFalse(testFile.exists())
    }

    @Test
    fun `cleanup only deletes registered files not all cache`() {
        val managedFile = File(context.cacheDir, "managed.cbz")
        val unmanagedFile = File(context.cacheDir, "unmanaged.cbz")

        managedFile.createNewFile()
        unmanagedFile.createNewFile()
        testFiles.add(managedFile)
        testFiles.add(unmanagedFile)

        tempFileManager.registerTempFile(managedFile.absolutePath)

        tempFileManager.cleanup()

        assertFalse(managedFile.exists())
        assertTrue(unmanagedFile.exists()) // Should still exist
    }

    private fun assertNotNull(value: Any?) {
        org.junit.Assert.assertNotNull(value)
    }
}
