package com.comicrack.core.auth

import android.content.Context
import android.content.Intent
import com.comicrack.data.database.dao.GoogleDriveAuthDao
import com.comicrack.data.database.entities.GoogleDriveAuth
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.DriveScopes
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Authentication state for Google Drive integration.
 */
sealed interface GoogleAuthState {
    data object SignedOut : GoogleAuthState
    data object SigningIn : GoogleAuthState
    data class SignedIn(
        val email: String,
        val displayName: String?,
    ) : GoogleAuthState

    data class Error(val message: String) : GoogleAuthState
}

/**
 * Manages Google Sign-In for Drive API access.
 *
 * Handles the OAuth2 flow, persists tokens via [GoogleDriveAuthDao],
 * and provides a [GoogleSignInAccount] for constructing Drive API credentials.
 *
 * Uses Google Sign-In (play-services-auth) with DRIVE_READONLY scope
 * for reading comic files from Google Drive.
 */
@Singleton
class GoogleAuthManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val authDao: GoogleDriveAuthDao,
) {
    companion object {
        private const val TAG = "GoogleAuthManager"
    }

    private val _authState = MutableStateFlow<GoogleAuthState>(GoogleAuthState.SignedOut)
    val authState: StateFlow<GoogleAuthState> = _authState.asStateFlow()

    private val googleSignInClient: GoogleSignInClient by lazy {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_READONLY))
            .build()
        GoogleSignIn.getClient(context, gso)
    }

    /**
     * Check for existing sign-in on app start.
     * Restores auth state from the last signed-in account.
     */
    suspend fun checkExistingAuth() {
        val account = GoogleSignIn.getLastSignedInAccount(context)
        if (account != null && !account.isExpired) {
            _authState.value = GoogleAuthState.SignedIn(
                email = account.email ?: "",
                displayName = account.displayName,
            )
            persistAuth(account)
            Timber.tag(TAG).d("Restored existing auth: ${account.email}")
        } else {
            // Check database for stored auth
            val stored = authDao.getAuth()
            if (stored != null && stored.userEmail.isNotEmpty()) {
                // Stored auth exists but account may need re-auth
                Timber.tag(TAG).d("Stored auth found for ${stored.userEmail}, needs re-verification")
            }
            _authState.value = GoogleAuthState.SignedOut
        }
    }

    /**
     * Get the sign-in intent to launch with Activity Result API.
     */
    fun getSignInIntent(): Intent {
        _authState.value = GoogleAuthState.SigningIn
        return googleSignInClient.signInIntent
    }

    /**
     * Handle the result from the sign-in activity.
     *
     * @param data The intent data from the activity result
     * @return true if sign-in succeeded
     */
    suspend fun handleSignInResult(data: Intent?): Boolean {
        return try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.result
            if (account != null) {
                persistAuth(account)
                _authState.value = GoogleAuthState.SignedIn(
                    email = account.email ?: "",
                    displayName = account.displayName,
                )
                Timber.tag(TAG).i("Sign-in successful: ${account.email}")
                true
            } else {
                _authState.value = GoogleAuthState.Error("Sign-in returned null account")
                false
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Sign-in failed")
            _authState.value = GoogleAuthState.Error(e.message ?: "Sign-in failed")
            false
        }
    }

    /**
     * Sign out and clear stored credentials.
     */
    suspend fun signOut() {
        try {
            googleSignInClient.signOut()
            authDao.clearAuth()
            _authState.value = GoogleAuthState.SignedOut
            Timber.tag(TAG).i("Signed out successfully")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error during sign-out")
            // Clear local state regardless
            authDao.clearAuth()
            _authState.value = GoogleAuthState.SignedOut
        }
    }

    /**
     * Get the current signed-in Google account.
     * Returns null if not signed in.
     */
    fun getSignedInAccount(): GoogleSignInAccount? =
        GoogleSignIn.getLastSignedInAccount(context)

    /**
     * Check if the user has granted Drive read scope.
     */
    fun hasDriveScope(): Boolean {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return false
        return GoogleSignIn.hasPermissions(account, Scope(DriveScopes.DRIVE_READONLY))
    }

    private suspend fun persistAuth(account: GoogleSignInAccount) {
        val auth = GoogleDriveAuth(
            id = 1,
            accessToken = account.idToken ?: "",
            refreshToken = account.serverAuthCode ?: "",
            userEmail = account.email ?: "",
            userDisplayName = account.displayName,
            expiresAt = 0L, // GoogleSignIn manages token refresh automatically
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
        )
        authDao.insertAuth(auth)
    }
}
