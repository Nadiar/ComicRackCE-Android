package com.comicrack.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.StarRate
import androidx.compose.material.icons.filled.Storage
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comicrack.data.database.dao.ComicBookDao
import com.comicrack.data.database.dao.LibraryPathDao
import com.comicrack.data.database.entities.LibraryPath
import com.comicrack.data.preferences.AppPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Simple data class to hold four count values (all, new, unread, reading).
 */
data class CountsQuad(
    val all: Int,
    val new: Int,
    val unread: Int,
    val reading: Int,
)

/**
 * ViewModel for the Navigation Rail.
 */
@HiltViewModel
class NavigationRailViewModel
    @Inject
    constructor(
        private val comicDao: ComicBookDao,
        private val libraryPathDao: LibraryPathDao,
        private val appPreferencesRepository: AppPreferencesRepository,
    ) : ViewModel() {
        private val _isOpen = MutableStateFlow(false)
        private val _selectedLibraryId = MutableStateFlow("all_comics")

        private val countsFlow =
            combine(
                comicDao.observeAllComics(),
                comicDao.observeNewComics(),
                comicDao.observeUnreadComics(),
                comicDao.observeReadingComics(),
            ) { all, new, unread, reading ->
                CountsQuad(all.size, new.size, unread.size, reading.size)
            }

        val uiState: StateFlow<NavigationRailState> =
            combine(
                _isOpen,
                _selectedLibraryId,
                countsFlow,
                libraryPathDao.observeActiveLibraryPaths(),
                appPreferencesRepository.libraryAdditionalFolders,
            ) { isOpen, selectedId, counts, dbPaths, additionalFolders ->
                // Fix display names for existing paths (e.g., "primary:Comics2" -> "Comics2")
                dbPaths.forEach { path ->
                    if (path.displayName.contains(":")) {
                        val betterName = extractDisplayNameFromPath(path.path)
                        if (betterName != path.displayName) {
                            viewModelScope.launch {
                                path.displayName = betterName
                                libraryPathDao.updateLibraryPath(path)
                            }
                        }
                    }
                }

                // Ensure all additional folders have database entries
                additionalFolders.forEach { folderPath ->
                    viewModelScope.launch {
                        val existing = libraryPathDao.getLibraryPathByPath(folderPath)
                        if (existing == null) {
                            // Create database entry for additional folder
                            val displayName = extractDisplayNameFromPath(folderPath)
                            val newPath = LibraryPath(folderPath, displayName)
                            libraryPathDao.insertLibraryPath(newPath)
                        }
                    }
                }

                val virtualItems =
                    listOf(
                        LibraryItem("all_comics", "All Comics", Icons.Default.GridView, counts.all),
                        LibraryItem("new", "New Comics", Icons.Default.StarRate, counts.new),
                        LibraryItem("reading", "Reading Now", Icons.Default.History, counts.reading),
                        LibraryItem("unread", "Unread Comics", Icons.Default.BookmarkBorder, counts.unread),
                    )
                val physicalItems =
                    dbPaths.map { path ->
                        val icon = getStorageIcon(path.path)
                        LibraryItem("path_${path.id}", path.displayName, icon, path.comicCount)
                    }
                NavigationRailState(
                    isOpen = isOpen,
                    selectedLibraryId = selectedId,
                    virtualLibraries = virtualItems,
                    physicalLibraries = physicalItems,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = NavigationRailState(),
            )

        /**
         * Toggles the open/closed state of the navigation rail.
         */
        fun toggleRail() {
            _isOpen.update { !it }
        }

        /**
         * Closes the navigation rail.
         */
        fun closeRail() {
            _isOpen.value = false
        }

        /**
         * Selects a library item and closes the rail.
         *
         * @param id The ID of the library item to select.
         */
        fun selectLibrary(id: String) {
            _selectedLibraryId.value = id
            _isOpen.value = false
        }

        /**
         * Helper function to format the badge count.
         *
         * @param count The raw numeric count.
         * @return A string representation, capped at "999+".
         */
        fun formatBadgeCount(count: Int): String = if (count > 999) "999+" else count.toString()

        /**
         * Extract a human-readable display name from a path or URI.
         *
         * For SAF URIs: Returns the folder name (without "primary:" prefix)
         * For local paths: Returns the folder name
         * Fallback: Returns the full path
         */
        private fun extractDisplayNameFromPath(path: String): String =
            when {
                path.startsWith("content://") -> {
                    // SAF URI - decode and extract folder name
                    val decodedPath = java.net.URLDecoder.decode(path, "UTF-8")
                    // Extract the tree path (e.g., "primary:Comics2" from the URI)
                    val treePattern = """/tree/([^/]+)""".toRegex()
                    val treeMatch = treePattern.find(decodedPath)
                    if (treeMatch != null) {
                        val treePart = treeMatch.groupValues[1]
                        // Remove "primary:" or other storage prefixes
                        treePart.substringAfter(":").ifEmpty { treePart }
                    } else {
                        // Fallback: get the last path segment
                        val segment =
                            android.net.Uri
                                .parse(path)
                                .lastPathSegment ?: path
                        segment.substringAfter(":").ifEmpty { segment }
                    }
                }
                path.contains("/") -> {
                    // Local path - extract the folder name
                    path.trim('/').split('/').lastOrNull() ?: path
                }
                else -> path
            }

        /**
         * Get the appropriate icon for a library path based on storage type.
         *
         * - primary: = Phone storage (PhoneAndroid icon)
         * - secondary: = SD card (SdCard icon)
         * - USB drive/custom = Storage icon
         * - Local/other = Folder icon
         */
        private fun getStorageIcon(path: String): ImageVector =
            when {
                path.startsWith("content://") -> {
                    // SAF URI - check the storage prefix
                    val decodedPath = java.net.URLDecoder.decode(path, "UTF-8")
                    when {
                        decodedPath.contains("/primary:") -> Icons.Default.PhoneAndroid
                        decodedPath.contains("/secondary:") -> Icons.Default.SdCard
                        decodedPath.contains("usb") || decodedPath.contains("USB") -> Icons.Default.Storage
                        else -> Icons.Default.Storage // Generic external storage
                    }
                }
                else -> Icons.Default.Folder // Local filesystem
            }
    }
