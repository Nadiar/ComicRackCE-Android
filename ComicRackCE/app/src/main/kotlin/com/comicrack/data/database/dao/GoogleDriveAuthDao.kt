package com.comicrack.data.database.dao

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.comicrack.data.database.entities.GoogleDriveAuth

/**
 * Data Access Object for GoogleDriveAuth entity.
 *
 * Manages Google Drive OAuth token persistence. Using singleton pattern (id=1)
 * to ensure only one active authentication state at a time.
 *
 * Phase 1: Authentication & API Foundation
 * Remote Library module - Google Drive authentication
 */
@Dao
interface GoogleDriveAuthDao {
    /**
     * Insert or replace Google Drive authentication credentials.
     * Uses singleton pattern - only one record should exist (id=1).
     *
     * @param auth The GoogleDriveAuth object with tokens
     * @return The row ID (always 1)
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuth(auth: GoogleDriveAuth): Long

    /**
     * Update existing authentication credentials.
     * Typically used when refreshing the access token.
     *
     * @param auth The GoogleDriveAuth object with updated tokens/timestamps
     */
    @Update
    suspend fun updateAuth(auth: GoogleDriveAuth)

    /**
     * Retrieve the stored authentication credentials.
     * Uses singleton pattern - returns the single record or null if never authenticated.
     *
     * @return The GoogleDriveAuth object, or null if no auth exists
     */
    @Query("SELECT * FROM google_drive_auth WHERE id = 1")
    suspend fun getAuth(): GoogleDriveAuth?

    /**
     * Delete authentication credentials (sign out).
     * Removes the singleton auth record.
     */
    @Delete
    suspend fun deleteAuth(auth: GoogleDriveAuth)

    /**
     * Clear all authentication data.
     * Used for sign-out functionality.
     */
    @Query("DELETE FROM google_drive_auth WHERE id = 1")
    suspend fun clearAuth()

    /**
     * Check if authentication exists without retrieving full data.
     *
     * @return true if auth record exists, false otherwise
     */
    @Query("SELECT EXISTS(SELECT 1 FROM google_drive_auth WHERE id = 1)")
    suspend fun authExists(): Boolean

    /**
     * Get only the access token (for API requests).
     *
     * @return The access token string, or null if no auth exists
     */
    @Query("SELECT accessToken FROM google_drive_auth WHERE id = 1")
    suspend fun getAccessToken(): String?

    /**
     * Get only the refresh token (for token refresh).
     *
     * @return The refresh token string, or null if no auth exists
     */
    @Query("SELECT refreshToken FROM google_drive_auth WHERE id = 1")
    suspend fun getRefreshToken(): String?

    /**
     * Get the token expiration timestamp.
     * Used to determine if token needs refresh.
     *
     * @return The expiration timestamp in milliseconds, or 0 if no auth exists
     */
    @Query("SELECT expiresAt FROM google_drive_auth WHERE id = 1")
    suspend fun getTokenExpiresAt(): Long
}
