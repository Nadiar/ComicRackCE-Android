package com.comicrack.reader.state

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

/**
 * Represents the current reading mode of the viewer.
 */
enum class ReadingMode {
    Single, // Single page (Portrait default)
    Double, // Double page (Landscape default)
    Webtoon, // Vertical continuous scroll (Future)
}

/**
 * Encapsulates the complete state of the reading engine.
 *
 * logic: doublePageDisplay preference + current page index + total pages -> ReadingState
 */
data class ReadingState(
    val totalPages: Int = 0,
    val currentAbsoluteIndex: Int = 0,
    // Configuration
    val mode: ReadingMode = ReadingMode.Single,
    val isRtl: Boolean = false,
    // Derived State: Which pages are ACTUALLY visible?
    // Single: [idx]
    // Double LTR: [idx, idx+1]
    // Double RTL: [idx+1, idx]
    val visiblePageIndices: List<Int> = listOf(0),
) {
    /**
     * Determines the next logical page step based on current mode.
     */
    val stepSize: Int get() = if (mode == ReadingMode.Double) 2 else 1
}
