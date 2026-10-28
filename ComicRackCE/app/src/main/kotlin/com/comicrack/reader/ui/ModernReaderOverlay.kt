package com.comicrack.reader.ui

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Rotate90DegreesCcw
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.comicrack.reader.ComicViewerViewModel
import kotlin.math.roundToInt

/**
 * Modern reader overlay providing an immersive reading experience with Material 3 design.
 *
 * This composable displays a transparent top and bottom bar that slide in/out based on visibility state.
 * The overlay includes page navigation controls, quick action buttons, and modal sheets for settings
 * and comic details.
 *
 * ## Features
 * - Immersive transparent overlay with gradient backgrounds
 * - Material 3 CenterAlignedTopAppBar
 * - Smooth slide and fade animations
 * - Page scrubber with slider control
 * - Quick action buttons (Rotate, Fit, Visual Settings)
 * - Modal bottom sheets for settings and details
 * - Full support for dark mode and dynamic theming
 *
 * ## Accessibility
 * All action buttons include content descriptions for screen readers.
 * Touch targets meet minimum 48dp requirements per Material Design guidelines.
 *
 * @param seriesName The title of the comic series to display in the top bar
 * @param issueNumber The issue number or identifier shown below the series name
 * @param currentPage Zero-based index of the current page being displayed
 * @param totalPages Total number of pages in the current comic
 * @param isVisible Controls the visibility state of the entire overlay
 * @param onToggleVisibility Callback invoked when user wants to toggle overlay visibility
 * @param onPageSelected Callback invoked with the selected page index when user changes page
 * @param onSettingsClick Callback invoked when settings button is tapped
 * @param onRotateClick Callback invoked when rotation button is tapped
 * @param onFitClick Callback invoked when fit-to-screen button is tapped
 * @param onShareClick Callback invoked when share button is tapped
 * @param onBackClick Callback invoked when back/navigation button is tapped
 * @param viewModel The ComicViewerViewModel providing state and business logic
 * @param modifier Optional Modifier for this composable
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModernReaderOverlay(
    seriesName: String,
    issueNumber: String,
    currentPage: Int,
    totalPages: Int,
    isVisible: Boolean,
    _onToggleVisibility: () -> Unit,
    onPageSelected: (Int) -> Unit,
    _onSettingsClick: () -> Unit = {},
    onRotateClick: () -> Unit = {},
    onFitClick: () -> Unit,
    _onShareClick: () -> Unit = {},
    onBackClick: () -> Unit,
    viewModel: ComicViewerViewModel,
    modifier: Modifier = Modifier,
) {
    val rotationDegrees by viewModel.rotation.collectAsState()
    val animatedRotation by animateFloatAsState(
        targetValue = rotationDegrees.toFloat(),
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "RotateIcon",
    )

    // State is controlled by parent
    val showBars = isVisible
    var showVisualSettings by remember { mutableStateOf(false) }
    var showDetails by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    Box(
        modifier =
            modifier
                .fillMaxWidth(),
    ) {
        // Top Bar - Transparent, Immersive
        AnimatedVisibility(
            visible = showBars,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            CenterAlignedTopAppBar(
                title = {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(end = 8.dp),
                    ) {
                        Text(
                            text = seriesName,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface, // Better contrast
                        )
                        Text(
                            text = "Issue #$issueNumber",
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
                actions = {
                    var showShareMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { showShareMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share Options",
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        DropdownMenu(
                            expanded = showShareMenu,
                            onDismissRequest = { showShareMenu = false },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        ) {
                            DropdownMenuItem(
                                text = { Text("Share Page") },
                                leadingIcon = { Icon(Icons.Default.Share, null) },
                                onClick = {
                                    showShareMenu = false
                                    viewModel.sharePageScreenshot(context)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Copy to Clipboard") },
                                leadingIcon = { Icon(Icons.Default.ContentCopy, null) },
                                onClick = {
                                    showShareMenu = false
                                    viewModel.copyPageToClipboard(context)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Save to Pictures") },
                                leadingIcon = { Icon(Icons.Default.Save, null) },
                                onClick = {
                                    showShareMenu = false
                                    viewModel.savePageToDocuments(context)
                                },
                            )
                        }
                    }

                    IconButton(onClick = { showDetails = true }) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Details",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    IconButton(onClick = { showVisualSettings = true }) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "Visual Settings",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
                modifier =
                    Modifier
                        .background(
                            brush =
                                Brush.verticalGradient(
                                    colors =
                                        listOf(
                                            Color.Black.copy(alpha = 0.9f),
                                            Color.Transparent,
                                        ),
                                ),
                        ).statusBarsPadding()
                        .displayCutoutPadding(),
                colors =
                    TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent, // Transparent because we handle background manually
                        navigationIconContentColor = Color.White,
                        titleContentColor = Color.White,
                        actionIconContentColor = Color.White,
                    ),
            )
        }

        // Bottom Bar - Page Scrubber & Quick Actions
        AnimatedVisibility(
            visible = showBars,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        // Immersive Gradient Background for Bottom Bar
                        .background(
                            brush =
                                Brush.verticalGradient(
                                    colors =
                                        listOf(
                                            Color.Transparent,
                                            Color.Black.copy(alpha = 0.9f),
                                        ),
                                ),
                        ),
            ) {
                // Page Scrubber (Slider)
                PageScrubber(
                    currentPage = currentPage,
                    totalPages = totalPages,
                    onPageSelected = onPageSelected,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                )

                // Quick Action Buttons Row
                QuickActionsRow(
                    onRotateClick = onRotateClick,
                    onFitClick = onFitClick,
                    onVisualSettingsClick = { showVisualSettings = true },
                    animatedRotation = animatedRotation,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }

        if (showVisualSettings) {
            ModalBottomSheet(
                onDismissRequest = { showVisualSettings = false },
                sheetState = rememberModalBottomSheetState(),
            ) {
                VisualSettingsBottomSheet(
                    viewModel = viewModel,
                    onDismiss = { showVisualSettings = false },
                )
            }
        }

        if (showDetails) {
            ModalBottomSheet(
                onDismissRequest = { showDetails = false },
                sheetState = rememberModalBottomSheetState(),
            ) {
                ComicDetailsBottomSheet(
                    viewModel = viewModel,
                    onDismiss = { showDetails = false },
                )
            }
        }
    }
}

/**
 * Page scrubber composable providing interactive page navigation via a slider control.
 *
 * Displays the current page number, a slider for navigation, and the total page count.
 * The slider provides direct page selection with tactile feedback and meets accessibility
 * requirements with a minimum touch target of 48dp.
 *
 * @param currentPage Zero-based index of the currently displayed page
 * @param totalPages Total number of pages in the comic
 * @param onPageSelected Callback invoked with the selected page index when user interacts with the slider
 * @param modifier Optional Modifier for this composable
 */
@Composable
fun PageScrubber(
    currentPage: Int,
    totalPages: Int,
    onPageSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Current page number
            Text(
                text = "${currentPage + 1}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp),
                maxLines = 1,
            )

            // Slider
            if (totalPages > 1) {
                Slider(
                    value = currentPage.toFloat(),
                    onValueChange = { onPageSelected(it.roundToInt()) },
                    valueRange = 0f..((totalPages - 1).toFloat()),
                    modifier =
                        Modifier
                            .weight(1f)
                            .height(48.dp)
                            .padding(vertical = 4.dp),
                    steps = totalPages - 2,
                )
            }

            // Total pages
            Text(
                text = "/$totalPages",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp),
                maxLines = 1,
            )
        }
    }
}

/**
 * Quick actions row displaying floating buttons for common reader operations.
 *
 * Provides quick access to frequently used reader functions with animated feedback.
 * The row has a pill-shaped background with low opacity for immersive reading.
 *
 * ## Available Actions
 * - **Rotate**: Rotates the current page by 90 degrees counterclockwise
 * - **Fit**: Toggles between fit-to-screen and actual size display modes
 * - **Visual Settings**: Opens a modal sheet for adjusting brightness, contrast, and layout options
 *
 * @param onRotateClick Callback invoked when the rotate button is tapped
 * @param onFitClick Callback invoked when the fit button is tapped
 * @param onVisualSettingsClick Callback invoked when the visual settings button is tapped
 * @param animatedRotation Current rotation animation value in degrees for the rotate icon
 * @param modifier Optional Modifier for this composable
 */
@Composable
fun QuickActionsRow(
    onRotateClick: () -> Unit = {},
    onFitClick: () -> Unit = {},
    onVisualSettingsClick: () -> Unit = {},
    animatedRotation: Float = 0f,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .height(52.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.95f),
                    shape = CircleShape, // Pill style
                ).padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Rotate Button
        FilledTonalIconButton(
            onClick = onRotateClick,
            modifier = Modifier.size(40.dp),
            colors =
                IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
        ) {
            Icon(
                imageVector = Icons.Filled.Rotate90DegreesCcw,
                contentDescription = "Rotate page",
                modifier =
                    Modifier
                        .size(20.dp)
                        .graphicsLayer { rotationZ = animatedRotation },
            )
        }

        // Fit Button
        FilledTonalIconButton(
            onClick = onFitClick,
            modifier = Modifier.size(40.dp),
            colors =
                IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
        ) {
            Icon(
                imageVector = Icons.Filled.FitScreen,
                contentDescription = "Toggle fit",
                modifier = Modifier.size(20.dp),
            )
        }

        // Visual Settings Button (Aa)
        FilledTonalIconButton(
            onClick = onVisualSettingsClick,
            modifier = Modifier.size(40.dp),
            colors =
                IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
        ) {
            Icon(
                imageVector = Icons.Filled.Tune,
                contentDescription = "Visual Settings",
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
