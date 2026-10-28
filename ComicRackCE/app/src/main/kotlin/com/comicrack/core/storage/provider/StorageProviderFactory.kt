package com.comicrack.core.storage.provider

import android.content.Context
import android.net.Uri
import com.comicrack.core.auth.GoogleAuthManager
import com.comicrack.core.storage.drive.DriveApiClient
import com.comicrack.core.storage.drive.GoogleDriveStorageProvider
import com.comicrack.data.database.entities.LibraryPath
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Creates the appropriate [StorageProvider] for a given [LibraryPath].
 *
 * Centralizes provider creation logic so the scanner pipeline only
 * needs to call [createProvider] without knowing backend specifics.
 */
@Singleton
class StorageProviderFactory @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val driveApiClient: DriveApiClient,
    private val authManager: GoogleAuthManager,
) {

    /**
     * Create a [StorageProvider] for the given library path.
     *
     * @param libraryPath The library path entity with storageType and path info
     * @return The appropriate StorageProvider, or null if the type is not yet supported
     */
    fun createProvider(libraryPath: LibraryPath): StorageProvider? {
        val storageType = StorageType.fromValue(libraryPath.storageType)
        return when (storageType) {
            StorageType.LOCAL -> LocalFileStorageProvider()
            StorageType.SAF -> SAFStorageProvider(context, Uri.parse(libraryPath.path))
            StorageType.GOOGLE_DRIVE -> {
                val email = (authManager.authState.value as? com.comicrack.core.auth.GoogleAuthState.SignedIn)?.email ?: ""
                GoogleDriveStorageProvider(driveApiClient, email)
            }
        }
    }

    /**
     * Create a [StorageProvider] from explicit type and path.
     *
     * Useful when the caller has type + path but not a full [LibraryPath] entity.
     */
    fun createProvider(storageType: StorageType, path: String): StorageProvider? =
        when (storageType) {
            StorageType.LOCAL -> LocalFileStorageProvider()
            StorageType.SAF -> SAFStorageProvider(context, Uri.parse(path))
            StorageType.GOOGLE_DRIVE -> {
                val email = (authManager.authState.value as? com.comicrack.core.auth.GoogleAuthState.SignedIn)?.email ?: ""
                GoogleDriveStorageProvider(driveApiClient, email)
            }
        }
}
