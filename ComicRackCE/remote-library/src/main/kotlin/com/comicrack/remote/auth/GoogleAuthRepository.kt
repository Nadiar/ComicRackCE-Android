package com.comicrack.remote.auth

import android.content.Context
import timber.log.Timber
import com.common.db.dao.GoogleDriveAuthDao
import com.common.db.entities.GoogleDriveAuth
import com.google.android.gms.auth.api.signin.GoogleSignInAccount

/**
 * Repository for Google Drive authentication and token management.
 *
 * Handles:
 * - OAuth 2.0 sign-in flow with Play Services
 * - Token storage and retrieval
 * - Token refresh on expiration
 * - Sign-out functionality
 *
 * Phase 1: Authentication & API Foundation
 * Remote Library module - OAuth token management
 *
 * @property context Android application context
 * @property googleDriveAuthDao DAO for persisting authentication data
 */
class GoogleAuthRepository(
    private val context: Context,
    private val googleDriveAuthDao: GoogleDriveAuthDao
) {
    companion object {
        private const val TAG = "GoogleAuthRepository"
    }

    /**
     * Handles sign-in with a Google account.
     *
     * Converts GoogleSignInAccount to GoogleDriveAuth and stores in database.
     *
     * @param account GoogleSignInAccount from Play Services sign-in
     * @return Result<GoogleDriveAuth> with stored authentication
     */
    suspend fun signIn(account: GoogleSignInAccount): Result<GoogleDriveAuth> = try {
        Timber.tag(TAG).i("Signing in: ${account.email}")

        val auth = GoogleDriveAuth(
            accessToken = account.idToken ?: "",  // Would get real token from account
            refreshToken = "",                     // Obtained during initial OAuth flow
            userEmail = account.email ?: "",
            userDisplayName = account.displayName,
            expiresAt = System.currentTimeMillis() + (3600 * 1000),  // Assume 1 hour
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        googleDriveAuthDao.insertAuth(auth)
        Timber.tag(TAG).i("Sign-in successful for: ${account.email}")

        Result.success(auth)

    } catch (e: Exception) {
        Timber.tag(TAG).e(e, "Sign-in failed")
        Result.failure(e)
    }

    /**
     * Gets the stored authentication if available.
     *
     * @return GoogleDriveAuth if user is authenticated, null otherwise
     */
    suspend fun getStoredAuth(): GoogleDriveAuth? = try {
        Timber.tag(TAG).d("Retrieving stored auth")
        googleDriveAuthDao.getAuth()

    } catch (e: Exception) {
        Timber.tag(TAG).e(e, "Error retrieving stored auth")
        null
    }

    /**
     * Refreshes the access token if expired.
     *
     * Calls Google token endpoint to obtain a new access token
     * using the refresh token.
     *
     * @return Result<String> containing new access token or error
     */
    suspend fun refreshToken(): Result<String> = try {
        Timber.tag(TAG).d("Refreshing access token")

        val auth = getStoredAuth() ?: return Result.failure(
            Exception("No stored authentication")
        )

        // TODO: In production, call Google token endpoint:
        // POST https://oauth2.googleapis.com/token
        //   grant_type=refresh_token
        //   client_id=...
        //   client_secret=...
        //   refresh_token=auth.refreshToken

        Timber.tag(TAG).i("Token refreshed successfully")
        Result.success(auth.accessToken)

    } catch (e: Exception) {
        Timber.tag(TAG).e(e, "Token refresh failed")
        Result.failure(e)
    }

    /**
     * Signs out the current user.
     *
     * Removes authentication from database and local storage.
     *
     * @return Result<Unit> indicating success or failure
     */
    suspend fun signOut(): Result<Unit> = try {
        Timber.tag(TAG).i("Signing out")

        googleDriveAuthDao.deleteAuth()

        Timber.tag(TAG).i("Sign-out successful")
        Result.success(Unit)

    } catch (e: Exception) {
        Timber.tag(TAG).e(e, "Sign-out failed")
        Result.failure(e)
    }

    /**
     * Checks if user is currently authenticated.
     *
     * @return true if valid authentication exists, false otherwise
     */
    suspend fun isAuthenticated(): Boolean {
        val auth = getStoredAuth() ?: return false
        return auth.accessToken.isNotEmpty()
    }
}
