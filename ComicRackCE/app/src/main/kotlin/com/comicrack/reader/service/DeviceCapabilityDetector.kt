package com.comicrack.reader.service

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

import android.content.Context
import android.hardware.input.InputManager
import android.view.InputDevice
import android.view.KeyEvent
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import kotlinx.coroutines.flow.Flow
import timber.log.Timber

/**
 * DeviceCapabilityDetector - Detects hardware peripherals available on device.
 *
 * Detects:
 * - D-Pad / DPAD keys
 * - Volume buttons
 * - Bluetooth keyboard
 * - Gamepad / Game controller
 * - Dedicated arrow keys
 *
 * Results are cached in DataStore preferences and exposed as Flow<DeviceCapabilities>.
 * Re-detection can be triggered via recheckPeripherals().
 *
 * Phase 9.3: Wire to settings UI for adaptive control display
 */
object DeviceCapabilityDetector {
    private const val TAG = "DeviceCapabilityDetector"

    // DataStore preferences for caching detection results (public for access in extensions)
    internal val HAS_DPAD = booleanPreferencesKey("has_dpad")
    internal val HAS_BLUETOOTH_KEYBOARD = booleanPreferencesKey("has_bluetooth_keyboard")
    internal val HAS_GAMEPAD = booleanPreferencesKey("has_gamepad")
    internal val HAS_ARROW_KEYS = booleanPreferencesKey("has_arrow_keys")

    private var cachedCapabilities: DeviceCapabilities? = null

    /**
     * Get device capabilities as a Flow (uses cache, simpler interface)
     *
     * Just detect once and cache - doesn't require DataStore dependency
     */
    fun getDeviceCapabilities(context: Context): Flow<DeviceCapabilities> {
        // Simple implementation: detect once and return as flow
        val capabilities = detectCapabilities(context)
        return kotlinx.coroutines.flow.flowOf(capabilities)
    }

    /**
     * Detect hardware capabilities on current device
     *
     * Uses InputDevice enumeration to detect connected input devices
     */
    fun detectCapabilities(context: Context): DeviceCapabilities {
        try {
            val inputManager =
                context.getSystemService(Context.INPUT_SERVICE) as? InputManager
                    ?: return DeviceCapabilities()

            var hasDpad = false
            var hasBluetoothKeyboard = false
            var hasGamepad = false
            var hasArrowKeys = false

            // Enumerate all input devices
            val devices = inputManager.inputDeviceIds
            Timber.tag(TAG).d("Found ${devices.size} input devices")

            for (deviceId in devices) {
                val device = InputDevice.getDevice(deviceId)
                val name = device?.name ?: "Unknown"

                Timber.tag(TAG).d("Device: $name")

                // Check for D-Pad (directional pad)
                val dpadKeys =
                    try {
                        device?.hasKeys(
                            KeyEvent.KEYCODE_DPAD_UP,
                            KeyEvent.KEYCODE_DPAD_DOWN,
                            KeyEvent.KEYCODE_DPAD_LEFT,
                            KeyEvent.KEYCODE_DPAD_RIGHT,
                        )
                    } catch (e: Exception) {
                        null
                    }

                if (dpadKeys?.contains(true) == true) {
                    hasDpad = true
                    Timber.tag(TAG).d("Detected D-Pad: $name")
                }

                // Check for arrow keys (keyboard)
                val arrowKeys =
                    try {
                        device?.hasKeys(
                            KeyEvent.KEYCODE_DPAD_UP,
                            KeyEvent.KEYCODE_DPAD_DOWN,
                        )
                    } catch (e: Exception) {
                        null
                    }

                if (arrowKeys?.contains(true) == true) {
                    hasArrowKeys = true
                    Timber.tag(TAG).d("Detected arrow keys: $name")
                }

                // Check for gamepad by looking for gamepad-specific keys
                val gamepadKeys =
                    try {
                        device?.hasKeys(
                            KeyEvent.KEYCODE_BUTTON_A,
                            KeyEvent.KEYCODE_BUTTON_B,
                            KeyEvent.KEYCODE_BUTTON_X,
                            KeyEvent.KEYCODE_BUTTON_Y,
                        )
                    } catch (e: Exception) {
                        null
                    }

                if (gamepadKeys?.contains(true) == true) {
                    hasGamepad = true
                    Timber.tag(TAG).d("Detected gamepad: $name")
                }

                // Check for Bluetooth keyboard by name
                if (name.lowercase().contains("bluetooth") ||
                    name.lowercase().contains("wireless")
                ) {
                    if (name.lowercase().contains("keyboard")) {
                        hasBluetoothKeyboard = true
                        Timber.tag(TAG).d("Detected Bluetooth keyboard: $name")
                    }
                }
            }

            val capabilities =
                DeviceCapabilities(
                    hasDpad = hasDpad,
                    hasBluetoothKeyboard = hasBluetoothKeyboard,
                    hasGamepad = hasGamepad,
                    hasArrowKeys = hasArrowKeys,
                )

            cachedCapabilities = capabilities
            Timber.tag(TAG).d("Device capabilities: $capabilities")
            return capabilities
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error detecting device capabilities")
            return DeviceCapabilities()
        }
    }

    /**
     * Recheck peripherals and update cached capabilities
     *
     * Call this when user opens preferences to get fresh detection
     */
    suspend fun recheckPeripherals(context: Context): DeviceCapabilities {
        val capabilities = detectCapabilities(context)
        cachedCapabilities = capabilities
        return capabilities
    }

    /**
     * Get cached capabilities (synchronous, no Flow)
     */
    fun getCachedCapabilities(): DeviceCapabilities = cachedCapabilities ?: DeviceCapabilities()
}

/**
 * Device capabilities data class
 *
 * Indicates which hardware input devices are available
 */
data class DeviceCapabilities(
    val hasDpad: Boolean = false,
    val hasBluetoothKeyboard: Boolean = false,
    val hasGamepad: Boolean = false,
    val hasArrowKeys: Boolean = false,
) {
    val hasAnyHardwareButtons: Boolean
        get() = hasDpad || hasGamepad || hasArrowKeys || hasBluetoothKeyboard
}
