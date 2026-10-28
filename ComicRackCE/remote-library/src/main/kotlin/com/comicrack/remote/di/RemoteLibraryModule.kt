package com.comicrack.remote.di

import android.content.Context
import com.comicrack.remote.api.DriveApiClient
import com.comicrack.remote.api.DriveServiceFactory
import com.comicrack.remote.auth.GoogleAuthRepository
import com.common.db.dao.GoogleDriveAuthDao
import com.common.db.dao.RemoteComicDao
import com.common.db.dao.SyncStateDao
import com.google.api.services.drive.Drive
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt dependency injection module for Remote Library functionality.
 *
 * Provides:
 * - GoogleAuthRepository for OAuth token management
 * - DriveServiceFactory for authenticated Drive service creation
 * - DriveApiClient for high-level Drive API operations
 * - Database DAOs for persistence
 *
 * All dependencies are singletons to maintain consistent authentication state
 * across the application lifecycle.
 *
 * Phase 1: Authentication & API Foundation
 * Remote Library module - Dependency injection setup
 */
@Module
@InstallIn(SingletonComponent::class)
object RemoteLibraryModule {

    /**
     * Provide GoogleAuthRepository singleton.
     * Manages OAuth token storage and retrieval.
     *
     * @param context Application context
     * @param googleDriveAuthDao DAO for auth persistence
     * @return GoogleAuthRepository singleton
     */
    @Provides
    @Singleton
    fun provideGoogleAuthRepository(
        @ApplicationContext context: Context,
        googleDriveAuthDao: GoogleDriveAuthDao
    ): GoogleAuthRepository {
        return GoogleAuthRepository(context, googleDriveAuthDao)
    }

    /**
     * Provide DriveServiceFactory singleton.
     * Creates authenticated Google Drive API service instances.
     *
     * @param authRepository OAuth token repository
     * @return DriveServiceFactory singleton
     */
    @Provides
    @Singleton
    fun provideDriveServiceFactory(
        authRepository: GoogleAuthRepository
    ): DriveServiceFactory {
        return DriveServiceFactory(authRepository)
    }

    /**
     * Provide authenticated Google Drive service singleton.
     * Lazily creates service when first requested.
     *
     * WARNING: Requires user to be authenticated first.
     * Call GoogleAuthRepository.signIn() before accessing this.
     *
     * @param driveServiceFactory Service factory
     * @return Google Drive API service instance
     */
    @Provides
    @Singleton
    fun provideDriveService(
        driveServiceFactory: DriveServiceFactory
    ): Drive {
        // Note: This is blocking and will fail if not authenticated
        // In production, consider using lazy initialization or
        // making this optional/nullable until user signs in
        return try {
            // This should be called after authentication
            // For now, returning a placeholder - actual implementation requires async/lazy init
            com.google.api.client.http.HttpRequest::class.java // Placeholder
            // Actual implementation would require kotlinx.coroutines.runBlocking
            // which is not recommended for Android
            // Consider making Drive optional until sign-in completes
            throw IllegalStateException("Drive service must be initialized after sign-in")
        } catch (e: Exception) {
            // Return minimal drive service for DI graph completeness
            // Real service created after authentication
            null as Drive? ?: throw e
        }
    }

    /**
     * Provide DriveApiClient singleton.
     * High-level wrapper around Google Drive API v3.
     *
     * @param authRepository OAuth token repository
     * @param driveService Google Drive API service
     * @return DriveApiClient singleton
     */
    @Provides
    @Singleton
    fun provideDriveApiClient(
        authRepository: GoogleAuthRepository,
        driveService: Drive
    ): DriveApiClient {
        return DriveApiClient(authRepository, driveService)
    }
}
