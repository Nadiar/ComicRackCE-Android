package com.comicrack.data.database

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.comicrack.data.database.dao.ComicBookDao
import com.comicrack.data.database.dao.ComicMetadataDao
import com.comicrack.data.database.dao.GoogleDriveAuthDao
import com.comicrack.data.database.dao.LibraryPathDao
import com.comicrack.data.database.dao.ReadProgressDao
import com.comicrack.data.database.dao.SeriesPreferencesDao
import com.comicrack.data.database.dao.SettingsDao
import com.comicrack.data.database.dao.SettingsDaoAdapter
import com.comicrack.data.database.dao.SyncStateDao
import com.comicrack.data.database.entities.ComicBook
import com.comicrack.data.database.entities.ComicMetadata
import com.comicrack.data.database.entities.GoogleDriveAuth
import com.comicrack.data.database.entities.LibraryPath
import com.comicrack.data.database.entities.ReadProgress
import com.comicrack.data.database.entities.SeriesPreferences
import com.comicrack.data.database.entities.Settings
import com.comicrack.data.database.entities.SyncState
import com.comicrack.data.database.migration.MIGRATION_6_7
import timber.log.Timber

/**
 * Room database for ComicRack library and cache metadata.
 *
 * Replaces legacy in-memory structures and FileCacheSource index serialization
 * with a proper relational database backed by Room.
 *
 * Entities:
 * - LibraryPath: SAF URIs and local library paths
 * - ComicBook: Discovered comics with metadata (title, format, page count)
 * - ComicMetadata: Parsed metadata from ComicInfo.xml / MetronInfo.xml (Phase Zeta)
 * - ReadProgress: User's page position and read status (Phase B)
 * - SeriesPreferences: Per-series reader settings (Phase B)
 * - Settings: Application-wide preferences and settings (Project Epsilon)
 *
 * Phase 3: Task 3.1 â€” Room Database Schema
 * Phase B: Library Preferences System
 * Project Epsilon: Preferences System
 * Project Zeta: Metadata Parsing & Validation (Phase 1 in progress)
 * **Migrated to Kotlin** for improved null safety and idiomatic Android development.
 */
@Database(
    entities = [
        LibraryPath::class,
        ComicBook::class,
        ComicMetadata::class,
        ReadProgress::class,
        SeriesPreferences::class,
        Settings::class,
        GoogleDriveAuth::class,
        SyncState::class,
    ],
    version = 7,
    exportSchema = false,
)
abstract class ComicRackDatabase : RoomDatabase() {
    /**
     * Get the LibraryPath data access object.
     */
    abstract fun libraryPathDao(): LibraryPathDao

    /**
     * Get the ComicBook data access object.
     */
    abstract fun comicBookDao(): ComicBookDao

    /**
     * Get the ComicMetadata data access object.
     */
    abstract fun comicMetadataDao(): ComicMetadataDao

    /**
     * Get the ReadProgress data access object.
     */
    abstract fun readProgressDao(): ReadProgressDao

    /**
     * Get the SeriesPreferences data access object.
     */
    abstract fun seriesPreferencesDao(): SeriesPreferencesDao

    /**
     * Get the Settings data access object.
     */
    abstract fun settingsDao(): SettingsDao

    /**
     * Get the GoogleDriveAuth data access object.
     */
    abstract fun googleDriveAuthDao(): GoogleDriveAuthDao

    /**
     * Get the SyncState data access object.
     */
    abstract fun syncStateDao(): SyncStateDao

    /**
     * Get the Settings DAO adapter (synchronous wrapper for Java interop).
     * @return Adapter for blocking DAO calls
     */
    fun settingsDaoAdapter(): SettingsDaoAdapter = SettingsDaoAdapter(settingsDao())

    companion object {
        /**
         * Singleton instance of the database.
         * Uses lazy initialization (double-checked locking).
         */
        @Volatile
        private var instance: ComicRackDatabase? = null

        private var allowMainThreadQueries = false

        /**
         * Enable main thread queries for testing purposes.
         */
        fun setAllowMainThreadQueries(allow: Boolean) {
            allowMainThreadQueries = allow
        }

        /**
         * Get or create the singleton database instance.
         * Thread-safe with lazy initialization.
         *
         * @param context The application context
         * @return The database instance
         */
        @JvmStatic
        fun getInstance(context: Context): ComicRackDatabase =
            instance ?: synchronized(this) {
                instance ?: createDatabase(context).also { instance = it }
            }

        private fun createDatabase(context: Context): ComicRackDatabase {
            val builder =
                Room
                    .databaseBuilder(
                        context.applicationContext,
                        ComicRackDatabase::class.java,
                        "comicracks.db",
                    ).addMigrations(MIGRATION_6_7)
                    .fallbackToDestructiveMigration()
                    .addCallback(
                        object : Callback() {
                            override fun onCreate(db: SupportSQLiteDatabase) {
                                super.onCreate(db)
                                Timber.tag("ComicRackDatabase").d("Database created, schema initialized")
                            }

                            override fun onOpen(db: SupportSQLiteDatabase) {
                                super.onOpen(db)
                                Timber.tag("ComicRackDatabase").d("Database opened, version=${db.version}")
                            }
                        },
                    )

            if (allowMainThreadQueries) {
                builder.allowMainThreadQueries()
            }

            return builder.build()
        }

        /**
         * Reset the singleton instance.
         * Used for testing to ensure fresh database state.
         */
        fun resetInstance() {
            instance = null
        }

        /**
         * Create an in-memory database for testing.
         * Allows main thread queries for unit testing with Robolectric.
         */
        fun getTestInstance(context: Context): ComicRackDatabase =
            Room
                .inMemoryDatabaseBuilder(
                    context.applicationContext,
                    ComicRackDatabase::class.java,
                ).fallbackToDestructiveMigration()
                .allowMainThreadQueries()
                .build()
    }
}
