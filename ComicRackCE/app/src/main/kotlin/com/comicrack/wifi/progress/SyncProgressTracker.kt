package com.comicrack.wifi.progress

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks sync progress for UI updates and notifications.
 */
@Singleton
class SyncProgressTracker
    @Inject
    constructor() {
        companion object {
            private const val PROGRESS_PERCENTAGE_MULTIPLIER = 100
        }
        private val _progress = MutableStateFlow(SyncProgress())
        val progress: StateFlow<SyncProgress> = _progress.asStateFlow()

        /**
         * Update progress percentage.
         */
        fun updateProgress(percent: Int) {
            _progress.value = _progress.value.copy(percent = percent)
        }

        /**
         * Update progress with book information.
         */
        fun updateProgress(
            current: Int,
            total: Int,
            currentBook: String = "",
        ) {
            val percent = if (total > 0) (current * PROGRESS_PERCENTAGE_MULTIPLIER) / total else 0
            _progress.value =
                SyncProgress(
                    percent = percent,
                    currentBook = currentBook,
                    booksProcessed = current,
                    totalBooks = total,
                )
        }

        /**
         * Mark sync as complete.
         */
        fun complete() {
            _progress.value =
                _progress.value.copy(
                    percent = 100,
                    isComplete = true,
                )
        }

        /**
         * Reset progress for new sync session.
         */
        fun reset() {
            _progress.value = SyncProgress()
        }
    }

/**
 * Data class representing current sync progress.
 */
data class SyncProgress(
    val percent: Int = 0,
    val currentBook: String = "",
    val booksProcessed: Int = 0,
    val totalBooks: Int = 0,
    val isComplete: Boolean = false,
) {
    /**
     * True if sync is actively running (started but not complete)
     */
    val isActive: Boolean get() = percent > 0 && !isComplete
}
