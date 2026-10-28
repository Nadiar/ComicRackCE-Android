package com.comicrack.reader.gesture

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs

/**
 * Layer that intercepts touch events and dispatches high-level gestures.
 *
 * The implementation composes two `pointerInput` handlers: a conditional
 * horizontal-drag detector (when `swipeEnabled` is true) and a tap/long-press
 * detector. The horizontal-drag detector runs first so that swipes are given
 * priority; taps still work because tap gestures come from the second detector.
 *
 * - Single Tap
 * - Long Press
 * - Horizontal Swipe (if enabled)
 *
 * @param swipeEnabled Whether horizontal swipes should be detected
 * @param onSingleTap Callback for single tap (x, y)
 * @param onLongPress Callback for long press (x, y)
 * @param onSwipeLeft Callback for left swipe (Next Page usually)
 * @param onSwipeRight Callback for right swipe (Previous Page usually)
 * @param content Child content
 */
@Composable
fun GestureLayer(
    modifier: Modifier = Modifier,
    swipeEnabled: Boolean = false,
    isRtl: Boolean = false,
    onSingleTap: (Offset) -> Unit = {},
    onLongPress: (Offset) -> Unit = {},
    onSwipeLeft: () -> Unit = {},
    onSwipeRight: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .pointerInput(swipeEnabled) {
                    if (swipeEnabled) {
                        var totalDrag = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { totalDrag = 0f },
                            onDragEnd = {
                                if (abs(totalDrag) > 50) { // Threshold
                                    if (totalDrag < 0) {
                                        if (isRtl) onSwipeRight() else onSwipeLeft()
                                    } else {
                                        if (isRtl) onSwipeLeft() else onSwipeRight()
                                    }
                                }
                                totalDrag = 0f
                            },
                            onDragCancel = { totalDrag = 0f },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                totalDrag += dragAmount
                            },
                        )
                    }
                }.pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { offset -> onSingleTap(offset) },
                        onLongPress = { offset -> onLongPress(offset) },
                    )
                },
    ) {
        content()
    }
}
