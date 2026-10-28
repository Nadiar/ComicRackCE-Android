package com.comicrack.core.di

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

import android.content.Context
import com.comicrack.core.metadata.ArchiveStreamFactory
import com.comicrack.core.metadata.ComicMetadataService
import com.comicrack.core.metadata.MetadataDispatcher
import com.comicrack.core.metadata.MetadataMapper
import com.comicrack.core.scanner.LibraryScannerService
import com.comicrack.core.scanner.LibraryService
import com.comicrack.core.storage.provider.StorageProviderFactory
import com.comicrack.data.database.ComicRackDatabase
import com.comicrack.data.database.dao.ComicBookDao
import com.comicrack.data.database.dao.ComicMetadataDao
import com.comicrack.data.database.dao.LibraryPathDao
import com.comicrack.data.database.dao.ReadProgressDao
import com.comicrack.data.database.dao.SettingsDao
import com.comicrack.data.database.dao.SyncStateDao
import com.comicrack.data.database.repository.ComicRackMetadataRepository
import com.comicrack.wifi.metadata.SyncMetadataService
import com.comicrack.reader.data.ComicPageProvider
import com.comicrack.reader.data.PlaceholderPageProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt dependency injection module for library scanning services.
 *
 * Provides singleton instances of:
 * - ComicRackDatabase (Room database)
 * - MetadataParser (XML metadata extraction)
 * - ComicRackMetadataRepository (persistence layer)
 * - ComicMetadataService (metadata loading service)
 * - LibraryScannerService (file discovery)
 * - LibraryService (orchestration)
 * - SyncMetadataService (WiFi sync metadata management)
 *
 * All services are scoped to SingletonComponent for app-wide lifetime.
 *
 * Phase 4, Task 4.7: Dependency Injection
 *
 * DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)
 */
@Module
@InstallIn(SingletonComponent::class)
object ScannerModule {
    /**
     * Provide ComicRackDatabase singleton instance.
     *
     * Uses Room.databaseBuilder() to create or access existing database.
     * Singleton scoped to prevent multiple database instances.
     *
     * @param context Application context for database file access
     * @return ComicRackDatabase instance
     */
    @Singleton
    @Provides
    fun provideComicRackDatabase(
        @ApplicationContext context: Context,
    ): ComicRackDatabase = ComicRackDatabase.getInstance(context)

    @Singleton
    @Provides
    fun provideLibraryPathDao(database: ComicRackDatabase): LibraryPathDao = database.libraryPathDao()

    @Singleton
    @Provides
    fun provideComicBookDao(database: ComicRackDatabase): ComicBookDao = database.comicBookDao()

    @Singleton
    @Provides
    fun provideReadProgressDao(database: ComicRackDatabase): ReadProgressDao = database.readProgressDao()

    @Singleton
    @Provides
    fun provideComicMetadataDao(database: ComicRackDatabase): ComicMetadataDao = database.comicMetadataDao()

    @Singleton
    @Provides
    fun provideSettingsDao(database: ComicRackDatabase): SettingsDao = database.settingsDao()

    @Singleton
    @Provides
    fun provideSyncStateDao(database: ComicRackDatabase): SyncStateDao = database.syncStateDao()


    /**
     * Provide ComicRackMetadataRepository singleton instance.
     *
     * Wraps metadata DAO for persistence operations.
     *
     * @param database ComicRackDatabase instance
     * @return ComicRackMetadataRepository instance
     */
    @Singleton
    @Provides
    fun provideComicMetadataRepository(database: ComicRackDatabase): ComicRackMetadataRepository =
        ComicRackMetadataRepository(database.comicMetadataDao())

    /**
     * Provide MetadataMapper singleton instance.
     *
     * @return MetadataMapper instance
     */
    @Singleton
    @Provides
    fun provideMetadataMapper(): MetadataMapper = MetadataMapper()

    /**
     * Provide ArchiveStreamFactory singleton instance.
     *
     * @param context Application context
     * @return ArchiveStreamFactory instance
     */
    @Singleton
    @Provides
    fun provideArchiveStreamFactory(
        @ApplicationContext context: Context,
    ): ArchiveStreamFactory = ArchiveStreamFactory(context)

    /**
     * Provide MetadataDispatcher singleton instance.
     *
     * @param context Application context
     * @param archiveStreamFactory Factory for streaming archive content
     * @return MetadataDispatcher instance
     */
    @Singleton
    @Provides
    fun provideMetadataDispatcher(
        @ApplicationContext context: Context,
        archiveStreamFactory: ArchiveStreamFactory,
    ): MetadataDispatcher = MetadataDispatcher(context, archiveStreamFactory)

    /**
     * Provide ComicMetadataService singleton instance.
     *
     * Lazy-loads or batch-scans metadata for comics using modular extractors.
     *
     * @param context Application context for file access
     * @param repository Metadata persistence layer
     * @param dispatcher Metadata extractor dispatcher
     * @param mapper Metadata mapping and normalization utility
     * @return ComicMetadataService instance
     */
    @Singleton
    @Provides
    fun provideComicMetadataService(
        repository: ComicRackMetadataRepository,
        dispatcher: MetadataDispatcher,
        mapper: MetadataMapper,
    ): ComicMetadataService = ComicMetadataService(repository, dispatcher, mapper)

    /**
     * Provide LibraryScannerService singleton instance.
     *
     * Discovers comic files in configured library paths.
     *
     * @param context Application context for storage access
     * @return LibraryScannerService instance
     */
    @Singleton
    @Provides
    fun provideLibraryScannerService(
        @ApplicationContext context: Context,
        storageProviderFactory: StorageProviderFactory,
    ): LibraryScannerService {
        val db = ComicRackDatabase.getInstance(context)
        return LibraryScannerService(
            context,
            db.libraryPathDao(),
            db.comicBookDao(),
            db,
            storageProviderFactory,
        )
    }

    /**
     * Provide LibraryService singleton instance.
     *
     * Orchestrates file discovery and metadata loading.
     *
     * @param scannerService File discovery service
     * @param metadataService Metadata loading service
     * @return LibraryService instance
     */
    @Singleton
    @Provides
    fun provideLibraryService(
        scannerService: LibraryScannerService,
        metadataService: ComicMetadataService,
    ): LibraryService = LibraryService(scannerService, metadataService)

    /**
     * Provide SyncMetadataService singleton instance.
     *
     * Manages sidecar XML files for WiFi sync.
     *
     * @param context Application context
     * @return SyncMetadataService instance
     */
    @Singleton
    @Provides
    fun provideSyncMetadataService(
        @ApplicationContext context: Context,
    ): SyncMetadataService = SyncMetadataService(context)

    /**
     * Provide default ComicPageProvider (placeholder).
     * Used as default injection; will be replaced by actual provider at runtime.
     *
     * @return Placeholder page provider
     */
    @Provides
    fun provideDefaultComicPageProvider(): ComicPageProvider = PlaceholderPageProvider("Unknown Series", "1")
}
