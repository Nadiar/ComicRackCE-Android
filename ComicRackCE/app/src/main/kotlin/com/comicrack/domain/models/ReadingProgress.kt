package com.comicrack.domain.models

/**
 * Domain model for reading progress tracking.
 *
 * Immutable representation of a user's progress through a comic book.
 */
data class ReadingProgress(
    val comicId: Long,
    val currentPage: Int,
    val totalPages: Int,
    val lastReadTime: Long,
    val isRead: Boolean,
) {
    /**
     * Calculate percentage complete (0-100).
     */
    fun percentComplete(): Int =
        when {
            totalPages == 0 -> 0
            else -> ((currentPage.toDouble() / totalPages) * 100).toInt()
        }

    /**
     * Check if reading is nearly complete (90%+ threshold).
     */
    fun isNearlyComplete(): Boolean = percentComplete() >= 90

    /**
     * Create a new progress with updated page number.
     */
    fun updatePage(newPage: Int): ReadingProgress =
        copy(
            currentPage = newPage,
            lastReadTime = System.currentTimeMillis(),
            isRead = newPage >= totalPages - 1,
        )

    /**
     * Mark this comic as fully read.
     */
    fun markAsRead(): ReadingProgress =
        copy(
            isRead = true,
            currentPage = totalPages - 1,
            lastReadTime = System.currentTimeMillis(),
        )

    /**
     * Mark this comic as unread.
     */
    fun markAsUnread(): ReadingProgress =
        copy(
            isRead = false,
            currentPage = 0,
            lastReadTime = System.currentTimeMillis(),
        )
}
