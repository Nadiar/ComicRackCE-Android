package com.comicrack.settings.catalog

// DOCFIX: doc-only pass - 2026-01-15 (docfix/pilot-comments)

import com.comicrack.settings.data.TagsRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central catalog of all ComicRack settings.
 * Singleton that provides all settings with language-aware tags loaded from JSON.
 *
 * All code injects this class to access settings - no object singleton pattern.
 */
@Singleton
class SettingsCatalog
    @Inject
    constructor(
        private val tagsRepository: TagsRepository,
    ) {
        // ============================================================================
        // GENERAL SETTINGS (3 settings)
        // ============================================================================

        private val generalRtlMode =
            Setting(
                id = "rtl_mode",
                title = "Right-to-Left Mode",
                description = "Enable RTL reading direction for Farsi, Hebrew, and Arabic comics",
                category = SettingCategory.GENERAL,
                subcategory = "Display",
                type = SettingType.Toggle,
                defaultValue = false,
                tags = emptyList(), // Will be loaded from JSON
                icon = null,
                isAdvanced = false,
            )

        private val generalLanguage =
            Setting(
                id = "language_selection",
                title = "App Language",
                description = "Select the language for the user interface",
                category = SettingCategory.GENERAL,
                subcategory = "Localization",
                type =
                    SettingType.Choice(
                        options = listOf("English", "EspaÃ±ol", "FranÃ§ais", "Deutsch", "æ—¥æœ¬èªž", "ä¸­æ–‡"),
                    ),
                defaultValue = "English",
                tags = emptyList(),
                icon = null,
                isAdvanced = false,
            )

        private val generalDarkMode =
            Setting(
                id = "dark_mode",
                title = "Dark Mode",
                description = "Apply dark theme to the entire application",
                category = SettingCategory.GENERAL,
                subcategory = "Appearance",
                type = SettingType.Toggle,
                defaultValue = true,
                tags = emptyList(),
                icon = null,
                isAdvanced = false,
            )

        // ============================================================================
        // READING SETTINGS (15 settings)
        // ============================================================================

        private val readingPageTransition =
            Setting(
                id = "page_transition_effect",
                title = "Page Transition Effect",
                description = "Choose how pages transition when navigating",
                category = SettingCategory.READING,
                subcategory = "Navigation",
                type = SettingType.Choice(options = listOf("Fade", "Slide", "Instant", "Flip")),
                defaultValue = "Fade",
                tags = emptyList(),
                isAdvanced = false,
            )

        private val readingZoomMode =
            Setting(
                id = "zoom_mode",
                title = "Zoom Mode",
                description = "Set default zoom behavior when opening a page",
                category = SettingCategory.READING,
                subcategory = "Zoom",
                type = SettingType.Choice(options = listOf("Fit Page", "Fit Width", "100%", "Smart")),
                defaultValue = "Fit Page",
                tags = emptyList(),
                isAdvanced = false,
            )

        private val readingDoubleTapZoom =
            Setting(
                id = "double_tap_zoom",
                title = "Double-Tap Zoom",
                description = "Enable zoom on double-tap gesture",
                category = SettingCategory.READING,
                subcategory = "Gestures",
                type = SettingType.Toggle,
                defaultValue = true,
                tags = emptyList(),
                isAdvanced = false,
            )

        private val readingPinchZoom =
            Setting(
                id = "pinch_zoom",
                title = "Pinch Zoom",
                description = "Enable zoom on pinch gesture",
                category = SettingCategory.READING,
                subcategory = "Gestures",
                type = SettingType.Toggle,
                defaultValue = true,
                tags = emptyList(),
                isAdvanced = false,
            )

        private val readingSwipeNavigation =
            Setting(
                id = "swipe_navigation",
                title = "Swipe Navigation",
                description = "Navigate pages with horizontal swipe gestures",
                category = SettingCategory.READING,
                subcategory = "Gestures",
                type = SettingType.Toggle,
                defaultValue = true,
                tags = emptyList(),
                isAdvanced = false,
            )

        private val readingSwipeDirection =
            Setting(
                id = "swipe_direction",
                title = "Swipe Direction",
                description = "Choose swipe behavior direction",
                category = SettingCategory.READING,
                subcategory = "Gestures",
                type = SettingType.Choice(options = listOf("Left-to-Right", "Right-to-Left", "Auto")),
                defaultValue = "Auto",
                tags = emptyList(),
                isAdvanced = false,
            )

        private val readingBrightness =
            Setting(
                id = "brightness_level",
                title = "Brightness Level",
                description = "Adjust screen brightness for reading (0-100%)",
                category = SettingCategory.READING,
                subcategory = "Display",
                type = SettingType.Slider(min = 0f, max = 100f, step = 1f),
                defaultValue = 50,
                tags = emptyList(),
                isAdvanced = false,
            )

        private val readingContrast =
            Setting(
                id = "contrast_level",
                title = "Contrast Level",
                description = "Adjust contrast for better readability (0-200%)",
                category = SettingCategory.READING,
                subcategory = "Display",
                type = SettingType.Slider(min = 0f, max = 200f, step = 5f),
                defaultValue = 100,
                tags = emptyList(),
                isAdvanced = false,
            )

        private val readingSaturation =
            Setting(
                id = "reader_saturation",
                title = "Saturation",
                description = "Adjust image saturation (50-150%)",
                category = SettingCategory.READING,
                subcategory = "Display",
                type = SettingType.Slider(min = 50f, max = 150f, step = 5f),
                defaultValue = 100,
                isAdvanced = false,
            )

        private val readingSharpness =
            Setting(
                id = "reader_sharpness",
                title = "Sharpness",
                description = "Adjust image sharpness (50-150%)",
                category = SettingCategory.READING,
                subcategory = "Display",
                type = SettingType.Slider(min = 50f, max = 150f, step = 5f),
                defaultValue = 100,
                isAdvanced = false,
            )

        private val readingOpenWithMenu =
            Setting(
                id = "reader_open_with_menu",
                title = "Initial Menu State",
                description = "Always open the reader menu when a comic starts",
                category = SettingCategory.READING,
                subcategory = "Behavior",
                type = SettingType.Toggle,
                defaultValue = true,
                isAdvanced = false,
            )

        private val readingShowTapToasts =
            Setting(
                id = "reader_show_tap_toasts",
                title = "Show Tap Feedbacks",
                description = "Show a brief toast message when tapping the screen",
                category = SettingCategory.READING,
                subcategory = "Behavior",
                type = SettingType.Toggle,
                defaultValue = true,
                isAdvanced = false,
            )

        private val readingMarginSize =
            Setting(
                id = "margin_size",
                title = "Margin Size",
                description = "Adjust page margin/padding (0-50px)",
                category = SettingCategory.READING,
                subcategory = "Display",
                type = SettingType.Slider(min = 0f, max = 50f, step = 1f),
                defaultValue = 10,
                tags = emptyList(),
                isAdvanced = false,
            )

        private val readingAnimateTransitions =
            Setting(
                id = "animate_transitions",
                title = "Animate Transitions",
                description = "Enable smooth animations between pages",
                category = SettingCategory.READING,
                subcategory = "Animation",
                type = SettingType.Toggle,
                defaultValue = true,
                tags = emptyList(),
                isAdvanced = false,
            )

        private val readingAutoScroll =
            Setting(
                id = "auto_scroll",
                title = "Auto-Scroll",
                description = "Automatically scroll pages downward",
                category = SettingCategory.READING,
                subcategory = "Navigation",
                type = SettingType.Toggle,
                defaultValue = false,
                tags = emptyList(),
                isAdvanced = true,
            )

        private val readingAutoScrollSpeed =
            Setting(
                id = "auto_scroll_speed",
                title = "Auto-Scroll Speed",
                description = "Set auto-scroll speed (1-10)",
                category = SettingCategory.READING,
                subcategory = "Navigation",
                type = SettingType.Slider(min = 1f, max = 10f, step = 1f),
                defaultValue = 5,
                tags = emptyList(),
                isAdvanced = true,
            )

        // readingInvertColors removed

        // ============================================================================
        // CONTROLS SETTINGS (25 settings)
        // ============================================================================

        private val controlsTapZoneLeft =
            Setting(
                id = "tap_zone_left_action",
                title = "Left Tap Zone Action",
                description = "Action performed when left edge is tapped",
                category = SettingCategory.CONTROLS,
                subcategory = "Tap Zones",
                type =
                    SettingType.Choice(
                        options = listOf("Previous Page", "Next Page", "Toggle Menu", "Bookmark", "Disabled"),
                    ),
                defaultValue = "Previous Page",
                tags = emptyList(),
                isAdvanced = false,
            )

        private val controlsTapZoneRight =
            Setting(
                id = "tap_zone_right_action",
                title = "Right Tap Zone Action",
                description = "Action performed when right edge is tapped",
                category = SettingCategory.CONTROLS,
                subcategory = "Tap Zones",
                type =
                    SettingType.Choice(
                        options = listOf("Previous Page", "Next Page", "Toggle Menu", "Bookmark", "Disabled"),
                    ),
                defaultValue = "Next Page",
                tags = emptyList(),
                isAdvanced = false,
            )

        private val controlsTapZoneTop =
            Setting(
                id = "tap_zone_top_action",
                title = "Top Tap Zone Action",
                description = "Action performed when top edge is tapped",
                category = SettingCategory.CONTROLS,
                subcategory = "Tap Zones",
                type =
                    SettingType.Choice(
                        options = listOf("Previous Page", "Next Page", "Toggle Menu", "Bookmark", "Disabled"),
                    ),
                defaultValue = "Toggle Menu",
                tags = emptyList(),
                isAdvanced = false,
            )

        private val controlsTapZoneBottom =
            Setting(
                id = "tap_zone_bottom_action",
                title = "Bottom Tap Zone Action",
                description = "Action performed when bottom edge is tapped",
                category = SettingCategory.CONTROLS,
                subcategory = "Tap Zones",
                type =
                    SettingType.Choice(
                        options = listOf("Previous Page", "Next Page", "Toggle Menu", "Bookmark", "Disabled"),
                    ),
                defaultValue = "Disabled",
                tags = emptyList(),
                isAdvanced = false,
            )

        private val controlsTapZoneSize =
            Setting(
                id = "tap_zone_size",
                title = "Tap Zone Size",
                description = "Size of edge tap zones (5-30% of screen)",
                category = SettingCategory.CONTROLS,
                subcategory = "Tap Zones",
                type = SettingType.Slider(min = 5f, max = 30f, step = 1f),
                defaultValue = 15,
                tags = emptyList(),
                isAdvanced = false,
            )

        private val controlsHapticFeedback =
            Setting(
                id = "haptic_feedback_enabled",
                title = "Haptic Feedback",
                description = "Enable vibration feedback on interactions",
                category = SettingCategory.CONTROLS,
                subcategory = "Haptics",
                type = SettingType.Toggle,
                defaultValue = true,
                tags = emptyList(),
                isAdvanced = false,
            )

        private val controlsHapticIntensity =
            Setting(
                id = "haptic_intensity",
                title = "Haptic Intensity",
                description = "Adjust vibration intensity (1-10)",
                category = SettingCategory.CONTROLS,
                subcategory = "Haptics",
                type = SettingType.Slider(min = 1f, max = 10f, step = 1f),
                defaultValue = 5,
                tags = emptyList(),
                isAdvanced = false,
            )

        private val controlsVolumeButtonsNavigate =
            Setting(
                id = "volume_buttons_navigate",
                title = "Volume Buttons Navigate",
                description = "Use volume buttons to navigate pages",
                category = SettingCategory.CONTROLS,
                subcategory = "Hardware Buttons",
                type = SettingType.Toggle,
                defaultValue = true,
                tags = emptyList(),
                isAdvanced = false,
            )

        private val controlsVolumeUpAction =
            Setting(
                id = "volume_up_action",
                title = "Volume Up Action",
                description = "Action for volume up button",
                category = SettingCategory.CONTROLS,
                subcategory = "Hardware Buttons",
                type = SettingType.Choice(options = listOf("Previous Page", "Next Page", "Disabled")),
                defaultValue = "Previous Page",
                tags = emptyList(),
                isAdvanced = false,
            )

        private val controlsVolumeDownAction =
            Setting(
                id = "volume_down_action",
                title = "Volume Down Action",
                description = "Action for volume down button",
                category = SettingCategory.CONTROLS,
                subcategory = "Hardware Buttons",
                type = SettingType.Choice(options = listOf("Previous Page", "Next Page", "Disabled")),
                defaultValue = "Next Page",
                tags = emptyList(),
                isAdvanced = false,
            )

        private val controlsOrientationLock =
            Setting(
                id = "orientation_lock",
                title = "Lock Orientation",
                description = "Lock screen to a specific orientation",
                category = SettingCategory.CONTROLS,
                subcategory = "Display",
                type =
                    SettingType.Choice(
                        options = listOf("Auto", "Portrait", "Landscape", "Portrait Inverted", "Landscape Inverted"),
                    ),
                defaultValue = "Auto",
                tags = emptyList(),
                isAdvanced = false,
            )

        private val controlsAutoPageTurnDelay =
            Setting(
                id = "auto_page_turn_delay",
                title = "Auto-Turn Page Delay",
                description = "Delay before auto-turning page (seconds)",
                category = SettingCategory.CONTROLS,
                subcategory = "Navigation",
                type = SettingType.Slider(min = 1f, max = 60f, step = 1f),
                defaultValue = 10,
                tags = emptyList(),
                isAdvanced = true,
            )

        private val controlsDoubleTapDelay =
            Setting(
                id = "double_tap_delay",
                title = "Double-Tap Delay",
                description = "Time window for double-tap recognition (ms)",
                category = SettingCategory.CONTROLS,
                subcategory = "Gestures",
                type = SettingType.Slider(min = 200f, max = 800f, step = 50f),
                defaultValue = 400,
                tags = emptyList(),
                isAdvanced = true,
            )

        private val controlsLongPressDelay =
            Setting(
                id = "long_press_delay",
                title = "Long-Press Delay",
                description = "Time to register long-press (ms)",
                category = SettingCategory.CONTROLS,
                subcategory = "Gestures",
                type = SettingType.Slider(min = 300f, max = 1000f, step = 50f),
                defaultValue = 500,
                tags = emptyList(),
                isAdvanced = true,
            )

        private val controlsEdgeGesturesEnabled =
            Setting(
                id = "edge_gestures_enabled",
                title = "Edge Gestures",
                description = "Enable gestures from screen edges",
                category = SettingCategory.CONTROLS,
                subcategory = "Gestures",
                type = SettingType.Toggle,
                defaultValue = true,
                tags = emptyList(),
                isAdvanced = false,
            )

        private val controlsGestureSwipeThreshold =
            Setting(
                id = "swipe_threshold",
                title = "Swipe Threshold",
                description = "Minimum swipe distance (pixels)",
                category = SettingCategory.CONTROLS,
                subcategory = "Gestures",
                type = SettingType.Slider(min = 10f, max = 100f, step = 5f),
                defaultValue = 50,
                tags = emptyList(),
                isAdvanced = true,
            )

        private val controlsStylusSupport =
            Setting(
                id = "stylus_support",
                title = "Stylus Support",
                description = "Enable stylus/pen input support",
                category = SettingCategory.CONTROLS,
                subcategory = "Input Devices",
                type = SettingType.Toggle,
                defaultValue = false,
                tags = emptyList(),
                isAdvanced = false,
            )

        private val controlsMouseSupport =
            Setting(
                id = "mouse_support",
                title = "Mouse Support",
                description = "Enable mouse wheel navigation",
                category = SettingCategory.CONTROLS,
                subcategory = "Input Devices",
                type = SettingType.Toggle,
                defaultValue = false,
                tags = emptyList(),
                isAdvanced = false,
            )

        private val controlsGamepadSupport =
            Setting(
                id = "gamepad_support",
                title = "Gamepad Support",
                description = "Enable gamepad/controller input",
                category = SettingCategory.CONTROLS,
                subcategory = "Input Devices",
                type = SettingType.Toggle,
                defaultValue = false,
                tags = emptyList(),
                isAdvanced = false,
            )

        private val controlsGamepadLeftAction =
            Setting(
                id = "gamepad_left_button",
                title = "Gamepad Left Button",
                description = "Action for gamepad left button",
                category = SettingCategory.CONTROLS,
                subcategory = "Input Devices",
                type = SettingType.Choice(options = listOf("Previous Page", "Next Page", "Menu", "Disabled")),
                defaultValue = "Previous Page",
                tags = emptyList(),
                isAdvanced = false,
            )

        private val controlsGamepadRightAction =
            Setting(
                id = "gamepad_right_button",
                title = "Gamepad Right Button",
                description = "Action for gamepad right button",
                category = SettingCategory.CONTROLS,
                subcategory = "Input Devices",
                type = SettingType.Choice(options = listOf("Previous Page", "Next Page", "Menu", "Disabled")),
                defaultValue = "Next Page",
                tags = emptyList(),
                isAdvanced = false,
            )

        // ============================================================================
        // WALLPAPER SETTINGS (5 settings)
        // ============================================================================

        private val wallpaperEnabled =
            Setting(
                id = "wallpaper_enabled",
                title = "Enable Wallpaper",
                description = "Show library wallpaper on home screen",
                category = SettingCategory.WALLPAPER,
                subcategory = "Appearance",
                type = SettingType.Toggle,
                defaultValue = true,
                tags = emptyList(),
                isAdvanced = false,
            )

        private val wallpaperOpacity =
            Setting(
                id = "wallpaper_opacity",
                title = "Wallpaper Opacity",
                description = "Adjust wallpaper transparency (0-100%)",
                category = SettingCategory.WALLPAPER,
                subcategory = "Appearance",
                type = SettingType.Slider(min = 0f, max = 100f, step = 5f),
                defaultValue = 70,
                tags = emptyList(),
                isAdvanced = false,
            )

        private val wallpaperBlurEffect =
            Setting(
                id = "wallpaper_blur",
                title = "Blur Effect",
                description = "Apply blur effect to wallpaper",
                category = SettingCategory.WALLPAPER,
                subcategory = "Appearance",
                type = SettingType.Toggle,
                defaultValue = false,
                tags = emptyList(),
                isAdvanced = false,
            )

        private val wallpaperBlurRadius =
            Setting(
                id = "wallpaper_blur_radius",
                title = "Blur Radius",
                description = "Blur intensity (1-20px)",
                category = SettingCategory.WALLPAPER,
                subcategory = "Appearance",
                type = SettingType.Slider(min = 1f, max = 20f, step = 1f),
                defaultValue = 5,
                tags = emptyList(),
                isAdvanced = false,
            )

        private val wallpaperDimming =
            Setting(
                id = "wallpaper_dimming",
                title = "Dimming Level",
                description = "Dim wallpaper brightness (0-50%)",
                category = SettingCategory.WALLPAPER,
                subcategory = "Appearance",
                type = SettingType.Slider(min = 0f, max = 50f, step = 5f),
                defaultValue = 20,
                tags = emptyList(),
                isAdvanced = false,
            )

        // ============================================================================
        // PUBLIC API
        // ============================================================================

        /**
         * Complete list of all settings in the catalog.
         * Tags are populated from JSON by TagsRepository during initialization.
         */
        val SETTINGS: MutableList<Setting> =
            mutableListOf(
                // General (3)
                generalRtlMode,
                generalLanguage,
                generalDarkMode,
                // Reading (15)
                readingPageTransition,
                readingZoomMode,
                readingDoubleTapZoom,
                readingPinchZoom,
                readingSwipeNavigation,
                readingSwipeDirection,
                readingBrightness,
                readingContrast,
                readingSaturation,
                readingSharpness,
                readingMarginSize,
                readingAnimateTransitions,
                readingAutoScroll,
                readingAutoScrollSpeed,
                readingOpenWithMenu,
                readingShowTapToasts,
                // Controls (25)
                controlsTapZoneLeft,
                controlsTapZoneRight,
                controlsTapZoneTop,
                controlsTapZoneBottom,
                controlsTapZoneSize,
                controlsHapticFeedback,
                controlsHapticIntensity,
                controlsVolumeButtonsNavigate,
                controlsVolumeUpAction,
                controlsVolumeDownAction,
                controlsOrientationLock,
                controlsAutoPageTurnDelay,
                controlsDoubleTapDelay,
                controlsLongPressDelay,
                controlsEdgeGesturesEnabled,
                controlsGestureSwipeThreshold,
                controlsStylusSupport,
                controlsMouseSupport,
                controlsGamepadSupport,
                controlsGamepadLeftAction,
                controlsGamepadRightAction,
                // Wallpaper (5)
                wallpaperEnabled,
                wallpaperOpacity,
                wallpaperBlurEffect,
                wallpaperBlurRadius,
                wallpaperDimming,
            )

        init {
            // Populate tags from JSON for each setting
            val updatedSettings =
                SETTINGS.map { setting ->
                    val tagsFromJson = tagsRepository.getTags(setting.id)
                    if (tagsFromJson.isNotEmpty()) {
                        setting.copy(tags = tagsFromJson)
                    } else {
                        setting
                    }
                }
            SETTINGS.clear()
            SETTINGS.addAll(updatedSettings)
        }

        /**
         * Find a setting by ID.
         * @param id The setting ID
         * @return The setting, or null if not found
         */
        fun getSettingById(id: String): Setting? = SETTINGS.find { it.id == id }

        /**
         * Get all settings in a category.
         * @param category The category to filter by
         * @return List of settings in that category
         */
        fun getSettingsByCategory(category: SettingCategory): List<Setting> =
            SETTINGS.filter { it.category == category }

        /**
         * Search settings by query and tags.
         * Phase 2.4: Simple search that matches on title, description, and tags.
         * Results are NOT ranked by relevance; instead they maintain system hierarchy order
         * within each category. Category order: GENERAL, READING, CONTROLS, WALLPAPER.
         *
         * This simple approach keeps the UI clean and predictable. Relevance ranking
         * can be added later as [SRC-09-001-B] if needed.
         *
         * @param query The search query
         * @return List of matching settings, grouped by category in system order
         */
        fun search(query: String): List<Setting> {
            if (query.isBlank()) return emptyList()

            val lowerQuery = query.lowercase()
            val results = mutableListOf<Setting>()

            for (setting in SETTINGS) {
                var isMatch = false

                // Title match
                if (setting.title.lowercase().contains(lowerQuery)) {
                    isMatch = true
                }

                // Description match
                if (setting.description.lowercase().contains(lowerQuery)) {
                    isMatch = true
                }

                // Tag match (searches through tags and hidden keywords)
                val hasTagMatch =
                    setting.tags.any { tag ->
                        tag.lowercase().contains(lowerQuery)
                    }
                if (hasTagMatch) {
                    isMatch = true
                }

                if (isMatch) {
                    results.add(setting)
                }
            }

            // Return results maintaining original SETTINGS order (which respects categories and hierarchy)
            return results
        }

        /**
         * Get all unique categories that have at least one setting.
         * @return List of populated categories
         */
        fun getPopulatedCategories(): List<SettingCategory> = SETTINGS.map { it.category }.distinct()

        /**
         * Get the count of settings in each category.
         * @return Map of category to count
         */
        fun getCategoryCounts(): Map<SettingCategory, Int> = SETTINGS.groupingBy { it.category }.eachCount()
    }
