package com.comicrack.data.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room migration from v6 to v7: Remote storage support.
 *
 * Adds columns to `library_paths` and `comics` for multi-backend storage,
 * creates `google_drive_auth` and `sync_state` tables, and adds indexes
 * for efficient remote comic queries.
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // --- LibraryPath extensions ---
        db.execSQL("ALTER TABLE library_paths ADD COLUMN storageType TEXT NOT NULL DEFAULT 'SAF'")
        db.execSQL("ALTER TABLE library_paths ADD COLUMN providerConfig TEXT")
        db.execSQL("ALTER TABLE library_paths ADD COLUMN syncIntervalMinutes INTEGER NOT NULL DEFAULT 60")
        db.execSQL("ALTER TABLE library_paths ADD COLUMN requiresAuth INTEGER NOT NULL DEFAULT 0")

        // --- ComicBook extensions ---
        db.execSQL("ALTER TABLE comics ADD COLUMN storageType TEXT NOT NULL DEFAULT 'LOCAL'")
        db.execSQL("ALTER TABLE comics ADD COLUMN remoteId TEXT")
        db.execSQL("ALTER TABLE comics ADD COLUMN cacheLocalPath TEXT")
        db.execSQL("ALTER TABLE comics ADD COLUMN thumbnailRemoteUrl TEXT")
        db.execSQL("ALTER TABLE comics ADD COLUMN syncedAt INTEGER NOT NULL DEFAULT 0")

        // --- GoogleDriveAuth table ---
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS google_drive_auth (
                id INTEGER NOT NULL PRIMARY KEY,
                accessToken TEXT NOT NULL DEFAULT '',
                refreshToken TEXT NOT NULL DEFAULT '',
                userEmail TEXT NOT NULL DEFAULT '',
                userDisplayName TEXT,
                expiresAt INTEGER NOT NULL DEFAULT 0,
                createdAt INTEGER NOT NULL DEFAULT 0,
                updatedAt INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )

        // --- SyncState table ---
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS sync_state (
                libraryPathId INTEGER NOT NULL PRIMARY KEY,
                lastSyncAt INTEGER NOT NULL DEFAULT 0,
                lastSyncStatus TEXT NOT NULL DEFAULT 'PENDING',
                lastErrorMessage TEXT,
                totalFilesFound INTEGER NOT NULL DEFAULT 0,
                totalComicsFound INTEGER NOT NULL DEFAULT 0,
                durationMs INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )

        // --- Indexes for remote comic queries ---
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_comics_remoteId ON comics(remoteId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_comics_storageType ON comics(storageType)")
    }
}
