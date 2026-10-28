package com.comicrack.reader.input

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

import android.view.KeyEvent
import com.comicrack.data.preferences.AppPreferencesRepository
import com.comicrack.data.preferences.ReaderTapAction
import timber.log.Timber

/**
 * ReaderInputController â€” hardware key event handler for reader UI.
 *
 * Handles volume keys (with long-press semantics), D-pad and other hardware
 * buttons and maps them to configured `ReaderTapAction` values or direct
 * processor calls.
 *
 * Implementation notes:
 * - Volume keys use a tracked long-press flow: `onKeyDown` starts tracking,
 *   `onKeyLongPress` executes the long-press action, and `onKeyUp` executes
 *   the short-press action when no long-press was registered.
 * - Hardware keys do not trigger haptic feedback by default to avoid noise during
 *   long-running reads; haptic is controlled by the action implementation.
 *
 * Testing and behavior:
 * - Add unit tests that simulate long-press vs single-press sequences to ensure
 *   the correct action mapping and long-press suppression behavior.
 * - Ensure behavior is consistent across devices with different key event timing.
 *
 * @param processor The [ReaderActionProcessor] used to execute reader actions.
 * @param bindings Hardware key bindings configuration (maps key codes to actions).
 */
class ReaderInputController(
    private val processor: com.comicrack.reader.action.ReaderActionProcessor,
    private val bindings: com.comicrack.data.preferences.HardwareKeyBindings,
) {
    // Track long press state
    private var isLongPress = false

    fun onKeyDown(
        keyCode: Int,
        event: KeyEvent?,
    ): Boolean {
        // Only handle Volume keys with special Long Press logic
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            event?.startTracking()
            return true
        }

        // For other keys, execute immediately (Legacy/Direct behavior)
        return handleDirectAction(keyCode)
    }

    fun onKeyLongPress(
        keyCode: Int,
        _event: KeyEvent?,
    ): Boolean {
        val action =
            when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> bindings.volumeUpLongPressAction
                KeyEvent.KEYCODE_VOLUME_DOWN -> bindings.volumeDownLongPressAction
                else -> ReaderTapAction.NONE
            }

        if (action != ReaderTapAction.NONE) {
            Timber.tag("ReaderInput").d("Long Press -> $action")
            isLongPress = true
            executeAction(action)
            return true
        }
        return false
    }

    fun onKeyUp(
        keyCode: Int,
        _event: KeyEvent?,
    ): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (isLongPress) {
                isLongPress = false
                return true
            }

            // Single Press Action
            val action =
                when (keyCode) {
                    KeyEvent.KEYCODE_VOLUME_UP -> bindings.volumeUpAction
                    KeyEvent.KEYCODE_VOLUME_DOWN -> bindings.volumeDownAction
                    else -> ReaderTapAction.NONE
                }

            Timber.tag("ReaderInput").d("Single Press -> $action")
            executeAction(action)
            return true
        }
        return false
    }

    private fun handleDirectAction(keyCode: Int): Boolean {
        val action = mapKeyCodeToAction(keyCode)
        if (action == ReaderTapAction.NONE) return false
        executeAction(action)
        return true
    }

    private fun mapKeyCodeToAction(keyCode: Int): ReaderTapAction =
        bindings.keyboardBindings[keyCode]
            ?: bindings.gamepadBindings[keyCode]
            ?: ReaderTapAction.NONE

    private fun executeAction(action: ReaderTapAction) {
        // Hardware keys generally don't need tap feedback unless requested
        processor.processAction(action, hapticEnabled = false)
    }
}
