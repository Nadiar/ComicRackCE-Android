package com.comicrack.data.preferences

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

/**
 * HardwareKeyBindings â€” Maps hardware key codes and buttons to reader actions.
 *
 * Contains default mappings for volume keys, keyboard, gamepad, and other inputs.
 * This file was annotated during a documentation-only pass (docfix/pilot-comments).
 * No behavioral changes were made.
 */

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class HardwareKeyBindings(
    // Volume Keys
    val volumeUpAction: ReaderTapAction = ReaderTapAction.PREVIOUS_PAGE,
    val volumeUpLongPressAction: ReaderTapAction = ReaderTapAction.NONE, // Renamed from DoubleTap
    val volumeDownAction: ReaderTapAction = ReaderTapAction.NEXT_PAGE,
    val volumeDownLongPressAction: ReaderTapAction = ReaderTapAction.NONE, // Renamed from DoubleTap
    // Keyboard (Map of Android KeyCode Int -> Action)
    // Default mappings can be handled here or in the repository defaults
    val keyboardBindings: Map<Int, ReaderTapAction> =
        mapOf(
            android.view.KeyEvent.KEYCODE_DPAD_UP to ReaderTapAction.PREVIOUS_PART,
            android.view.KeyEvent.KEYCODE_DPAD_DOWN to ReaderTapAction.NEXT_PART,
            android.view.KeyEvent.KEYCODE_DPAD_LEFT to ReaderTapAction.PREVIOUS_PAGE,
            android.view.KeyEvent.KEYCODE_DPAD_RIGHT to ReaderTapAction.NEXT_PAGE,
            android.view.KeyEvent.KEYCODE_SPACE to ReaderTapAction.NEXT_PAGE,
            android.view.KeyEvent.KEYCODE_PAGE_UP to ReaderTapAction.PREVIOUS_PAGE,
            android.view.KeyEvent.KEYCODE_PAGE_DOWN to ReaderTapAction.NEXT_PAGE,
            android.view.KeyEvent.KEYCODE_ESCAPE to ReaderTapAction.BACK_TO_LIBRARY,
        ),
    // Gamepad (Map of Android KeyCode Int -> Action)
    val gamepadBindings: Map<Int, ReaderTapAction> =
        mapOf(
            android.view.KeyEvent.KEYCODE_DPAD_UP to ReaderTapAction.PREVIOUS_PART,
            android.view.KeyEvent.KEYCODE_DPAD_DOWN to ReaderTapAction.NEXT_PART,
            android.view.KeyEvent.KEYCODE_DPAD_LEFT to ReaderTapAction.PREVIOUS_PAGE,
            android.view.KeyEvent.KEYCODE_DPAD_RIGHT to ReaderTapAction.NEXT_PAGE,
            android.view.KeyEvent.KEYCODE_BUTTON_A to ReaderTapAction.NEXT_PAGE,
            android.view.KeyEvent.KEYCODE_BUTTON_B to ReaderTapAction.PREVIOUS_PAGE,
        ),
    // Bluetooth / Other
    val bluetoothBindings: Map<Int, ReaderTapAction> = emptyMap(),
)
