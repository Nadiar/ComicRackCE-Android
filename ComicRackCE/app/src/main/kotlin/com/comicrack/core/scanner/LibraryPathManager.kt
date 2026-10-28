package com.comicrack.core.scanner

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.comicrack.data.database.ComicRackDatabase
import com.comicrack.data.database.dao.LibraryPathDao
import com.comicrack.data.database.entities.LibraryPath
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import java.io.File

/**
 * Manager for library path storage and SAF URI permission handling.
 *
 * Responsible for:
 * - Adding new library paths with SAF URI persistence
 * - Retrieving and managing persisted library paths
 * - Handling SAF URI permissions (takePersistableUriPermission)
 * - Activating/deactivating library paths
 *
 * Phase 3: Task 3.3 - Library Scanner SAF Migration
 * Migrated to Kotlin.
 *
 * TECHNICAL DEBT NOTE: This class uses runBlocking for database operations.
 * These are primarily called from Android lifecycle methods (onCreate, onResume) where
 * coroutines cannot be launched directly. Prefer async/await patterns in new code.
 */
class LibraryPathManager(
    private val context: Context,
    private val dao: LibraryPathDao,
) {
    constructor(context: Context) : this(context, ComicRackDatabase.getInstance(context).libraryPathDao())
    constructor(context: Context, db: ComicRackDatabase) : this(context, db.libraryPathDao())

    companion object {
        private const val TAG = "LibraryPathManager"
    }

    /**
     * Add a new library path with SAF URI persistence.
     *
     * Calls takePersistableUriPermission to maintain access across app restarts.
     * The URI permission is granted by the user through the file picker.
     *
     * @param treeUri SAF tree URI (from ACTION_OPEN_DOCUMENT_TREE picker)
     * @param displayName User-friendly display name for the library
     * @return The ID of the inserted library path
     * @throws Exception if URI permission cannot be persisted
     */
    fun addLibraryPath(
        treeUri: Uri?,
        displayName: String?,
    ): Long {
        requireNotNull(treeUri) { "treeUri must not be null" }
        requireNotNull(displayName) { "displayName must not be null" }

        try {
            // Persist SAF URI permission so we can access it after app restart
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            Timber.tag(TAG).d("Persisted SAF URI permission for: $displayName")
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to persist URI permission (may not be a tree URI): $treeUri")
            // Continue anyway - URI might still be accessible
        }

        // Create and insert LibraryPath entity
        val path =
            LibraryPath(
                path = treeUri.toString(),
                displayName = displayName,
            ).apply {
                createdAt = System.currentTimeMillis()
                lastScannedAt = 0
                comicCount = 0
                isActive = true
            }

        val id = runBlocking { dao.insertLibraryPath(path) }
        Timber.tag(TAG).d("Added library path: $displayName (ID: $id)")

        return id
    }

    /**
     * Add a local file path as a library (for app-specific cache/data directories).
     *
     * @param filePath Full file path to directory
     * @param displayName User-friendly display name
     * @return The ID of the inserted library path
     * @throws Exception if path is invalid
     */
    fun addLocalLibraryPath(
        filePath: String?,
        displayName: String?,
    ): Long {
        requireNotNull(filePath) { "filePath must not be null" }
        requireNotNull(displayName) { "displayName must not be null" }

        val dir = File(filePath)
        require(dir.isDirectory) { "Path is not a directory: $filePath" }

        // Create and insert LibraryPath entity
        val path =
            LibraryPath(
                path = filePath,
                displayName = displayName,
            ).apply {
                createdAt = System.currentTimeMillis()
                lastScannedAt = 0
                comicCount = 0
                isActive = true
            }

        val id = runBlocking { dao.insertLibraryPath(path) }
        Timber.tag(TAG).d("Added local library path: $displayName (ID: $id)")

        return id
    }

    /**
     * Get all library paths (both active and inactive).
     *
     * @return List of all persisted library paths
     */
    fun getAllLibraryPaths(): List<LibraryPath> =
        runBlocking {
            dao.getAllLibraryPaths()
        }

    /**
     * Get only active library paths that should be scanned.
     *
     * @return List of active library paths
     */
    fun getActiveLibraryPaths(): List<LibraryPath> =
        runBlocking {
            dao.getActiveLibraryPaths()
        }

    /**
     * Get a library path by ID.
     *
     * @param id The library path ID
     * @return The LibraryPath, or null if not found
     */
    fun getLibraryPathById(id: Long): LibraryPath? =
        runBlocking {
            dao.getLibraryPathById(id)
        }

    /**
     * Get a library path by URI/file path string.
     *
     * @param path The path URI or file path
     * @return The LibraryPath, or null if not found
     */
    fun getLibraryPathByPath(path: String?): LibraryPath? {
        if (path == null) return null
        return runBlocking { dao.getLibraryPathByPath(path) }
    }

    /**
     * Activate a library path (re-enable scanning).
     *
     * @param id The library path ID
     */
    fun activateLibraryPath(id: Long) =
        runBlocking {
            dao.reactivateLibraryPath(id)
            Timber.tag(TAG).d("Activated library path ID: $id")
        }

    /**
     * Deactivate a library path (disable scanning without deleting).
     *
     * @param id The library path ID
     */
    fun deactivateLibraryPath(id: Long) =
        runBlocking {
            dao.deactivateLibraryPath(id)
            Timber.tag(TAG).d("Deactivated library path ID: $id")
        }

    /**
     * Delete a library path and all its associated comics.
     *
     * @param id The library path ID
     */
    fun deleteLibraryPath(id: Long) =
        runBlocking {
            dao.deleteLibraryPathById(id)
            // Note: ComicBook cascading delete should be handled by Room with appropriate annotations
            Timber.tag(TAG).d("Deleted library path ID: $id")
        }

    /**
     * Update library path metadata.
     *
     * @param path The LibraryPath with updated fields
     */
    fun updateLibraryPath(path: LibraryPath) =
        runBlocking {
            dao.updateLibraryPath(path)
            Timber.tag(TAG).d("Updated library path: ${path.displayName}")
        }

    /**
     * Get total number of comics across all library paths.
     *
     * @return Total comic count
     */
    fun getTotalComicCount(): Int =
        runBlocking {
            dao.getTotalComicCount()
        }

    /**
     * Check if a library path is already registered.
     *
     * @param pathUri URI or file path
     * @return true if already registered, false otherwise
     */
    fun isPathRegistered(pathUri: String?): Boolean {
        if (pathUri == null) return false
        return runBlocking { dao.getLibraryPathByPath(pathUri) != null }
    }

    /**
     * Release SAF URI permission (if no longer needed).
     *
     * Note: This removes the persistent permission grant, but the user
     * can re-grant by selecting the folder again.
     *
     * @param uri The SAF URI
     */
    fun releaseSafPermission(uri: Uri?) {
        if (uri == null) return
        try {
            context.contentResolver.releasePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            Timber.tag(TAG).d("Released SAF permission for: $uri")
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to release SAF permission")
        }
    }
}
