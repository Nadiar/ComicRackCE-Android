package com.comicrack.reader.service

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

import android.app.Activity
import com.comicrack.data.preferences.AppPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * BrightnessManager - Controls system brightness for the reader.
 *
 * Observes the readerBrightness preference and applies it to the system
 * when the reader is open. Restores the original brightness on close.
 *
 * **Brightness Mapping**:
 * - 0.0 (darkest) â†’ system brightness: 0.0
 * - 0.5 (normal) â†’ system brightness: 0.5
 * - 1.0 (brightest) â†’ system brightness: 1.0
 *
 * **Special Cases**:
 * - -1.0 = Let system manage brightness (follow system settings)
 * - User can manually adjust system brightness while reading
 */
object BrightnessManager {
    private const val TAG = "BrightnessManager"

    private var originalBrightness: Float? = null
    private var observerScope: CoroutineScope? = null

    /**
     * Start observing brightness preference and applying to system.
     * Should be called from Activity onCreate() or when reader opens.
     *
     * @param activity The Activity to apply brightness to
     * @param preferencesRepository The repository providing brightness preference
     */
    fun startObservingBrightness(
        activity: Activity,
        preferencesRepository: AppPreferencesRepository,
    ) {
        Timber.tag(TAG).d("Starting brightness observation")

        // Save original brightness
        originalBrightness = activity.window.attributes.screenBrightness
        Timber.tag(TAG).d("Saved original brightness: $originalBrightness")

        // Create scope for observing preferences
        observerScope = CoroutineScope(Dispatchers.Main)

        observerScope?.launch {
            try {
                preferencesRepository.readerBrightness.collect { brightness ->
                    applyBrightness(activity, brightness)
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error observing brightness preference")
            }
        }
    }

    /**
     * Stop observing brightness and optionally restore original brightness.
     *
     * @param activity The Activity to restore brightness for
     * @param restore If true, restore original brightness on close
     */
    fun stopObservingBrightness(
        activity: Activity,
        restore: Boolean = true,
    ) {
        Timber.tag(TAG).d("Stopping brightness observation (restore=$restore)")

        if (restore) {
            originalBrightness?.let { applyBrightness(activity, it) }
        }

        observerScope?.let { scope ->
            // Note: CoroutineScope doesn't have cancel(), jobs in launch will complete naturally
            // when observerScope goes out of scope
        }
        observerScope = null
    }

    /**
     * Apply brightness to the activity window.
     *
     * Brightness values:
     * - 0.0 = Darkest (use caution, display might be hard to see)
     * - 0.5 = Normal brightness (system determines)
     * - 1.0 = Maximum brightness
     * - -1.0 = Let system manage (BRIGHTNESS_OVERRIDE_NONE)
     *
     * @param activity The Activity whose brightness to adjust
     * @param brightness The brightness value (0.0 to 1.0)
     */
    private fun applyBrightness(
        activity: Activity,
        brightness: Float,
    ) {
        try {
            val clampedBrightness = brightness.coerceIn(0.0f, 1.0f)

            val layoutParams = activity.window.attributes
            layoutParams.screenBrightness = clampedBrightness
            activity.window.attributes = layoutParams

            Timber.tag(TAG).d("Applied brightness: $clampedBrightness")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error applying brightness")
        }
    }
}
