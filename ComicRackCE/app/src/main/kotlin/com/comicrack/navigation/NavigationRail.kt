package com.comicrack.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Main Navigation Rail component.
 *
 * Layers the rail content over a semi-transparent scrim.
 * Handles slide-in/out animations and swipe-to-close gestures.
 *
 * @param viewModel The state management delegate.
 * @param onSettingsClick Callback when settings is tapped.
 */
@Composable
fun NavigationRail(
    viewModel: NavigationRailViewModel,
    onSettingsClick: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        // Scrim
        NavigationRailScrim(
            isOpen = state.isOpen,
            onClose = { viewModel.closeRail() },
        )

        // Rail Content
        AnimatedVisibility(
            visible = state.isOpen,
            enter =
                slideInHorizontally(
                    initialOffsetX = { -it },
                    animationSpec = tween(durationMillis = 300),
                ),
            exit =
                slideOutHorizontally(
                    targetOffsetX = { -it },
                    animationSpec = tween(durationMillis = 200),
                ),
        ) {
            Surface(
                modifier =
                    Modifier
                        .width(256.dp)
                        .fillMaxHeight()
                        .windowInsetsPadding(WindowInsets.systemBars)
                        .draggable(
                            orientation = Orientation.Horizontal,
                            state =
                                rememberDraggableState { delta ->
                                    if (delta < -20) viewModel.closeRail()
                                },
                        ),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
                shadowElevation = 16.dp,
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    // Scrollable content area
                    Column(
                        modifier =
                            Modifier
                                .weight(1f)
                                .padding(vertical = 8.dp),
                    ) {
                        // Virtual Collections
                        state.virtualLibraries.forEach { item ->
                            NavigationRailItem(
                                item = item,
                                isSelected = state.selectedLibraryId == item.id,
                                onClick = { viewModel.selectLibrary(item.id) },
                                badgeContent = viewModel.formatBadgeCount(item.count),
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        // Physical Libraries
                        state.physicalLibraries.forEach { item ->
                            NavigationRailItem(
                                item = item,
                                isSelected = state.selectedLibraryId == item.id,
                                onClick = { viewModel.selectLibrary(item.id) },
                                badgeContent = viewModel.formatBadgeCount(item.count),
                            )
                        }
                    }

                    // Settings button (always visible at bottom, doesn't scroll)
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    NavigationRailSettingsButton(
                        onSettingsClick = {
                            viewModel.closeRail()
                            onSettingsClick()
                        },
                    )
                }
            }
        }
    }
}

/**
 * Semi-transparent background overlay that closes the rail when tapped.
 *
 * @param isOpen Whether the rail (and thus the scrim) is active.
 * @param onClose Callback to close the rail.
 */
@Composable
fun NavigationRailScrim(
    isOpen: Boolean,
    onClose: () -> Unit,
) {
    AnimatedVisibility(
        visible = isOpen,
        enter = fadeIn(animationSpec = tween(durationMillis = 200)),
        exit = fadeOut(animationSpec = tween(durationMillis = 200)),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.32f))
                    .clickable { onClose() },
        )
    }
}

/**
 * Settings button at the bottom of the navigation rail.
 * Always visible, doesn't scroll with library items.
 *
 * @param onSettingsClick Callback when settings icon is tapped (close rail first, then navigate to settings).
 */
@Composable
fun NavigationRailSettingsButton(onSettingsClick: () -> Unit) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(28.dp))
                .clickable { onSettingsClick() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(modifier = Modifier.width(16.dp))
        Icon(
            imageVector = Icons.Default.Settings,
            contentDescription = "Settings",
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.width(12.dp))
        androidx.compose.material3.Text(
            text = "Settings",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}
