package com.comicrack.reader.ui

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * PageTransitionAnimations - Provides animation effects for page transitions.
 *
 * Features:
 * - Fade animation: Smooth opacity transition
 * - Slide animation: Horizontal slide based on navigation direction
 * - Optional: Can be disabled via pageTransitionsEnabled preference
 *
 * Phase 9.1: Wire to pageTransitionsEnabled preference
 *
 * Usage & testing:
 * - Provide a way to disable animations for users preferring reduced motion; add
 *   tests that verify UI remains usable when animations are turned off.
 * - Validate that animations do not cause layout thrash or frame drops on low-end devices.
 *
 * Usage:
 * ```kotlin
 * AnimatedPageContent(
 *     isVisible = true,
 *     useAnimation = pageTransitionsEnabled,
 *     modifier = Modifier.fillMaxSize()
 * ) {
 *     // Your page content
 * }
 * ```
 */
@Composable
fun AnimatedPageContent(
    isVisible: Boolean,
    useAnimation: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (useAnimation) {
        // With animation: fade + slide
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn() + slideInHorizontally(initialOffsetX = { it / 2 }),
            exit = fadeOut() + slideOutHorizontally(targetOffsetX = { -it / 2 }),
            modifier = modifier,
        ) {
            content()
        }
    } else {
        // Without animation: just show/hide
        if (isVisible) {
            content()
        }
    }
}

/**
 * FadePageTransition - Simpler fade-only animation for page transitions.
 *
 * Use this for smoother, less intrusive transitions during reading.
 * Disables slide to keep focus on the page content.
 */
@Composable
fun FadePageTransition(
    isVisible: Boolean,
    useAnimation: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (useAnimation) {
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = modifier,
        ) {
            content()
        }
    } else {
        if (isVisible) {
            content()
        }
    }
}
