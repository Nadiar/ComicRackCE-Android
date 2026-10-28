package com.comicrack.settings.viewmodel

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comicrack.core.auth.GoogleAuthManager
import com.comicrack.core.auth.GoogleAuthState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for Google Drive authentication UI.
 *
 * Exposes [authState] for the Compose screen and handles
 * sign-in/sign-out actions.
 *
 * Phase 3: Google Drive Authentication (SRC-05-007 v2)
 */
@HiltViewModel
class GoogleAuthViewModel @Inject constructor(
    private val authManager: GoogleAuthManager,
) : ViewModel() {

    val authState: StateFlow<GoogleAuthState> = authManager.authState

    init {
        viewModelScope.launch {
            authManager.checkExistingAuth()
        }
    }

    fun getSignInIntent(): Intent = authManager.getSignInIntent()

    fun handleSignInResult(data: Intent?) {
        viewModelScope.launch {
            authManager.handleSignInResult(data)
        }
    }

    fun signOut() {
        viewModelScope.launch {
            authManager.signOut()
        }
    }
}
