package com.comicrack.settings.screens

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.comicrack.data.preferences.PreferencesViewModel
import com.comicrack.settings.catalog.Setting

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onSettingNavigate: (Setting) -> Unit = {},
) {
    /**
     * Settings search screen.
     *
     * Notes:
     * - Uses `viewModel.catalog.search` to perform catalog lookups. If the catalog
     *   grows large consider adding debounce, incremental search, or indexing to
     *   avoid expensive work on the UI thread.
     * - Recent searches are persisted in preferences; keep that behavior when
     *   refactoring so user history is preserved.
     *
     * @param onBack Back navigation callback.
     * @param onSettingNavigate Called when a search result is selected to navigate
     *   into the appropriate setting editor.
     */
    val viewModel: PreferencesViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }

    // Search Results
    val searchResults =
        remember(searchQuery) {
            if (searchQuery.isBlank()) emptyList() else viewModel.catalog.search(searchQuery)
        }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            // Android Settings-style Search Bar (Top App Bar slot)
            SearchBar(
                inputField = {
                    SearchBarDefaults.InputField(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        onSearch = {
                            if (it.isNotBlank()) {
                                viewModel.addRecentSearch(it)
                            }
                        },
                        expanded = false, // Docked mode
                        onExpandedChange = { },
                        placeholder = { Text("Search settings") },
                        leadingIcon = {
                            IconButton(onClick = onBack) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                )
                            }
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                        },
                    )
                },
                expanded = false,
                onExpandedChange = { },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = MaterialTheme.shapes.extraLarge,
                colors =
                    SearchBarDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                content = {},
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // State: Empty Query -> History
                if (searchQuery.isBlank()) {
                    if (state.recentSearches.isNotEmpty()) {
                        item {
                            Text(
                                text = "Recent",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                            )
                        }

                        items(state.recentSearches.toList().reversed()) { historyItem ->
                            ListItem(
                                headlineContent = { Text(historyItem) },
                                leadingContent = {
                                    Icon(
                                        Icons.Default.History,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                                trailingContent = {
                                    IconButton(onClick = { viewModel.removeRecentSearch(historyItem) }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Remove")
                                    }
                                },
                                modifier =
                                    Modifier
                                        .clickable { searchQuery = historyItem }
                                        .fillMaxWidth(),
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            )
                        }

                        item {
                            TextButton(
                                onClick = { viewModel.clearRecentSearches() },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Clear history")
                            }
                        }
                    }
                } else {
                    // State: Results
                    if (searchResults.isEmpty()) {
                        item {
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "No matches found",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    } else {
                        items(searchResults) { setting ->
                            ListItem(
                                headlineContent = { Text(setting.title) },
                                supportingContent = {
                                    Text(
                                        "${setting.category.name}" +
                                            (if (setting.subcategory != null) " > ${setting.subcategory}" else ""),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                },
                                modifier =
                                    Modifier
                                        .clickable {
                                            viewModel.addRecentSearch(searchQuery)
                                            onSettingNavigate(setting)
                                        },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}
