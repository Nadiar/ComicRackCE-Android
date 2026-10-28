package com.comicrack.data.preferences

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/**
 * PreferencesViewModelFactory â€” Factory for creating PreferencesViewModel instances
 *
 * **DEPRECATED**: Use Hilt @HiltViewModel instead.
 *
 * This factory is maintained for backward compatibility but is no longer needed.
 * Hilt automatically injects PreferencesViewModel with all dependencies.
 *
 * Legacy Usage (do not use):
 * ```kotlin
 * val factory = PreferencesViewModelFactory(repository, context)
 * val viewModel: PreferencesViewModel = viewModel(factory = factory)
 * ```
 *
 * New Usage (Hilt):
 * ```kotlin
 * val viewModel: PreferencesViewModel = viewModel()  // Automatic injection via Hilt
 * ```
 *
 * Phase 4, Task 4.7: Dependency Injection
 *
 * DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)
 */
@Deprecated("Use Hilt @HiltViewModel instead", replaceWith = ReplaceWith("viewModel()"))
class PreferencesViewModelFactory : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        throw UnsupportedOperationException(
            "PreferencesViewModelFactory is deprecated. Use Hilt @HiltViewModel instead. " +
                "Simply call viewModel() in your Composable to let Hilt inject dependencies.",
        )
}
