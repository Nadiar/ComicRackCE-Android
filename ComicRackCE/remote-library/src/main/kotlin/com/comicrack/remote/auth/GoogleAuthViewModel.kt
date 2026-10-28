package com.comicrack.remote.auth

import timber.log.Timber
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comicrack.data.preferences.AppPreferencesRepository
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for Google Drive sign-in flow and authentication state management.
 *
 * Manages:
 * - Authentication UI state (UNAUTHENTICATED, SIGNING_IN, AUTHENTICATED, ERROR)
 * - Error messages for display
 * - Sign-in and sign-out operations
 * - Checking current authentication status
 *
 * Uses Hilt dependency injection and Flow for reactive state updates.
 *
 * Phase 1: Authentication & API Foundation
 * Remote Library module - OAuth sign-in flow
 */
@HiltViewModel
class GoogleAuthViewModel @Inject constructor(
    private val authRepository: GoogleAuthRepository,
    private val preferenceRepository: AppPreferencesRepository
) : ViewModel() {

    companion object {
        private const val TAG = "GoogleAuthViewModel"
    }

    enum class AuthState {
        UNAUTHENTICATED,
        SIGNING_IN,
        AUTHENTICATED,
        ERROR
    }

    // Mutable internal state
    private val _authState = MutableStateFlow<AuthState>(AuthState.UNAUTHENTICATED)
    private val _errorMessage = MutableStateFlow<String?>(null)

    // Immutable public state flows
    val authState: StateFlow<AuthState> = _authState.asStateFlow()
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        // Check if already authenticated on ViewModel creation
        checkAuthenticationStatus()
    }

    /**
     * Handle Google Sign-In result.
     * Stores credentials and updates UI state.
     *
     * @param account Google Sign-In account from sign-in activity
     */
    fun signInWithGoogle(account: GoogleSignInAccount) {
        Timber.tag(TAG).i("Sign-in attempt for account: ${account.email}")
        viewModelScope.launch {
            try {
                _authState.value = AuthState.SIGNING_IN
                _errorMessage.value = null

                val result = authRepository.signIn(account)
                if (result.isSuccess) {
                    Timber.tag(TAG).i("Sign-in successful for ${account.email}")
                    _authState.value = AuthState.AUTHENTICATED
                    _errorMessage.value = null
                } else {
                    val errorMsg = result.exceptionOrNull()?.message ?: "Sign-in failed"
                    Timber.tag(TAG).e("Sign-in failed: $errorMsg")
                    _authState.value = AuthState.ERROR
                    _errorMessage.value = errorMsg
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Unexpected error during sign-in")
                _authState.value = AuthState.ERROR
                _errorMessage.value = e.message ?: "Unknown error occurred"
            }
        }
    }

    /**
     * Sign out and clear stored credentials.
     */
    fun signOut() {
        Timber.tag(TAG).i("Sign-out initiated")
        viewModelScope.launch {
            try {
                _authState.value = AuthState.SIGNING_IN
                _errorMessage.value = null

                val result = authRepository.signOut()
                if (result.isSuccess) {
                    Timber.tag(TAG).i("Sign-out successful")
                    _authState.value = AuthState.UNAUTHENTICATED
                    _errorMessage.value = null
                } else {
                    val errorMsg = result.exceptionOrNull()?.message ?: "Sign-out failed"
                    Timber.tag(TAG).e("Sign-out failed: $errorMsg")
                    _authState.value = AuthState.ERROR
                    _errorMessage.value = errorMsg
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Unexpected error during sign-out")
                _authState.value = AuthState.ERROR
                _errorMessage.value = e.message ?: "Unknown error occurred"
            }
        }
    }

    /**
     * Check current authentication status.
     * Called on ViewModel initialization and after sign-in/sign-out.
     */
    fun checkAuthenticationStatus() {
        Timber.tag(TAG).d("Checking authentication status")
        viewModelScope.launch {
            try {
                val isAuthenticated = authRepository.isAuthenticated()
                if (isAuthenticated) {
                    Timber.tag(TAG).i("User is authenticated")
                    _authState.value = AuthState.AUTHENTICATED
                    _errorMessage.value = null
                } else {
                    Timber.tag(TAG).i("User is not authenticated")
                    _authState.value = AuthState.UNAUTHENTICATED
                    _errorMessage.value = null
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error checking authentication status")
                _authState.value = AuthState.ERROR
                _errorMessage.value = e.message ?: "Failed to check authentication"
            }
        }
    }

    /**
     * Clear any error messages.
     */
    fun clearErrorMessage() {
        _errorMessage.value = null
    }
}
