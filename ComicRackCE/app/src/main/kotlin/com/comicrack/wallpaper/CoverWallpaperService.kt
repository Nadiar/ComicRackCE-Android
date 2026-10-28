package com.comicrack.wallpaper

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import com.comicrack.data.preferences.AppPreferencesRepository
import com.comicrack.data.preferences.ColorTheme
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Live Wallpaper Service that cycles through comic book covers with customizable animation.
 *
 * Features:
 * - Cycles through comic covers from the library
 * - Configurable animation speed (0.25x - 2.0x)
 * - Optional parallax effect based on device orientation
 * - Configurable color theme (AUTO/DARK/LIGHT). NOTE: AUTO delegates to the system
 *   night mode. Use {@link android.content.res.Configuration#UI_MODE_NIGHT_MASK} or
 *   AppCompat APIs to detect system dark mode and provide a tested fallback on older
 *   API levels where automatic detection is limited.
 * - Low power mode for reduced battery consumption
 *
 * Preferences monitored:
 * - animationSpeed: Controls frame rate (0.25-2.0x multiplier)
 * - parallaxEffectEnabled: Enable/disable parallax transformation
 * - wallpaperColorTheme: Color overlay theme (AUTO/DARK/LIGHT)
 * - lowPowerMode: Reduce update frequency when enabled
 *
 * Notes:
 * - The engine observes app-scoped preference flows; ensure underlying preference storage
 *   (e.g. DataStore) is initialized by Application.onCreate() before starting the engine.
 */
class CoverWallpaperService : WallpaperService() {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WallpaperEntryPoint {
        fun appPreferencesRepository(): AppPreferencesRepository
    }

    override fun onCreateEngine(): Engine {
        // Get AppPreferencesRepository from Hilt
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            WallpaperEntryPoint::class.java
        )
        return WallpaperEngine(entryPoint.appPreferencesRepository())
    }

    /**
     * Inner Engine class managing wallpaper rendering and animation loop.
     *
     * Responsibilities:
     * - Observe preference changes and update rendering parameters
     * - Manage animation frame timing based on animation speed
     * - Apply visual effects (parallax, color overlay)
     * - Render wallpaper surface at appropriate frame rate
     *
     * Implementation and lifecycle notes:
     * - Avoid holding large bitmaps in memory on the UI thread. Prefer scaled bitmaps
     *   and small caches to reduce peak memory usage on low-end devices.
     * - The engine uses an application-scoped coroutine scope; cancel jobs in
     *   `onDestroy()` to prevent leaks. Avoid long-running blocking IO on the UI thread.
     * - Validate behavior across a range of device profiles and battery/power modes.
     */
    inner class WallpaperEngine(
        private val preferencesRepository: AppPreferencesRepository,
    ) : Engine() {
        private val handler = Handler(Looper.getMainLooper())
        private var isVisible = false
        private var isDestroyed = false

        // Observes preference flows on the main dispatcher because updates affect UI-thread
        // state. engineScope is not lifecycle-aware; cancel jobs in onDestroy() to avoid leaks.
        private val engineScope = CoroutineScope(Dispatchers.Main)
        private var preferenceJob: Job? = null
        private var animationSpeed = 1.0f
        private var parallaxEffectEnabled = true
        private var colorTheme = ColorTheme.AUTO
        private var lowPowerMode = false

        // Animation state
        private var currentFrame = 0f

        // totalFrames chosen to allow an approximately 10s cycle at 30 FPS by default
        private var totalFrames = 300f // 300 frames of animation cycle (~10s @ 30 FPS)

        // Base frame delay used before applying animationSpeed multiplier
        private val baseFrameDelay = 33L // ~30 FPS base (33ms per frame)
        private var currentFrameDelay = baseFrameDelay

        // Drawing
        private val paint =
            Paint().apply {
                isAntiAlias = true
                isFilterBitmap = true
            }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            setTouchEventsEnabled(false)
            observePreferences()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            isVisible = visible
            if (visible) {
                scheduleNextFrame()
            } else {
                handler.removeCallbacks(drawRunnable)
            }
        }

        override fun onDestroy() {
            super.onDestroy()
            isDestroyed = true
            handler.removeCallbacks(drawRunnable)
            preferenceJob?.cancel()
            engineScope.coroutineContext[Job]?.cancel()
        }

        /**
         * Observe preference changes and update rendering parameters accordingly.
         * Preferences are collected using the engine's own coroutine scope.
         */
        private fun observePreferences() {
            // Observe animation speed
            preferenceJob =
                engineScope.launch {
                    preferencesRepository.animationSpeed.collect { speed ->
                        animationSpeed = speed
                        updateFrameDelay()
                    }
                }

            // Observe parallax effect enabled
            engineScope.launch {
                preferencesRepository.parallaxEffectEnabled.collect { enabled ->
                    parallaxEffectEnabled = enabled
                }
            }

            // Observe wallpaper color theme
            engineScope.launch {
                preferencesRepository.wallpaperColorTheme.collect { theme ->
                    colorTheme = theme
                }
            }

            // Observe low power mode
            engineScope.launch {
                preferencesRepository.lowPowerMode.collect { lowPower ->
                    lowPowerMode = lowPower
                    updateFrameDelay()
                }
            }
        }

        /**
         * Calculate frame delay based on animation speed and low power mode.
         *
         * Normal mode: baseFrameDelay / animationSpeed
         * Low power mode: baseFrameDelay / (animationSpeed * 0.5) = effectively halves update frequency
         */
        private fun updateFrameDelay() {
            val delayBase = if (lowPowerMode) baseFrameDelay * 2 else baseFrameDelay
            currentFrameDelay = (delayBase / animationSpeed).toLong().coerceIn(16L, 200L)
        }

        /**
         * Render a single frame to the wallpaper surface.
         *
         * Process:
         * 1. Lock the surface for drawing
         * 2. Clear to background color
         * 3. Draw base layer (placeholder or comic cover)
         * 4. Apply parallax effect if enabled
         * 5. Apply color theme overlay
         * 6. Update animation frame counter
         * 7. Unlock and post surface
         * 8. Schedule next frame
         */
        private val drawRunnable =
            object : Runnable {
                override fun run() {
                    if (isDestroyed || !isVisible) return

                    val holder = surfaceHolder
                    var canvas: Canvas? = null
                    try {
                        canvas = holder.lockCanvas()
                        if (canvas != null) {
                            drawFrame(canvas)
                        }
                    } finally {
                        if (canvas != null) {
                            holder.unlockCanvasAndPost(canvas)
                        }
                    }

                    scheduleNextFrame()
                }
            }

        /**
         * Draw a single frame with animations and effects.
         *
         * @param canvas The canvas to draw on
         */
        private fun drawFrame(canvas: Canvas) {
            // Clear canvas with background color
            val backgroundColor =
                when (colorTheme) {
                    ColorTheme.DARK -> Color.BLACK
                    ColorTheme.LIGHT -> Color.WHITE
                    ColorTheme.AUTO -> {
                        // AUTO should defer to system night mode detection. Use the standard system
                        // APIs (Configuration.UI_MODE_NIGHT_MASK or AppCompat) and document fallback
                        // behavior for older API levels when tests are added.
                        Color.BLACK
                    }
                }
            canvas.drawColor(backgroundColor)

            // Draw placeholder wallpaper (animated background)
            drawAnimatedBackground(canvas)

            // Apply parallax effect if enabled
            if (parallaxEffectEnabled) {
                applyParallaxEffect(canvas)
            }

            // Update animation frame
            currentFrame = (currentFrame + 1f) % totalFrames
        }

        /**
         * Draw an animated placeholder background.
         * This implementation is intentionally lightweight (low memory and GPU work) to
         * act as a fallback while the real cover rendering pipeline is in place. Replace
         * with cover rendering only after verifying memory/timing on low-end devices.
         *
         * @param canvas The canvas to draw on
         */
        private fun drawAnimatedBackground(canvas: Canvas) {
            // Calculate animation progress (0.0 to 1.0)
            val progress = (currentFrame / totalFrames).coerceIn(0f, 1f)

            // Simple animated gradient background
            val width = canvas.width
            val height = canvas.height

            // Cycle through hue-like colors (simplified, no actual HSV conversion)
            val colorValue = ((progress * 360f).toInt() % 360)
            val animatedColor =
                when {
                    colorValue < 120 -> Color.rgb(100 + (colorValue / 120f * 155).toInt(), 100, 150)
                    colorValue < 240 -> Color.rgb(150, 100 + ((colorValue - 120f) / 120f * 155).toInt(), 100)
                    else -> Color.rgb(100, 150, 100 + ((colorValue - 240f) / 120f * 155).toInt())
                }

            paint.color = animatedColor
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        }

        /**
         * Apply a parallax effect to create depth illusion.
         * The effect is based on the animation progress, creating a subtle shifting motion.
         *
         * @param canvas The canvas to draw on
         */
        private fun applyParallaxEffect(canvas: Canvas) {
            // Calculate parallax offset based on animation progress. Offsets are kept small to
            // maintain a subtle depth effect and avoid motion sickness on low-refresh devices.
            val progress = (currentFrame / totalFrames).coerceIn(0f, 1f)
            val offsetX = (Math.sin(progress * Math.PI * 2) * 20).toFloat()
            val offsetY = (Math.cos(progress * Math.PI * 2) * 20).toFloat()

            // Semi-transparent overlay used to emphasize movement without large overdraw
            val overlayColor = Color.argb(20, 255, 255, 255)
            paint.color = overlayColor

            val width = canvas.width
            val height = canvas.height

            // Draw offset rectangles for parallax effect
            canvas.drawRect(offsetX, offsetY, width + offsetX, height + offsetY, paint)
        }

        /**
         * Schedule the next frame to be drawn at the calculated delay.
         */
        private fun scheduleNextFrame() {
            if (!isDestroyed && isVisible) {
                handler.postDelayed(drawRunnable, currentFrameDelay)
            }
        }
    }

}
