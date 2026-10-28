package com.comicrack.domain.models

/**
 * Domain model for a library path.
 *
 * Represents a location (SAF URI or local path) containing comic files.
 */
data class Library(
    val id: Long,
    val path: String,
    val displayName: String,
    val createdAt: Long,
    val lastScannedAt: Long,
    val comicCount: Int,
    val isActive: Boolean,
) {
    /**
     * Check if this library needs scanning (never scanned or been a while).
     */
    fun needsScan(thresholdHours: Int = 24): Boolean {
        if (lastScannedAt == 0L) return true
        val hoursSinceLastScan = (System.currentTimeMillis() - lastScannedAt) / (1000 * 60 * 60)
        return hoursSinceLastScan >= thresholdHours
    }

    /**
     * Create an updated library with new scan results.
     */
    fun updateAfterScan(newComicCount: Int): Library =
        copy(
            lastScannedAt = System.currentTimeMillis(),
            comicCount = newComicCount,
        )

    /**
     * Toggle the active status.
     */
    fun toggleActive(): Library = copy(isActive = !isActive)
}
