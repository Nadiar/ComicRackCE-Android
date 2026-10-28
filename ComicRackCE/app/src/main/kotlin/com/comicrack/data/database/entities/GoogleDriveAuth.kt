package com.comicrack.data.database.entities

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for Google Drive OAuth credentials and token storage.
 *
 * Stores access tokens, refresh tokens, and user information for authenticated
 * Google Drive API access. Using singleton pattern (id: Int = 1) to ensure only
 * one active authentication state exists.
 *
 * Phase 1: Authentication & API Foundation
 * Remote Library module - Google Drive integration
 *
 * @property id Singleton primary key (always 1)
 * @property accessToken OAuth 2.0 access token for API requests
 * @property refreshToken OAuth 2.0 refresh token for renewing access token
 * @property userEmail User's Google account email address
 * @property userDisplayName User's display name from Google Profile
 * @property expiresAt Timestamp when access token expires (milliseconds since epoch)
 * @property createdAt Timestamp when credentials were first created
 * @property updatedAt Timestamp when credentials were last refreshed or updated
 */
@Entity(tableName = "google_drive_auth")
data class GoogleDriveAuth(
    @PrimaryKey
    val id: Int = 1, // Singleton pattern
    @ColumnInfo(defaultValue = "''")
    val accessToken: String = "",
    @ColumnInfo(defaultValue = "''")
    val refreshToken: String = "",
    @ColumnInfo(defaultValue = "''")
    val userEmail: String = "",
    val userDisplayName: String? = null,
    @ColumnInfo(defaultValue = "0")
    val expiresAt: Long = 0L, // Timestamp when token expires
    @ColumnInfo(defaultValue = "0")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0")
    val updatedAt: Long = System.currentTimeMillis(),
) {
    companion object {
        // Token expiration check constants
        private const val EXPIRATION_BUFFER_MINUTES = 5
        private const val SECONDS_PER_MINUTE = 60
        private const val MILLISECONDS_PER_SECOND = 1000
    }

    /**
     * Checks if the access token has expired or is about to expire.
     * Returns true if token expired or will expire within 5 minutes.
     */
    fun isTokenExpired(): Boolean {
        val expirationBuffer = EXPIRATION_BUFFER_MINUTES * SECONDS_PER_MINUTE * MILLISECONDS_PER_SECOND
        return System.currentTimeMillis() > (expiresAt - expirationBuffer)
    }

    override fun toString(): String =
        "GoogleDriveAuth(userEmail='$userEmail', userDisplayName='$userDisplayName', expiresAt=$expiresAt)"
}
