package com.comicrack.navigation

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Represents an individual library entry in the navigation rail.
 *
 * @property id Unique identifier for the library item.
 * @property displayName Human-readable label shown in the rail.
 * @property icon Material Design icon associated with this item.
 * @property count Numeric count displayed in the badge.
 */
data class LibraryItem(
    val id: String,
    val displayName: String,
    val icon: ImageVector,
    val count: Int,
)

/**
 * UI State for the Navigation Rail.
 *
 * @property isOpen Whether the rail is currently visible.
 * @property selectedLibraryId The ID of the currently selected library/collection.
 * @property libraries The list of library items to display.
 */
data class NavigationRailState(
    val isOpen: Boolean = false,
    val selectedLibraryId: String = "all_comics",
    val virtualLibraries: List<LibraryItem> = emptyList(),
    val physicalLibraries: List<LibraryItem> = emptyList(),
)
