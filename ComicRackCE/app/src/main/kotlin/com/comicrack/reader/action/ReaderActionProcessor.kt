package com.comicrack.reader.action

/**
 * Reader action processing and host UI callback contracts.
 *
 * This file defines `ReaderUiCallbacks` and `ReaderActionProcessor` which separate
 * gesture/key handling from UI execution and navigation. Prefer unit tests that
 * verify mapping from actions to ViewModel calls without UI dependencies.
 */

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

import android.content.Context
import android.os.VibrationEffect
import android.os.VibratorManager
import com.comicrack.data.preferences.AppPreferencesRepository
import com.comicrack.data.preferences.ReaderTapAction
import com.comicrack.reader.ComicViewerViewModel
import timber.log.Timber

/**
 * UI callbacks invoked by the reader when user-driven actions require Activity-/Fragment-level
 * responsibilities (navigation, dialogs, system UI). Implementations should perform UI work
 * (show dialogs, navigate, toggle controls) and avoid directly changing the reading state â€”
 * state changes should instead be performed via `ComicViewerViewModel`.
 */
interface ReaderUiCallbacks {
    /** Toggle visibility of reader chrome and controls (system bars, overlays). */
    fun onToggleUI()

    /** Open the reader settings UI. */
    fun onOpenSettings()

    /** Open the contextual menu for reader actions. */
    fun onOpenMenu()

    /** Finish the reader Activity / close the reader view. */
    fun onFinishActivity()

    /** Show bookmarks UI for the current comic. */
    fun onShowBookmarks()

    /** Show dialog or UI to jump to a specific page. */
    fun onShowGoToPage()

    /** Show zoom controls (if provided by the host UI). */
    fun onShowZoomControls()

    /**
     * Request the host to capture a snapshot/screenshot.
     *
     * @param context A Context suitable for UI operations; prefer Activity context when
     *                capturing views or interacting with the window.
     */
    fun onSnapshot(context: Context)
}

/**
 * ReaderActionProcessor
 *
 * Central dispatcher that maps configured reader tap/key/gamepad actions to ViewModel
 * updates and host UI callbacks. Keeps input-handling code separate from execution logic
 * so gestures/keys can be remapped without changing behavior implementation.
 *
 * @param context Application or Activity context used for system services and UI operations.
 * @param viewModel The `ComicViewerViewModel` used to mutate reader state (page, zoom, rotation).
 * @param callbacks Host UI callbacks implemented by the Activity/Fragment.
 */
class ReaderActionProcessor(
    private val context: Context,
    private val viewModel: ComicViewerViewModel,
    private val callbacks: ReaderUiCallbacks,
) {
    private val vibrator =
        try {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } catch (e: Exception) {
            null
        }

    /**
     * Execute a configured reader action.
     *
     * Maps `ReaderTapAction` values to ViewModel methods or host UI callbacks. Side-effects
     * (navigation, showing dialogs) are routed to `callbacks`, while reading-state changes
     * are performed on `viewModel`.
     *
     * @param action The `ReaderTapAction` to execute.
     * @param hapticEnabled When true, attempts a short haptic tick; this is best-effort and
     *                      will be ignored on devices without vibrator or when the call fails.
     */
    fun processAction(
        action: ReaderTapAction,
        hapticEnabled: Boolean = false,
    ) {
        if (hapticEnabled) {
            triggerHapticFeedback()
        }

        Timber.tag("ReaderActionProcessor").d("Processing action: $action")

        when (action) {
            // Navigation
            ReaderTapAction.NEXT_PAGE -> viewModel.nextPage()
            ReaderTapAction.PREVIOUS_PAGE -> viewModel.previousPage()
            ReaderTapAction.FIRST_PAGE -> viewModel.goToPage(0)
            ReaderTapAction.LAST_PAGE -> {
                val total = viewModel.totalPages.value
                if (total > 0) viewModel.goToPage(total - 1)
            }

            // Fallback behavior: toggle UI so the host can present navigation controls.
            // Playlist/part navigation is not implemented here.
            ReaderTapAction.NEXT_COMIC,
            ReaderTapAction.PREVIOUS_COMIC,
            ReaderTapAction.NEXT_PART,
            ReaderTapAction.PREVIOUS_PART,
            -> {
                callbacks.onToggleUI()
            }

            // UI Toggles
            ReaderTapAction.MENU -> callbacks.onToggleUI()
            ReaderTapAction.BOOKMARKS -> callbacks.onShowBookmarks()
            ReaderTapAction.GOTO_PAGE -> callbacks.onShowGoToPage()
            ReaderTapAction.SHOW_ZOOM -> callbacks.onShowZoomControls()

            // Settings / View Modes
            ReaderTapAction.TOGGLE_DOUBLE_PAGE,
            ReaderTapAction.TOGGLE_BRIGHTNESS,
            ReaderTapAction.ADJUST_COLORS,
            -> {
                // Log legacy actions
                Timber.tag("ReaderActionProcessor").d("Legacy action $action called (no-op)")
            }
            ReaderTapAction.SWITCH_FITTING_MODE -> viewModel.cycleFitMode()
            ReaderTapAction.SWITCH_ROTATION -> viewModel.rotatePage()

            ReaderTapAction.TOGGLE_LANDSCAPE_PORTRAIT -> {
                // Not implemented: orientation changes require Activity-level handling and user consent.
                Timber.tag("ReaderActionProcessor").w("Toggle Orientation not implemented yet")
            }

            // Zoom
            ReaderTapAction.ZOOM_IN -> viewModel.zoomIn()
            ReaderTapAction.ZOOM_OUT -> viewModel.zoomOut()

            // Misc
            ReaderTapAction.SNAPSHOT ->
                viewModel.capturePageScreenshot(
                    context,
                    ComicViewerViewModel.CaptureMode.SHARE,
                )
            ReaderTapAction.COPY_TO_CLIPBOARD ->
                viewModel.capturePageScreenshot(
                    context,
                    ComicViewerViewModel.CaptureMode.CLIPBOARD,
                )
            ReaderTapAction.BACK_TO_LIBRARY -> callbacks.onFinishActivity()

            // Unsupported actions: NEXT_BOOKMARK, PREVIOUS_BOOKMARK, RATE_BOOK, CHANGE_PAGE_TYPE
            // These are not yet implemented. See TECHNICAL-DEBT-TODO-TRACKING.md P3.1
            ReaderTapAction.NEXT_BOOKMARK,
            ReaderTapAction.PREVIOUS_BOOKMARK,
            ReaderTapAction.RATE_BOOK,
            ReaderTapAction.CHANGE_PAGE_TYPE,
            -> {
                Timber.tag("ReaderActionProcessor").d("Action $action not yet implemented")
            }

            ReaderTapAction.NONE -> { /* No-op */ }
        }
    }

    /** Trigger a short predefined haptic effect if supported; failures are logged and ignored. */
    private fun triggerHapticFeedback() {
        try {
            vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        } catch (e: Exception) {
            Timber.tag("ReaderActionProcessor").w(e, "Failed to vibrate")
        }
    }
}
