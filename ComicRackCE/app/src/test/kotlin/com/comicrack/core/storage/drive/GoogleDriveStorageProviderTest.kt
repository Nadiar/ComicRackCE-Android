package com.comicrack.core.storage.drive

import com.comicrack.core.storage.provider.StorageType
import com.google.api.services.drive.model.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Tests for [GoogleDriveStorageProvider].
 */
class GoogleDriveStorageProviderTest {

    private lateinit var driveApiClient: DriveApiClient
    private lateinit var provider: GoogleDriveStorageProvider

    @Before
    fun setup() {
        driveApiClient = mock()
        provider = GoogleDriveStorageProvider(driveApiClient, "user@example.com")
    }

    // ==========================================================================
    // Type
    // ==========================================================================

    @Test
    fun `type is GOOGLE_DRIVE`() {
        assertEquals(StorageType.GOOGLE_DRIVE, provider.type)
    }

    // ==========================================================================
    // Comic format detection
    // ==========================================================================

    @Test
    fun `isSupportedComicFormat returns true for cbz extension`() {
        assertTrue(provider.isSupportedComicFormat(null, "comic.cbz"))
    }

    @Test
    fun `isSupportedComicFormat returns true for cbr extension`() {
        assertTrue(provider.isSupportedComicFormat(null, "comic.cbr"))
    }

    @Test
    fun `isSupportedComicFormat returns true for cb7 extension`() {
        assertTrue(provider.isSupportedComicFormat(null, "archive.cb7"))
    }

    @Test
    fun `isSupportedComicFormat returns true for zip extension`() {
        assertTrue(provider.isSupportedComicFormat(null, "file.zip"))
    }

    @Test
    fun `isSupportedComicFormat returns true for rar extension`() {
        assertTrue(provider.isSupportedComicFormat(null, "file.rar"))
    }

    @Test
    fun `isSupportedComicFormat returns true for 7z extension`() {
        assertTrue(provider.isSupportedComicFormat(null, "file.7z"))
    }

    @Test
    fun `isSupportedComicFormat returns true for tar extension`() {
        assertTrue(provider.isSupportedComicFormat(null, "file.tar"))
    }

    @Test
    fun `isSupportedComicFormat returns true for cbt extension`() {
        assertTrue(provider.isSupportedComicFormat(null, "file.cbt"))
    }

    @Test
    fun `isSupportedComicFormat returns true for cbp extension`() {
        assertTrue(provider.isSupportedComicFormat(null, "file.cbp"))
    }

    @Test
    fun `isSupportedComicFormat returns false for unsupported extension`() {
        assertFalse(provider.isSupportedComicFormat(null, "image.jpg"))
    }

    @Test
    fun `isSupportedComicFormat returns false for pdf extension`() {
        // PDF is not in COMIC_EXTENSIONS (only in COMIC_MIME_TYPES)
        assertFalse(provider.isSupportedComicFormat(null, "doc.pdf"))
    }

    @Test
    fun `isSupportedComicFormat returns true for zip MIME type`() {
        assertTrue(provider.isSupportedComicFormat("application/zip", "unknown"))
    }

    @Test
    fun `isSupportedComicFormat returns true for rar MIME type`() {
        assertTrue(provider.isSupportedComicFormat("application/x-rar-compressed", "unknown"))
    }

    @Test
    fun `isSupportedComicFormat returns true for cbz MIME type`() {
        assertTrue(provider.isSupportedComicFormat("application/x-cbz", "unknown"))
    }

    @Test
    fun `isSupportedComicFormat returns true for 7z MIME type`() {
        assertTrue(provider.isSupportedComicFormat("application/x-7z-compressed", "unknown"))
    }

    @Test
    fun `isSupportedComicFormat returns true for pdf MIME type`() {
        assertTrue(provider.isSupportedComicFormat("application/pdf", "unknown"))
    }

    @Test
    fun `isSupportedComicFormat is case insensitive for extensions`() {
        assertTrue(provider.isSupportedComicFormat(null, "comic.CBZ"))
        assertTrue(provider.isSupportedComicFormat(null, "comic.Cbr"))
    }

    @Test
    fun `isSupportedComicFormat handles file with no extension`() {
        assertFalse(provider.isSupportedComicFormat(null, "noextension"))
    }

    // ==========================================================================
    // listFiles - pagination
    // ==========================================================================

    @Test
    fun `listFiles handles single page result`() = runTest {
        val driveFile = createDriveFile("file1", "comic.cbz", "application/zip", false, 1024)
        val pagedResult = PagedFileList(files = listOf(driveFile), nextPageToken = null)
        whenever(driveApiClient.listFiles(eq("folder1"), eq(null), any()))
            .thenReturn(Result.success(pagedResult))

        val result = provider.listFiles("folder1")

        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrThrow().size)
        assertEquals("file1", result.getOrThrow().first().identifier)
    }

    @Test
    fun `listFiles handles multi-page results`() = runTest {
        val file1 = createDriveFile("file1", "comic1.cbz", "application/zip", false, 1024)
        val file2 = createDriveFile("file2", "comic2.cbz", "application/zip", false, 2048)

        val page1 = PagedFileList(files = listOf(file1), nextPageToken = "page2token")
        val page2 = PagedFileList(files = listOf(file2), nextPageToken = null)

        whenever(driveApiClient.listFiles(eq("folder1"), eq(null), any()))
            .thenReturn(Result.success(page1))
        whenever(driveApiClient.listFiles(eq("folder1"), eq("page2token"), any()))
            .thenReturn(Result.success(page2))

        val result = provider.listFiles("folder1")

        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrThrow().size)
    }

    @Test
    fun `listFiles returns failure when API fails`() = runTest {
        whenever(driveApiClient.listFiles(any(), any(), any()))
            .thenReturn(Result.failure(RuntimeException("Network error")))

        val result = provider.listFiles("folder1")

        assertTrue(result.isFailure)
    }

    @Test
    fun `listFiles correctly maps folders`() = runTest {
        val folder = createDriveFile("folderId", "Comics", DriveApiClient.FOLDER_MIME_TYPE, true, 0)
        val pagedResult = PagedFileList(files = listOf(folder), nextPageToken = null)
        whenever(driveApiClient.listFiles(eq("parent1"), eq(null), any()))
            .thenReturn(Result.success(pagedResult))

        val result = provider.listFiles("parent1")

        assertTrue(result.isSuccess)
        val entry = result.getOrThrow().first()
        assertTrue(entry.isDirectory)
        assertEquals("folderId", entry.identifier)
        assertEquals("Comics", entry.displayName)
    }

    // ==========================================================================
    // isAccessible
    // ==========================================================================

    @Test
    fun `isAccessible delegates to DriveApiClient`() = runTest {
        whenever(driveApiClient.isAccessible()).thenReturn(true)
        assertTrue(provider.isAccessible())

        whenever(driveApiClient.isAccessible()).thenReturn(false)
        assertFalse(provider.isAccessible())
    }

    // ==========================================================================
    // resolveDisplayPath
    // ==========================================================================

    @Test
    fun `resolveDisplayPath includes account email`() = runTest {
        val file = createDriveFile("id1", "Comics", DriveApiClient.FOLDER_MIME_TYPE, true, 0)
        whenever(driveApiClient.getFileMetadata("id1")).thenReturn(Result.success(file))

        val path = provider.resolveDisplayPath("id1")

        assertTrue(path.startsWith("Google Drive (user@example.com)"))
        assertTrue(path.contains("Comics"))
    }

    @Test
    fun `resolveDisplayPath falls back to identifier on API failure`() = runTest {
        whenever(driveApiClient.getFileMetadata("id1"))
            .thenReturn(Result.failure(RuntimeException("Error")))

        val path = provider.resolveDisplayPath("id1")

        assertEquals("Google Drive (user@example.com)/id1", path)
    }

    // ==========================================================================
    // Helpers
    // ==========================================================================

    private fun createDriveFile(
        id: String,
        name: String,
        mimeType: String,
        isFolder: Boolean,
        size: Long,
    ): File {
        val file = File()
        file.id = id
        file.name = name
        file.mimeType = if (isFolder) DriveApiClient.FOLDER_MIME_TYPE else mimeType
        file.setSize(size)
        return file
    }
}
