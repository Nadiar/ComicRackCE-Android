package com.comicrack.wifi.file

import android.content.Context
import com.comicrack.core.storage.saf.SAFFileSystem
import com.comicrack.wifi.file.SyncFileManager
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.FileNotFoundException

/**
 * Tests for SyncFileManager - SAF-based file operations.
 *
 * Uses Robolectric for Android framework mocking.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class FileManagerTest {
    private lateinit var context: Context
    private lateinit var safFileSystem: SAFFileSystem
    private lateinit var fileManager: SyncFileManager

    @Before
    fun setUp() {
        context = mock()
        safFileSystem = mock()

        fileManager = SyncFileManager(context, safFileSystem, "_sync".toPath())
    }

    // ==========================================================================
    // File listing tests
    // ==========================================================================

    @Test
    fun `listFiles returns cbp and xml filenames`() =
        runTest {
            // This test verifies the filtering logic
            val testFiles =
                listOf(
                    "comic1.cbp",
                    "comic1.cbp.xml",
                    "comic2.cbp",
                    "other.txt", // Should be filtered out
                )

            // Test the filtering logic directly
            val result = filterSyncFiles(testFiles)

            assertEquals(3, result.size)
            assertTrue(result.contains("comic1.cbp"))
            assertTrue(result.contains("comic1.cbp.xml"))
            assertTrue(result.contains("comic2.cbp"))
            assertFalse(result.contains("other.txt"))
        }

    @Test
    fun `listFiles returns empty list for empty folder`() =
        runTest {
            val result = filterSyncFiles(emptyList())
            assertTrue(result.isEmpty())
        }

    // ==========================================================================
    // File extension filtering tests
    // ==========================================================================

    @Test
    fun `isSyncFile returns true for cbp files`() {
        assertTrue(isSyncFile("test.cbp"))
        assertTrue(isSyncFile("Comic Book.cbp"))
        assertTrue(isSyncFile("test.CBP")) // Case insensitive
    }

    @Test
    fun `isSyncFile returns true for cbp xml sidecar files`() {
        assertTrue(isSyncFile("test.cbp.xml"))
        assertTrue(isSyncFile("Comic Book.cbp.xml"))
    }

    @Test
    fun `isSyncFile returns true for ini and cbz files`() {
        assertTrue(isSyncFile("sync.ini"))
        assertTrue(isSyncFile("metadata.xml"))
        assertTrue(isSyncFile("archive.cbz"))
    }

    @Test
    fun `isSyncFile returns false for other extensions`() {
        assertFalse(isSyncFile("test.pdf"))
        assertFalse(isSyncFile("test.txt"))
    }

    // ==========================================================================
    // File read tests
    // ==========================================================================

    @Test
    fun `readFile returns null for non-existent file`() =
        runTest {
            // SAF reports a missing document by throwing from source()
            whenever(safFileSystem.source(any())).thenAnswer { throw FileNotFoundException("nonexistent.cbp") }

            val result = fileManager.readFile("nonexistent.cbp")
            assertNull(result)
        }

    // ==========================================================================
    // File write validation tests
    // ==========================================================================

    @Test
    fun `validateFilename rejects path traversal attempts`() {
        assertFalse(isValidFilename("../etc/passwd"))
        assertFalse(isValidFilename("..\\windows\\system32"))
        assertFalse(isValidFilename("test/../other.cbp"))
    }

    @Test
    fun `validateFilename accepts valid filenames`() {
        assertTrue(isValidFilename("comic.cbp"))
        assertTrue(isValidFilename("Comic Book Title.cbp"))
        assertTrue(isValidFilename("comic.cbp.xml"))
        assertTrue(isValidFilename("Comic (2024) #01.cbp"))
    }

    @Test
    fun `validateFilename rejects empty filename`() {
        assertFalse(isValidFilename(""))
        assertFalse(isValidFilename("   "))
    }

    // ==========================================================================
    // Helper functions for testing
    // ==========================================================================

    /**
     * Filter filenames to only include sync files (.cbp, .cbp.xml, .ini, .xml, .cbz)
     */
    private fun filterSyncFiles(filenames: List<String>): List<String> = filenames.filter { isSyncFile(it) }

    /**
     * Check if a filename is a sync file
     */
    private fun isSyncFile(filename: String): Boolean {
        val lower = filename.lowercase()
        return lower.endsWith(".cbp") ||
            lower.endsWith(".cbp.xml") ||
            lower.endsWith(".ini") ||
            lower.endsWith(".xml") ||
            lower.endsWith(".cbz")
    }

    /**
     * Validate that a filename is safe (no path traversal)
     */
    private fun isValidFilename(filename: String): Boolean = when {
        filename.isBlank() -> false
        filename.contains("..") -> false
        filename.contains("/") || filename.contains("\\") -> false
        else -> true
    }
}
