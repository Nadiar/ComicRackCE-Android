package com.comicrack.core.sync

import com.comicrack.core.storage.provider.StorageFileEntry
import com.comicrack.core.storage.provider.StorageProvider
import com.comicrack.core.storage.provider.StorageProviderFactory
import com.comicrack.core.storage.provider.StorageType
import com.comicrack.data.database.dao.ComicBookDao
import com.comicrack.data.database.dao.LibraryPathDao
import com.comicrack.data.database.dao.SyncStateDao
import com.comicrack.data.database.entities.ComicBook
import com.comicrack.data.database.entities.LibraryPath
import com.comicrack.data.database.entities.SyncState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Tests for [RemoteSyncService].
 */
class RemoteSyncServiceTest {

    private lateinit var libraryPathDao: LibraryPathDao
    private lateinit var comicDao: ComicBookDao
    private lateinit var syncStateDao: SyncStateDao
    private lateinit var storageProviderFactory: StorageProviderFactory
    private lateinit var syncService: RemoteSyncService

    @Before
    fun setup() {
        libraryPathDao = mock()
        comicDao = mock()
        syncStateDao = mock()
        storageProviderFactory = mock()
        syncService = RemoteSyncService(libraryPathDao, comicDao, syncStateDao, storageProviderFactory)
    }

    // ==========================================================================
    // SyncResult
    // ==========================================================================

    @Test
    fun `SyncResult isSuccessful returns true when no error`() {
        val result = RemoteSyncService.SyncResult(totalComicsFound = 5)
        assertTrue(result.isSuccessful)
    }

    @Test
    fun `SyncResult isSuccessful returns false when error present`() {
        val result = RemoteSyncService.SyncResult(error = "something failed")
        assertTrue(!result.isSuccessful)
    }

    // ==========================================================================
    // syncAllRemotePaths
    // ==========================================================================

    @Test
    fun `syncAllRemotePaths returns empty list when no remote paths`() = runTest {
        whenever(libraryPathDao.getByStorageType(StorageType.GOOGLE_DRIVE.value))
            .thenReturn(emptyList())

        val results = syncService.syncAllRemotePaths()

        assertTrue(results.isEmpty())
    }

    // ==========================================================================
    // syncPath - provider errors
    // ==========================================================================

    @Test
    fun `syncPath returns error when provider is null`() = runTest {
        val path = createLibraryPath(storageType = "UNSUPPORTED_TYPE")
        whenever(storageProviderFactory.createProvider(path)).thenReturn(null)
        whenever(syncStateDao.getSyncStatus(any())).thenReturn(null)

        val result = syncService.syncPath(path)

        assertTrue(!result.isSuccessful)
        assertTrue(result.error!!.contains("No provider"))
    }

    @Test
    fun `syncPath returns error when provider is not accessible`() = runTest {
        val path = createLibraryPath()
        val provider = mock<StorageProvider>()
        whenever(provider.isAccessible()).thenReturn(false)
        whenever(storageProviderFactory.createProvider(path)).thenReturn(provider)
        whenever(syncStateDao.getSyncStatus(any())).thenReturn(null)

        val result = syncService.syncPath(path)

        assertTrue(!result.isSuccessful)
        assertTrue(result.error!!.contains("not accessible"))
    }

    // ==========================================================================
    // syncPath - successful scan
    // ==========================================================================

    @Test
    fun `syncPath discovers comics in flat folder`() = runTest {
        val path = createLibraryPath(pathStr = "rootFolderId")
        val provider = createMockProvider(
            accessible = true,
            folderContents = mapOf(
                "rootFolderId" to listOf(
                    createFileEntry("file1", "comic1.cbz", isDir = false),
                    createFileEntry("file2", "comic2.cbr", isDir = false),
                ),
            ),
        )
        whenever(storageProviderFactory.createProvider(path)).thenReturn(provider)
        whenever(syncStateDao.getSyncStatus(any())).thenReturn(null)
        whenever(comicDao.getComicsByStorageType(any())).thenReturn(emptyList())

        val result = syncService.syncPath(path)

        assertTrue(result.isSuccessful)
        assertEquals(2, result.totalComicsFound)
        assertEquals(2, result.newComicsAdded)
    }

    @Test
    fun `syncPath traverses subfolders via BFS`() = runTest {
        val path = createLibraryPath(pathStr = "root")
        val provider = createMockProvider(
            accessible = true,
            folderContents = mapOf(
                "root" to listOf(
                    createFileEntry("subfolder1", "SubFolder", isDir = true),
                    createFileEntry("file1", "comic.cbz", isDir = false),
                ),
                "subfolder1" to listOf(
                    createFileEntry("file2", "nested.cbr", isDir = false),
                ),
            ),
        )
        whenever(storageProviderFactory.createProvider(path)).thenReturn(provider)
        whenever(syncStateDao.getSyncStatus(any())).thenReturn(null)
        whenever(comicDao.getComicsByStorageType(any())).thenReturn(emptyList())

        val result = syncService.syncPath(path)

        assertTrue(result.isSuccessful)
        assertEquals(2, result.totalComicsFound)
    }

    @Test
    fun `syncPath removes stale comics`() = runTest {
        val path = createLibraryPath(id = 10, pathStr = "root")
        val provider = createMockProvider(
            accessible = true,
            folderContents = mapOf(
                "root" to listOf(
                    createFileEntry("file1", "comic1.cbz", isDir = false),
                ),
            ),
        )
        whenever(storageProviderFactory.createProvider(path)).thenReturn(provider)
        whenever(syncStateDao.getSyncStatus(any())).thenReturn(null)

        // Pre-existing comic that is no longer on Drive
        val staleComic = ComicBook().apply {
            id = 99
            remoteId = "stale-file"
            storageType = StorageType.GOOGLE_DRIVE.value
            libraryPathId = 10
        }
        whenever(comicDao.getComicsByStorageType(any())).thenReturn(listOf(staleComic))

        val result = syncService.syncPath(path)

        assertTrue(result.isSuccessful)
        assertEquals(1, result.comicsRemoved)
        verify(comicDao).deleteComicBook(staleComic)
    }

    @Test
    fun `syncPath does not remove comics from other library paths`() = runTest {
        val path = createLibraryPath(id = 10, pathStr = "root")
        val provider = createMockProvider(
            accessible = true,
            folderContents = mapOf("root" to emptyList()),
        )
        whenever(storageProviderFactory.createProvider(path)).thenReturn(provider)
        whenever(syncStateDao.getSyncStatus(any())).thenReturn(null)

        // Comic from a different library path
        val otherPathComic = ComicBook().apply {
            id = 50
            remoteId = "other-file"
            storageType = StorageType.GOOGLE_DRIVE.value
            libraryPathId = 99 // Different path
        }
        whenever(comicDao.getComicsByStorageType(any())).thenReturn(listOf(otherPathComic))

        val result = syncService.syncPath(path)

        assertTrue(result.isSuccessful)
        assertEquals(0, result.comicsRemoved)
        verify(comicDao, never()).deleteComicBook(otherPathComic)
    }

    // ==========================================================================
    // Helpers
    // ==========================================================================

    private fun createLibraryPath(
        id: Long = 1,
        pathStr: String = "testFolderId",
        storageType: String = StorageType.GOOGLE_DRIVE.value,
    ): LibraryPath {
        val lp = LibraryPath(path = pathStr)
        lp.id = id
        lp.storageType = storageType
        return lp
    }

    private fun createFileEntry(
        id: String,
        name: String,
        isDir: Boolean,
        size: Long = 1024,
    ) = StorageFileEntry(
        identifier = id,
        displayName = name,
        isDirectory = isDir,
        size = if (isDir) 0 else size,
        lastModified = System.currentTimeMillis(),
        mimeType = if (isDir) "application/vnd.google-apps.folder" else "application/zip",
    )

    private suspend fun createMockProvider(
        accessible: Boolean,
        folderContents: Map<String, List<StorageFileEntry>>,
    ): StorageProvider {
        val provider = mock<StorageProvider>()
        whenever(provider.type).thenReturn(StorageType.GOOGLE_DRIVE)
        whenever(provider.isAccessible()).thenReturn(accessible)

        for ((folderId, entries) in folderContents) {
            whenever(provider.listFiles(folderId)).thenReturn(Result.success(entries))
        }

        return provider
    }
}
