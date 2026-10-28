package com.comicrack.data.preferences

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * AppPreferencesRepository â€” DataStore-based preferences for ComicRack
 *
 * **Phase 1: Data Layer Implementation**
 * Uses Jetpack DataStore (Preferences) for type-safe, async-safe preferences.
 * Replaces previous Room-based approach with modern Kotlin Coroutines.
 *
 * Responsibilities:
 * - Expose preferences as reactive Flows
 * - Provide safe, non-blocking read/write operations
 * - Guarantee single source of truth via singleton pattern
 *
 * **Key Design:**
 * - Singleton object for global access (no DI overhead)
 * - All keys are private and immutable
 * - Flows are recomposed automatically on value changes
 * - Default values match modern UX expectations (e.g., Grid view, System theme)
 *
 * @see PreferencesViewModel for UI state management
 */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "app_preferences",
)

// ===== Preference Enums (Top-Level) =====

enum class LibraryViewMode(
    val key: String,
) {
    GRID("grid"),
    LIST("list"),
}

enum class SortOrder(
    val key: String,
) {
    TITLE("title"),
    DATE_ADDED("date_added"),
    LAST_READ("last_read"),
}

enum class ReaderMode(
    val key: String,
) {
    SCROLL("scroll"),
    PAGE("page"),
}

enum class AppTheme(
    val key: String,
) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark"),
}

enum class FitMode(
    val key: String,
) {
    FIT_WIDTH("fit_width"),
    FIT_HEIGHT("fit_height"),
    FIT_PAGE("fit_page"),
    ORIGINAL_SIZE("original_size"),
}

enum class ReadingDirection(
    val key: String,
) {
    LTR("ltr"),
    RTL("rtl"),
}

enum class ColorTheme(
    val key: String,
) {
    AUTO("auto"),
    LIGHT("light"),
    DARK("dark"),
}

enum class ZoneSize(
    val key: String,
    val ratio: Float,
) {
    TINY("tiny", 0.10f),
    SMALL("small", 0.20f),
    MEDIUM("medium", 0.30f),
    LARGE("large", 0.40f),
    HUGE("huge", 0.50f),
}

/**
 * Available actions for tap zones and keyboard shortcuts
 * Matches legacy ComicRack reader action enum
 */
enum class ReaderTapAction(
    val key: String,
    val displayName: String,
) {
    NONE("none", "None"),
    MENU("menu", "Toggle Menu"),
    NEXT_PART("nextpart", "Next Part"),
    PREVIOUS_PART("previouspart", "Previous Part"),
    NEXT_PAGE("nextpage", "Next Page"),
    PREVIOUS_PAGE("previouspage", "Previous Page"),
    FIRST_PAGE("firstpage", "First Page"),
    LAST_PAGE("lastpage", "Last Page"),
    NEXT_BOOKMARK("nextbookmark", "Next Bookmark"),
    PREVIOUS_BOOKMARK("previousbookmark", "Previous Bookmark"),
    BOOKMARKS("bookmarks", "Show Bookmarks"),
    GOTO_PAGE("gotopage", "Go to Page"),
    NEXT_COMIC("nextcomic", "Next Comic"),
    PREVIOUS_COMIC("previouscomic", "Previous Comic"),
    TOGGLE_DOUBLE_PAGE("toggledoublepage", "Toggle Double Page"),
    SWITCH_FITTING_MODE("switchfittingmode", "Switch Fit Mode"),
    TOGGLE_BRIGHTNESS("togglebrightness", "Toggle Brightness"),
    ADJUST_COLORS("adjustcolors", "Adjust Colors"),
    SWITCH_ROTATION("switchrotation", "Rotate"),
    TOGGLE_LANDSCAPE_PORTRAIT("togglelandscapeportrait", "Toggle Orientation"),
    SHOW_ZOOM("showzoom", "Show Zoom"),
    ZOOM_IN("zoomin", "Zoom In"),
    ZOOM_OUT("zoomout", "Zoom Out"),
    SNAPSHOT("snapshot", "Save Screenshot"),
    COPY_TO_CLIPBOARD("copytoclipboard", "Copy to Clipboard"),
    RATE_BOOK("ratebook", "Rate Book"),
    CHANGE_PAGE_TYPE("changepagetype", "Change Page Type"),
    BACK_TO_LIBRARY("backtolibrary", "Back to Library"),
    ;

    /**
     * Returns a human-friendly name for the action, suitable for UI display (e.g., in toasts).
     */
    fun humanize(): String =
        when (this) {
            NEXT_PAGE -> "\u2192 Next Page"
            PREVIOUS_PAGE -> "\u2190 Previous Page"
            NEXT_PART -> "\u21a0 Next Part"
            PREVIOUS_PART -> "\u219e Previous Part"
            FIRST_PAGE -> "\u21e4 First Page"
            LAST_PAGE -> "\u21e5 Last Page"
            MENU -> "\u2022 Toggle Menu"
            GOTO_PAGE -> "\u25aa Go to Page"
            NEXT_COMIC -> "\u00bb Next Comic"
            PREVIOUS_COMIC -> "\u00ab Previous Comic"
            else -> displayName
        }

    companion object {
        /**
         * Converts a key string to a ReaderTapAction.
         * Performs case-insensitive matching to handle legacy PascalCase values (e.g., "NextPage" -> NEXT_PAGE).
         * Returns NONE if no match is found.
         */
        fun fromKey(key: String): ReaderTapAction {
            // First try exact match
            val exactMatch = entries.find { it.key == key }
            if (exactMatch != null) return exactMatch
            
            // Try case-insensitive match (for legacy PascalCase values like "NextPage", "NextPart")
            val caseInsensitiveMatch = entries.find { it.key.equals(key, ignoreCase = true) }
            if (caseInsensitiveMatch != null) {
                timber.log.Timber.tag("ReaderTapAction")
                    .w("Legacy key format detected: '$key' -> ${caseInsensitiveMatch.name}")
                return caseInsensitiveMatch
            }
            
            // No match found
            timber.log.Timber.tag("ReaderTapAction").w("Unknown action key: '$key', defaulting to NONE")
            return NONE
        }
    }
}

enum class BackgroundSyncMode(
    val key: String,
) {
    /** Only run when app is open */
    OFF("off"),

    /** Run in background when charging + on home WiFi */
    CHARGING("charging"),

    /** Always run in background on home WiFi (may impact battery) */
    ALWAYS("always"),
    ;

    companion object {
        fun fromKey(key: String): BackgroundSyncMode = entries.find { it.key == key } ?: CHARGING
    }
}

/**
 * Repository exposing application preferences backed by Jetpack DataStore.
 *
 * Automatically injected via Hilt. No manual initialization required.
 */
@Singleton
class AppPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val dataStore: DataStore<Preferences> = context.dataStore

    companion object {
        /**
         * Legacy Android key expected by ComicRack CE desktop.
         * The desktop validates incoming device keys against this hard-coded value.
         * Random UUIDs will cause the desktop to reject the connection.
         */
        @Suppress("MaxLineLength") // Required legacy key - cannot be shortened
        private const val LEGACY_ANDROID_KEY = "3082019d30820106a00302010202044e7b1922300d06092a864886f70d010105050030133111300f060355040a130863596f20536f6674301e170d3131303932323131313635305a170d3336303931353131313635305a30133111300f060355040a130863596f20536f667430819f300d06092a864886f70d010101050003818d00308189028181008d81ffb74008a048c517275a464db26461df06a3c85675b6ffa8bea15ec9288eec1ef1bf7616d09b7265bf1c5666473342c2a96ca385769592d73a21595335e5173c69ae5bb7aebd29387e9635ce30bdf11afff71145570b6577799ecac6100bcf0b2c4df6fe34fb8a418b5511c6a56c97b15c544269e91478ee24633ef063090203010001300d06092a864886f70d010105050003818100802cf8770c7af0744f9680b54da88b56eb1d6a48e8d446ce746817fe959991dc1f882323c6015edd4d48f28cfe3a94e30b75c92855b01a8c48354aae8e13a0b949390133c07b09419ac73d0b0b3dc13b2838fe9eae4b171c8022cb47ead602771560277cde7ad61e1a9ce5dee880d0226ed8cc71f36fb376d271a3cb61f1128b"

        // Layout constraints (columns, spacing, elevation)
        private const val MIN_LAYOUT_COLUMNS = 1
        private const val MAX_LAYOUT_COLUMNS = 6
        private const val MIN_GRID_SPACING_DP = 0.0f
        private const val MAX_GRID_SPACING_DP = 32.0f
        private const val DEFAULT_GRID_SPACING_DP = 8.0f
        private const val MIN_CARD_ELEVATION_DP = 0.0f
        private const val MAX_CARD_ELEVATION_DP = 16.0f
        private const val DEFAULT_CARD_ELEVATION_DP = 4.0f

        // Reader enhancement ranges
        private const val MIN_ENHANCEMENT_VALUE = 0.5f
        private const val MAX_ENHANCEMENT_VALUE = 1.5f
        private const val DEFAULT_ENHANCEMENT_VALUE = 1.0f

        // Zoom and sensitivity ranges
        private const val MIN_PINCH_ZOOM_SENSITIVITY = 0.25f
        private const val MAX_PINCH_ZOOM_SENSITIVITY = 2.0f

        // Animation speed range
        private const val MIN_ANIMATION_SPEED = 0.25f
        private const val MAX_ANIMATION_SPEED = 2.0f

        // Recent searches limit
        private const val RECENT_SEARCHES_LIMIT = 10
    }

    // ===== Preference Keys =====
    // Library
    private val LIBRARY_VIEW_MODE = stringPreferencesKey("library_view_mode")
    private val LIBRARY_SORT_ORDER = stringPreferencesKey("library_sort_order")
    private val LIBRARY_PATH = stringPreferencesKey("library_path") // Phase 3.2: Main library folder URI
    // Adaptive column count (stored as string)
    private val LIBRARY_LAYOUT_COLUMNS = stringPreferencesKey("library_layout_columns")
    private val LIBRARY_GRID_SPACING = floatPreferencesKey("library_grid_spacing") // Grid spacing in dp
    private val LIBRARY_CARD_ELEVATION = floatPreferencesKey("library_card_elevation") // Card elevation in dp
    private val LIBRARY_SHOW_TITLES = booleanPreferencesKey("library_show_titles") // Show comic titles
    private val LIBRARY_ANIMATE_CARDS = booleanPreferencesKey("library_animate_cards") // Enable card animations
    private val AUTO_SCAN_LIBRARY = booleanPreferencesKey("auto_scan_library")
    private val RECURSIVE_SCAN = booleanPreferencesKey("recursive_scan")
    private val REMEMBER_READING_POSITION = booleanPreferencesKey("remember_reading_position")

    // When true, prefer direct file access (MANAGE_EXTERNAL_STORAGE) over the
    // Storage Access Framework (SAF). This flag exists for legacy devices and
    // workflows that cannot use SAF. **Prefer SAF** where possible since it
    // provides scoped access and better platform compatibility. Enabling
    // direct file access requires user consent and appropriate manifest
    // permissions; treat this as an opt-in advanced setting.
    // Use MANAGE_EXTERNAL_STORAGE instead of SAF
    private val USE_DIRECT_FILE_ACCESS = booleanPreferencesKey("use_direct_file_access")

    // Reader
    private val READER_MODE = stringPreferencesKey("reader_mode")
    private val READER_FLASH_COVER = booleanPreferencesKey("reader_flash_cover")
    private val READER_ASK_RATING = booleanPreferencesKey("reader_ask_rating")
    private val READER_SHOW_PAGE_NUMBERS = booleanPreferencesKey("reader_show_page_numbers")
    private val READER_KEEP_SCREEN_ON = booleanPreferencesKey("reader_keep_screen_on")
    private val READER_BRIGHTNESS = floatPreferencesKey("reader_brightness")
    private val READER_BRIGHTNESS_ENABLED = booleanPreferencesKey("reader_brightness_enabled")
    private val PAGE_FIT_MODE = stringPreferencesKey("page_fit_mode")
    private val PAGE_TRANSITIONS_ENABLED = booleanPreferencesKey("page_transitions_enabled")
    private val READING_DIRECTION = stringPreferencesKey("reading_direction")
    private val READER_DYNAMIC_BACKGROUND = booleanPreferencesKey("reader_dynamic_background")

    // Image Adjustments
    private val READER_SATURATION = floatPreferencesKey("reader_saturation") // 1.0 = Normal
    private val READER_CONTRAST = floatPreferencesKey("reader_contrast") // 1.0 = Normal
    private val READER_SHARPNESS = floatPreferencesKey("reader_sharpness") // 1.0 = Normal

    // Visibility/Behavior
    private val READER_OPEN_WITH_MENU = booleanPreferencesKey("reader_open_with_menu")
    private val READER_SHOW_TAP_TOASTS = booleanPreferencesKey("reader_show_tap_toasts")

    // Controls
    private val HAPTIC_FEEDBACK = booleanPreferencesKey("haptic_feedback")
    private val TAP_NAVIGATION = booleanPreferencesKey("tap_navigation")
    private val DOUBLE_TAP_ENABLED = booleanPreferencesKey("double_tap_enabled")
    private val PINCH_ZOOM_SENSITIVITY = floatPreferencesKey("pinch_zoom_sensitivity")

    // Tap Zones (9 zones Ã— 3 types: single tap, long tap, double tap = 27 keys)
    // Zone Layout: TopLeft, TopCenter, TopRight, MiddleLeft, Center,
    //             MiddleRight, BottomLeft, BottomCenter, BottomRight
    private val TAP_ZONE_TOP_LEFT = stringPreferencesKey("reader_tapping_top_left") 
    private val TAP_ZONE_TOP_CENTER = stringPreferencesKey("reader_tapping_top_center") 
    private val TAP_ZONE_TOP_RIGHT = stringPreferencesKey("reader_tapping_top_right") 
    private val TAP_ZONE_MIDDLE_LEFT = stringPreferencesKey("reader_tapping_middle_left") 
    private val TAP_ZONE_CENTER = stringPreferencesKey("reader_tapping_center") 
    private val TAP_ZONE_MIDDLE_RIGHT = stringPreferencesKey("reader_tapping_middle_right") 
    private val TAP_ZONE_BOTTOM_LEFT = stringPreferencesKey("reader_tapping_bottom_left") 
    private val TAP_ZONE_BOTTOM_CENTER = stringPreferencesKey("reader_tapping_bottom_center") 
    private val TAP_ZONE_BOTTOM_RIGHT = stringPreferencesKey("reader_tapping_bottom_right") 

    private val LONG_TAP_ZONE_TOP_LEFT = stringPreferencesKey("reader_longtouch_top_left") 
    private val LONG_TAP_ZONE_TOP_CENTER = stringPreferencesKey("reader_longtouch_top_center") 
    private val LONG_TAP_ZONE_TOP_RIGHT = stringPreferencesKey("reader_longtouch_top_right") 
    private val LONG_TAP_ZONE_MIDDLE_LEFT = stringPreferencesKey("reader_longtouch_middle_left") 
    private val LONG_TAP_ZONE_CENTER = stringPreferencesKey("reader_longtouch_center") 
    private val LONG_TAP_ZONE_MIDDLE_RIGHT = stringPreferencesKey("reader_longtouch_middle_right") 
    private val LONG_TAP_ZONE_BOTTOM_LEFT = stringPreferencesKey("reader_longtouch_bottom_left") 
    private val LONG_TAP_ZONE_BOTTOM_CENTER = stringPreferencesKey("reader_longtouch_bottom_center") 
    private val LONG_TAP_ZONE_BOTTOM_RIGHT = stringPreferencesKey("reader_longtouch_bottom_right") 

    private val DOUBLE_TAP_ZONE_TOP_LEFT = stringPreferencesKey("reader_doubletap_top_left") 
    private val DOUBLE_TAP_ZONE_TOP_CENTER = stringPreferencesKey("reader_doubletap_top_center") 
    private val DOUBLE_TAP_ZONE_TOP_RIGHT = stringPreferencesKey("reader_doubletap_top_right") 
    private val DOUBLE_TAP_ZONE_MIDDLE_LEFT = stringPreferencesKey("reader_doubletap_middle_left") 
    private val DOUBLE_TAP_ZONE_CENTER = stringPreferencesKey("reader_doubletap_center") 
    private val DOUBLE_TAP_ZONE_MIDDLE_RIGHT = stringPreferencesKey("reader_doubletap_middle_right") 
    private val DOUBLE_TAP_ZONE_BOTTOM_LEFT = stringPreferencesKey("reader_doubletap_bottom_left") 
    private val DOUBLE_TAP_ZONE_BOTTOM_CENTER = stringPreferencesKey("reader_doubletap_bottom_center") 
    private val DOUBLE_TAP_ZONE_BOTTOM_RIGHT = stringPreferencesKey("reader_doubletap_bottom_right") 


    // Navigation Panel (5 areas Ã— 2: single + long = 10 keys, though some don't have single)
    private val NAV_PANEL_TAP_FORWARD = stringPreferencesKey("reader_navpanel_tap_forward") // Default: NextBookmark
    private val NAV_PANEL_TAP_BACK = stringPreferencesKey("reader_navpanel_tap_back") // Default: PreviousBookmark
    private val NAV_PANEL_TAP_NEXTBOOK = stringPreferencesKey("reader_navpanel_tap_nextbook") // Default: NextComic
    private val NAV_PANEL_TAP_PREVBOOK = stringPreferencesKey("reader_navpanel_tap_prevbook") // Default: PreviousComic

    // Default: ChangePageType
    private val NAV_PANEL_LONG_THUMBNAIL =
        stringPreferencesKey("reader_navpanel_longtouch_thumbnail")
    private val NAV_PANEL_LONG_FORWARD = stringPreferencesKey("reader_navpanel_longtouch_forward") // Default: GotoPage
    private val NAV_PANEL_LONG_BACK = stringPreferencesKey("reader_navpanel_longtouch_back") // Default: FirstPage
    private val NAV_PANEL_LONG_NEXTBOOK = stringPreferencesKey("reader_navpanel_longtouch_nextbook") // Default: None
    private val NAV_PANEL_LONG_PREVBOOK = stringPreferencesKey("reader_navpanel_longtouch_prevbook") // Default: None

    // Hardware Bindings (JSON)
    private val HARDWARE_KEY_BINDINGS_JSON = stringPreferencesKey("hardware_key_bindings_json")

    // Hardware Button Enabled/Disabled State
    private val VOLUME_BUTTONS_ENABLED = booleanPreferencesKey("hardware_volume_buttons_enabled") // Default: true
    private val KEYBOARD_ENABLED = booleanPreferencesKey("hardware_keyboard_enabled") // Default: true (was arrow keys)
    private val GAMEPAD_BUTTONS_ENABLED = booleanPreferencesKey("hardware_gamepad_buttons_enabled") // Default: true
    private val BLUETOOTH_BUTTONS_ENABLED = booleanPreferencesKey("hardware_bluetooth_buttons_enabled") // Default: true

    // Helper settings (3 keys + 1 for reverse touch)
    private val HORIZONTAL_SWIPE_ENABLED = booleanPreferencesKey("reader_horizontal_swipe_enabled") // Default: false
    private val RTL_NAVIGATION_ENABLED = booleanPreferencesKey("reader_rtl_navigation_enabled") // Default: true
    private val SHOW_TAP_AREAS = booleanPreferencesKey("reader_tapping_show_area") // Default: false

    // Wallpaper
    private val ANIMATION_SPEED = floatPreferencesKey("animation_speed")
    private val PARALLAX_EFFECT = booleanPreferencesKey("parallax_effect")
    private val WALLPAPER_COLOR_THEME = stringPreferencesKey("wallpaper_color_theme")
    private val LOW_POWER_MODE = booleanPreferencesKey("low_power_mode")
    private val CENTER_ZONE_SIZE = stringPreferencesKey("reader_center_zone_size") // New: Small, Medium, Large

    // Theme
    private val APP_THEME = stringPreferencesKey("app_theme")
    private val DYNAMIC_COLOR_ENABLED = booleanPreferencesKey("dynamic_color_enabled") // Android 12+ Material You

    // Search
    private val RECENT_SEARCHES =
        androidx.datastore.preferences.core
            .stringSetPreferencesKey("recent_searches")

    // WiFi Sync preferences â€” controls discovery, pairing, and background mode.
    // - WIFI_SYNC_HOME_SSID: SSID that identifies the user's home network for automatic standby.
    // - WIFI_SYNC_FOLDER_URI: SAF tree URI for the sync folder (must have persisted permissions).
    // - Pairing state flags track whether a desktop has been successfully paired.
    // - WIFI_SYNC_BACKGROUND_MODE accepts one of {"off", "charging", "always"}.
    //
    // Important: The service checks `wifi_sync_enabled` and `wifi_sync_home_ssid` before
    // starting standby sessions. The sync folder must be accessible to background services
    // (persisted SAF permission or direct access as configured).
    private val WIFI_SYNC_ENABLED = booleanPreferencesKey("wifi_sync_enabled")
    private val WIFI_SYNC_HOME_SSID = stringPreferencesKey("wifi_sync_home_ssid")
    private val WIFI_SYNC_FOLDER_URI = stringPreferencesKey("wifi_sync_folder_uri")
    private val WIFI_SYNC_FOLDER_NAME = stringPreferencesKey("wifi_sync_folder_name")
    private val WIFI_SYNC_DEVICE_NAME = stringPreferencesKey("wifi_sync_device_name")
    private val WIFI_SYNC_DEVICE_NAME_SOURCE = stringPreferencesKey("wifi_sync_device_name_source")
    private val WIFI_SYNC_DEVICE_ID = stringPreferencesKey("wifi_sync_device_id")
    private val WIFI_SYNC_DEVICE_KEY = stringPreferencesKey("wifi_sync_device_key")

    // Pairing state - tracks if device has ever completed a successful handshake
    private val WIFI_SYNC_IS_PAIRED = booleanPreferencesKey("wifi_sync_is_paired")
    private val WIFI_SYNC_LAST_PAIRED_DESKTOP = stringPreferencesKey("wifi_sync_last_paired_desktop")
    private val WIFI_SYNC_LAST_SYNC_TIMESTAMP = longPreferencesKey("wifi_sync_last_sync_timestamp")

    // Background sync mode: "off", "charging", "always"
    private val WIFI_SYNC_BACKGROUND_MODE = stringPreferencesKey("wifi_sync_background_mode")

    private val moshi =
        com.squareup.moshi.Moshi
            .Builder()
            .build()
    private val hardwareKeyBindingsAdapter = moshi.adapter(HardwareKeyBindings::class.java)


    // ===== Library Preferences =====

    /**
     * Observable library view mode (Grid or List)
     * Default: GRID
     * Lazy initialization to avoid accessing dataStore before init()
     */
    val libraryViewMode: Flow<LibraryViewMode> by lazy {
        dataStore.data.map { prefs ->
            LibraryViewMode.values().find { it.key == prefs[LIBRARY_VIEW_MODE] }
                ?: LibraryViewMode.GRID
        }
    }

    /**
     * Set the library view mode.
     *
     * @param mode the desired [LibraryViewMode]
     */
    suspend fun setLibraryViewMode(mode: LibraryViewMode) {
        dataStore.edit { prefs ->
            prefs[LIBRARY_VIEW_MODE] = mode.key
        }
    }

    /**
     * Observable library sort order (Title, Date Added, Last Read)
     * Default: TITLE
     * Lazy initialization to avoid accessing dataStore before init()
     */
    val librarySortOrder: Flow<SortOrder> by lazy {
        dataStore.data.map { prefs ->
            SortOrder.values().find { it.key == prefs[LIBRARY_SORT_ORDER] }
                ?: SortOrder.TITLE
        }
    }

    /**
     * Set the library sort order.
     *
     * @param order the desired [SortOrder]
     */
    suspend fun setLibrarySortOrder(order: SortOrder) {
        dataStore.edit { prefs ->
            prefs[LIBRARY_SORT_ORDER] = order.key
        }
    }

    /**
     * Phase 3.2: Library folder path selected via Storage Access Framework (SAF) picker
     * Stores the main library folder URI for comic scanning
     */
    val libraryPath: Flow<String?> by lazy {
        dataStore.data.map { prefs ->
            prefs[LIBRARY_PATH]
        }
    }

    /**
     * Persist the main library folder URI selected by the user (SAF).
     *
     * @param path SAF tree URI string identifying the main library folder
     */
    suspend fun setLibraryPath(path: String) {
        dataStore.edit { prefs ->
            prefs[LIBRARY_PATH] = path
        }
    }

    /**
     * Blocking version of libraryPath for debug/testing purposes.
     * Should not be used in production code.
     *
     * Note: This method uses runBlocking which blocks the calling thread.
     * For production code, use the suspend libraryPath property with proper coroutine handling.
     * @see libraryPath
     * @deprecated Use suspend function with proper coroutine handling in production
     */
    @Deprecated(
        "Use suspend libraryPath property with proper coroutine handling",
        replaceWith = ReplaceWith("libraryPath.first()"),
    )
    fun getLibraryPathBlocking(): String? =
        kotlinx.coroutines.runBlocking {
            libraryPath.first()
        }

    /**
     * Additional library folders for multi-folder support
     */
    private val LIBRARY_ADDITIONAL_FOLDERS =
        androidx.datastore.preferences.core.stringSetPreferencesKey(
            "library_additional_folders",
        )

    val libraryAdditionalFolders: Flow<Set<String>> by lazy {
        dataStore.data.map { prefs ->
            prefs[LIBRARY_ADDITIONAL_FOLDERS].orEmpty()
        }
    }

    /**
     * Set the additional library folders for multi-folder support.
     *
     * @param folders set of SAF URIs (strings) representing extra library folders
     */
    suspend fun setLibraryAdditionalFolders(folders: Set<String>) {
        dataStore.edit { prefs ->
            prefs[LIBRARY_ADDITIONAL_FOLDERS] = folders
        }
    }

    /**
     * Startup Cleanup: Ensure library paths are mutually exclusive
     * Called on app startup to fix any legacy state.
     */
    suspend fun sanitizeLibraryPaths() {
        dataStore.edit { prefs ->
            val mainPath = prefs[LIBRARY_PATH]
            val additional = prefs[LIBRARY_ADDITIONAL_FOLDERS]

            if (mainPath != null && additional != null && additional.contains(mainPath)) {
                val cleaned = additional - mainPath
                prefs[LIBRARY_ADDITIONAL_FOLDERS] = cleaned
                // Log/Print removed duplicate if possible
            }
        }
    }

    /**
     * Observable library layout columns (for grid layout)
     * Default: 2 (adaptive based on screen size)
     * Values: 1 (list), 2 (default), 3 (wide), 4+ (ultra-wide)
     */
    val libraryLayoutColumns: Flow<Int> by lazy {
        dataStore.data.map { prefs ->
            prefs[LIBRARY_LAYOUT_COLUMNS]?.toIntOrNull() ?: 2
        }
    }

    /**
     * Set the number of library layout columns (grid).
     * Values are clamped to the range 1..6.
     *
     * @param columns requested column count
     */
    suspend fun setLibraryLayoutColumns(columns: Int) {
        dataStore.edit { prefs ->
            prefs[LIBRARY_LAYOUT_COLUMNS] = columns.coerceIn(MIN_LAYOUT_COLUMNS, MAX_LAYOUT_COLUMNS).toString()
        }
    }

    /**
     * Observable library grid spacing (in dp)
     * Default: 8.0f
     * Range: 0.0f - 32.0f
     */
    val libraryGridSpacing: Flow<Float> by lazy {
        dataStore.data.map { prefs ->
            prefs[LIBRARY_GRID_SPACING] ?: DEFAULT_GRID_SPACING_DP
        }
    }

    /**
     * Set the library grid spacing (in dp).
     * Value is clamped to 0.0..32.0 dp.
     *
     * @param spacing spacing in dp
     */
    suspend fun setLibraryGridSpacing(spacing: Float) {
        dataStore.edit { prefs ->
            prefs[LIBRARY_GRID_SPACING] = spacing.coerceIn(MIN_GRID_SPACING_DP, MAX_GRID_SPACING_DP)
        }
    }

    /**
     * Observable library card elevation (in dp)
     * Default: 4.0f
     * Range: 0.0f - 16.0f
     */
    val libraryCardElevation: Flow<Float> by lazy {
        dataStore.data.map { prefs ->
            prefs[LIBRARY_CARD_ELEVATION] ?: DEFAULT_CARD_ELEVATION_DP
        }
    }

    /**
     * Set the card elevation used in library UI (in dp).
     * Value is clamped to 0.0..16.0 dp.
     *
     * @param elevation elevation in dp
     */
    suspend fun setLibraryCardElevation(elevation: Float) {
        dataStore.edit { prefs ->
            prefs[LIBRARY_CARD_ELEVATION] = elevation.coerceIn(MIN_CARD_ELEVATION_DP, MAX_CARD_ELEVATION_DP)
        }
    }

    /**
     * Observable show comic titles in library
     * Default: true
     */
    val libraryShowTitles: Flow<Boolean> by lazy {
        dataStore.data.map { prefs ->
            prefs[LIBRARY_SHOW_TITLES] ?: true
        }
    }

    /**
     * Toggle visibility of comic titles in the library view.
     *
     * @param show true to show titles, false to hide
     */
    suspend fun setLibraryShowTitles(show: Boolean) {
        dataStore.edit { prefs ->
            prefs[LIBRARY_SHOW_TITLES] = show
        }
    }

    /**
     * Observable enable card animations in library
     * Default: true
     */
    val libraryAnimateCards: Flow<Boolean> by lazy {
        dataStore.data.map { prefs ->
            prefs[LIBRARY_ANIMATE_CARDS] ?: true
        }
    }

    /**
     * Enable or disable card animations in the library.
     *
     * @param animate true to enable animations, false to disable
     */
    suspend fun setLibraryAnimateCards(animate: Boolean) {
        dataStore.edit { prefs ->
            prefs[LIBRARY_ANIMATE_CARDS] = animate
        }
    }

    // ===== General Preferences =====

    /**
     * Observable auto-scan library on startup
     * Default: true
     */
    val autoScanLibrary: Flow<Boolean> by lazy {
        dataStore.data.map { prefs ->
            prefs[AUTO_SCAN_LIBRARY] ?: true
        }
    }

    suspend fun setAutoScanLibrary(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[AUTO_SCAN_LIBRARY] = enabled
        }
    }

    /**
     * Observable recursive scan subdirectories
     * Default: true
     */
    val recursiveScan: Flow<Boolean> by lazy {
        dataStore.data.map { prefs ->
            prefs[RECURSIVE_SCAN] ?: true
        }
    }

    suspend fun setRecursiveScan(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[RECURSIVE_SCAN] = enabled
        }
    }

    /**
     * Observable remember reading position for each comic
     * Default: true
     */
    val rememberReadingPosition: Flow<Boolean> by lazy {
        dataStore.data.map { prefs ->
            prefs[REMEMBER_READING_POSITION] ?: true
        }
    }

    suspend fun setRememberReadingPosition(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[REMEMBER_READING_POSITION] = enabled
        }
    }

    /**
     * Observable use direct file access (MANAGE_EXTERNAL_STORAGE) instead of SAF picker
     * Default: false (use SAF picker by default)
     *
     * When enabled, uses direct file system access which requires the All Files Access
     * permission. This can be useful on devices where the SAF picker has issues.
     */
    val useDirectFileAccess: Flow<Boolean> by lazy {
        dataStore.data.map { prefs ->
            prefs[USE_DIRECT_FILE_ACCESS] ?: false
        }
    }

    suspend fun setUseDirectFileAccess(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[USE_DIRECT_FILE_ACCESS] = enabled
        }
    }

    /**
     * Observable reader mode (Scroll or Page)
     * Default: PAGE
     * Lazy initialization to avoid accessing dataStore before init()
     */
    val readerMode: Flow<ReaderMode> by lazy {
        dataStore.data.map { prefs ->
            ReaderMode.values().find { it.key == prefs[READER_MODE] }
                ?: ReaderMode.PAGE
        }
    }

    suspend fun setReaderMode(mode: ReaderMode) {
        dataStore.edit { prefs ->
            prefs[READER_MODE] = mode.key
        }
    }

    /**
     * Observable flash cover page on reader open
     * Default: true
     * Lazy initialization to avoid accessing dataStore before init()
     */
    val readerFlashCover: Flow<Boolean> by lazy {
        dataStore.data.map { prefs ->
            prefs[READER_FLASH_COVER] ?: true
        }
    }

    suspend fun setReaderFlashCover(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[READER_FLASH_COVER] = enabled
        }
    }

    /**
     * Observable ask for rating after reading
     * Default: true
     * Lazy initialization to avoid accessing dataStore before init()
     */
    val readerAskRating: Flow<Boolean> by lazy {
        dataStore.data.map { prefs ->
            prefs[READER_ASK_RATING] ?: true
        }
    }

    suspend fun setReaderAskRating(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[READER_ASK_RATING] = enabled
        }
    }

    /**
     * Observable show page numbers in reader
     * Default: true
     * Lazy initialization to avoid accessing dataStore before init()
     */
    val readerShowPageNumbers: Flow<Boolean> by lazy {
        dataStore.data.map { prefs ->
            prefs[READER_SHOW_PAGE_NUMBERS] ?: true
        }
    }

    suspend fun setReaderShowPageNumbers(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[READER_SHOW_PAGE_NUMBERS] = enabled
        }
    }

    /**
     * Observable keep screen on during reading
     * Default: true
     * Lazy initialization to avoid accessing dataStore before init()
     */
    val readerKeepScreenOn: Flow<Boolean> by lazy {
        dataStore.data.map { prefs ->
            prefs[READER_KEEP_SCREEN_ON] ?: true
        }
    }

    suspend fun setReaderKeepScreenOn(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[READER_KEEP_SCREEN_ON] = enabled
        }
    }

    /**
     * Observable reader brightness (0.0 to 1.0)
     * Default: 0.5 (system auto-brightness)
     * Lazy initialization to avoid accessing dataStore before init()
     */
    val readerBrightness: Flow<Float> by lazy {
        dataStore.data.map { prefs ->
            prefs[READER_BRIGHTNESS] ?: 0.5f
        }
    }

    /**
     * Observable reader brightness enabled
     * Default: true
     */
    val readerBrightnessEnabled: Flow<Boolean> by lazy {
        dataStore.data.map { prefs ->
            prefs[READER_BRIGHTNESS_ENABLED] ?: true
        }
    }

    /**
     * Set the reader brightness level.
     * Value is clamped to the range 0.0..1.0.
     *
     * @param brightness requested brightness in 0.0..1.0
     */
    suspend fun setReaderBrightness(brightness: Float) {
        // Clamp to valid range
        val clamped = brightness.coerceIn(0.0f, 1.0f)
        dataStore.edit { prefs ->
            prefs[READER_BRIGHTNESS] = clamped
        }
    }

    suspend fun setReaderBrightnessEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[READER_BRIGHTNESS_ENABLED] = enabled
        }
    }

    val readerSaturation: Flow<Float> by lazy {
        dataStore.data.map { prefs -> prefs[READER_SATURATION] ?: DEFAULT_ENHANCEMENT_VALUE }
    }

    suspend fun setReaderSaturation(value: Float) {
        val clamped = value.coerceIn(MIN_ENHANCEMENT_VALUE, MAX_ENHANCEMENT_VALUE)
        dataStore.edit { prefs -> prefs[READER_SATURATION] = clamped }
    }

    val readerContrast: Flow<Float> by lazy {
        dataStore.data.map { prefs -> prefs[READER_CONTRAST] ?: DEFAULT_ENHANCEMENT_VALUE }
    }

    suspend fun setReaderContrast(value: Float) {
        val clamped = value.coerceIn(MIN_ENHANCEMENT_VALUE, MAX_ENHANCEMENT_VALUE)
        dataStore.edit { prefs -> prefs[READER_CONTRAST] = clamped }
    }

    val readerSharpness: Flow<Float> by lazy {
        dataStore.data.map { prefs -> prefs[READER_SHARPNESS] ?: DEFAULT_ENHANCEMENT_VALUE }
    }

    suspend fun setReaderSharpness(value: Float) {
        val clamped = value.coerceIn(MIN_ENHANCEMENT_VALUE, MAX_ENHANCEMENT_VALUE)
        dataStore.edit { prefs -> prefs[READER_SHARPNESS] = clamped }
    }

    val readerOpenWithMenu: Flow<Boolean> by lazy {
        dataStore.data.map { prefs -> prefs[READER_OPEN_WITH_MENU] ?: true }
    }

    suspend fun setReaderOpenWithMenu(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[READER_OPEN_WITH_MENU] = enabled }
    }

    val readerShowTapToasts: Flow<Boolean> by lazy {
        dataStore.data.map { prefs -> prefs[READER_SHOW_TAP_TOASTS] ?: true }
    }

    suspend fun setReaderShowTapToasts(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[READER_SHOW_TAP_TOASTS] = enabled }
    }

    /**
     * Observable page fit mode (FitWidth, FitHeight, FitPage, OriginalSize)
     * Default: FIT_PAGE
     */
    val pageFitMode: Flow<FitMode> by lazy {
        dataStore.data.map { prefs ->
            FitMode.values().find { it.key == prefs[PAGE_FIT_MODE] }
                ?: FitMode.FIT_PAGE
        }
    }

    suspend fun setPageFitMode(mode: FitMode) {
        dataStore.edit { prefs ->
            prefs[PAGE_FIT_MODE] = mode.key
        }
    }

    /**
     * Observable page transitions enabled
     * Default: true
     */
    val pageTransitionsEnabled: Flow<Boolean> by lazy {
        dataStore.data.map { prefs ->
            prefs[PAGE_TRANSITIONS_ENABLED] ?: true
        }
    }

    suspend fun setPageTransitionsEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[PAGE_TRANSITIONS_ENABLED] = enabled
        }
    }

    /**
     * Observable reading direction (LTR or RTL)
     * Default: LTR
     */
    val readingDirection: Flow<ReadingDirection> by lazy {
        dataStore.data.map { prefs ->
            ReadingDirection.values().find { it.key == prefs[READING_DIRECTION] }
                ?: ReadingDirection.LTR
        }
    }

    suspend fun setReadingDirection(direction: ReadingDirection) {
        dataStore.edit { prefs ->
            prefs[READING_DIRECTION] = direction.key
        }
    }

    /**
     * Observable dynamic background color based on page border
     * Default: true
     */
    val readerDynamicBackground: Flow<Boolean> by lazy {
        dataStore.data.map { prefs ->
            prefs[READER_DYNAMIC_BACKGROUND] ?: true
        }
    }

    suspend fun setReaderDynamicBackground(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[READER_DYNAMIC_BACKGROUND] = enabled
        }
    }

    // ===== Controls Preferences =====

    /**
     * Observable haptic feedback toggle
     * Default: true
     */
    val hapticFeedback: Flow<Boolean> by lazy {
        dataStore.data.map { prefs ->
            prefs[HAPTIC_FEEDBACK] ?: true
        }
    }

    suspend fun setHapticFeedback(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[HAPTIC_FEEDBACK] = enabled
        }
    }

    /**
     * Observable tap navigation (left/right edge taps)
     * Default: true
     */
    val tapNavigation: Flow<Boolean> by lazy {
        dataStore.data.map { prefs ->
            prefs[TAP_NAVIGATION] ?: true
        }
    }

    suspend fun setTapNavigation(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[TAP_NAVIGATION] = enabled
        }
    }

    /**
     * Observable double-tap to fit
     * Default: true
     */
    val doubleTapEnabled: Flow<Boolean> by lazy {
        dataStore.data.map { prefs ->
            prefs[DOUBLE_TAP_ENABLED] ?: true
        }
    }

    suspend fun setDoubleTapEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[DOUBLE_TAP_ENABLED] = enabled
        }
    }

    /**
     * Observable pinch-zoom sensitivity (0.25 to 2.0)
     * Default: 1.0 (normal)
     */
    val pinchZoomSensitivity: Flow<Float> by lazy {
        dataStore.data.map { prefs ->
            (prefs[PINCH_ZOOM_SENSITIVITY] ?: 1.0f).coerceIn(0.25f, 2.0f)
        }
    }

    suspend fun setPinchZoomSensitivity(sensitivity: Float) {
        val clamped = sensitivity.coerceIn(MIN_PINCH_ZOOM_SENSITIVITY, MAX_PINCH_ZOOM_SENSITIVITY)
        dataStore.edit { prefs ->
            prefs[PINCH_ZOOM_SENSITIVITY] = clamped
        }
    }

    /**
     * Observable center zone size
     * Default: MEDIUM
     */
    val centerZoneSize: Flow<ZoneSize> by lazy {
        dataStore.data.map { prefs ->
            ZoneSize.values().find { it.key == prefs[CENTER_ZONE_SIZE] }
                ?: ZoneSize.MEDIUM
        }
    }

    suspend fun setCenterZoneSize(size: ZoneSize) {
        dataStore.edit { prefs ->
            prefs[CENTER_ZONE_SIZE] = size.key
        }
    }

    // ===== Tap Zone Preferences (9 zones Ã— 3 types) =====

    val tapZoneTopLeft: Flow<ReaderTapAction> by lazy {
        dataStore.data.map { prefs ->
            ReaderTapAction.fromKey(prefs[TAP_ZONE_TOP_LEFT] ?: "previouspage")
        }
    }

    suspend fun setTapZoneTopLeft(action: ReaderTapAction) {
        dataStore.edit { prefs -> prefs[TAP_ZONE_TOP_LEFT] = action.key }
    }

    val tapZoneTopCenter: Flow<ReaderTapAction> by lazy {
        dataStore.data.map { prefs ->
            ReaderTapAction.fromKey(prefs[TAP_ZONE_TOP_CENTER] ?: "none")
        }
    }

    suspend fun setTapZoneTopCenter(action: ReaderTapAction) {
        dataStore.edit { prefs -> prefs[TAP_ZONE_TOP_CENTER] = action.key }
    }

    val tapZoneTopRight: Flow<ReaderTapAction> by lazy {
        dataStore.data.map { prefs ->
            ReaderTapAction.fromKey(prefs[TAP_ZONE_TOP_RIGHT] ?: "nextpage")
        }
    }

    suspend fun setTapZoneTopRight(action: ReaderTapAction) {
        dataStore.edit { prefs -> prefs[TAP_ZONE_TOP_RIGHT] = action.key }
    }

    val tapZoneMiddleLeft: Flow<ReaderTapAction> by lazy {
        dataStore.data.map { prefs ->
            ReaderTapAction.fromKey(prefs[TAP_ZONE_MIDDLE_LEFT] ?: "previouspage")
        }
    }

    suspend fun setTapZoneMiddleLeft(action: ReaderTapAction) {
        dataStore.edit { prefs -> prefs[TAP_ZONE_MIDDLE_LEFT] = action.key }
    }

    val tapZoneCenter: Flow<ReaderTapAction> by lazy {
        dataStore.data.map { prefs ->
            ReaderTapAction.fromKey(prefs[TAP_ZONE_CENTER] ?: "menu")
        }
    }

    suspend fun setTapZoneCenter(action: ReaderTapAction) {
        dataStore.edit { prefs -> prefs[TAP_ZONE_CENTER] = action.key }
    }

    val tapZoneMiddleRight: Flow<ReaderTapAction> by lazy {
        dataStore.data.map { prefs ->
            ReaderTapAction.fromKey(prefs[TAP_ZONE_MIDDLE_RIGHT] ?: "nextpage")
        }
    }

    suspend fun setTapZoneMiddleRight(action: ReaderTapAction) {
        dataStore.edit { prefs -> prefs[TAP_ZONE_MIDDLE_RIGHT] = action.key }
    }

    val tapZoneBottomLeft: Flow<ReaderTapAction> by lazy {
        dataStore.data.map { prefs ->
            ReaderTapAction.fromKey(prefs[TAP_ZONE_BOTTOM_LEFT] ?: "previouspage")
        }
    }

    suspend fun setTapZoneBottomLeft(action: ReaderTapAction) {
        dataStore.edit { prefs -> prefs[TAP_ZONE_BOTTOM_LEFT] = action.key }
    }

    val tapZoneBottomCenter: Flow<ReaderTapAction> by lazy {
        dataStore.data.map { prefs ->
            ReaderTapAction.fromKey(prefs[TAP_ZONE_BOTTOM_CENTER] ?: "nextpage")
        }
    }

    suspend fun setTapZoneBottomCenter(action: ReaderTapAction) {
        dataStore.edit { prefs -> prefs[TAP_ZONE_BOTTOM_CENTER] = action.key }
    }

    val tapZoneBottomRight: Flow<ReaderTapAction> by lazy {
        dataStore.data.map { prefs ->
            ReaderTapAction.fromKey(prefs[TAP_ZONE_BOTTOM_RIGHT] ?: "nextpage")
        }
    }

    suspend fun setTapZoneBottomRight(action: ReaderTapAction) {
        dataStore.edit { prefs -> prefs[TAP_ZONE_BOTTOM_RIGHT] = action.key }
    }

    // ===== Hardware Key Bindings (JSON) =====

    /**
     * Observable Hardware Keys (Keyboard, Gamepad, Volume)
     * Deserialized from JSON
     */
    val hardwareKeyBindings: Flow<HardwareKeyBindings> by lazy {
        dataStore.data.map { prefs ->
            val json = prefs[HARDWARE_KEY_BINDINGS_JSON]
            if (json != null) {
                try {
                    hardwareKeyBindingsAdapter.fromJson(json) ?: HardwareKeyBindings()
                } catch (e: Exception) {
                    HardwareKeyBindings() // Fallback on error
                }
            } else {
                HardwareKeyBindings()
            }
        }
    }

    suspend fun setHardwareKeyBindings(bindings: HardwareKeyBindings) {
        dataStore.edit { prefs ->
            prefs[HARDWARE_KEY_BINDINGS_JSON] = hardwareKeyBindingsAdapter.toJson(bindings)
        }
    }

    // Long tap zones
    val longTapZoneTopLeft: Flow<ReaderTapAction> by lazy {
        dataStore.data.map { prefs ->
            ReaderTapAction.fromKey(prefs[LONG_TAP_ZONE_TOP_LEFT] ?: "none")
        }
    }

    suspend fun setLongTapZoneTopLeft(action: ReaderTapAction) {
        dataStore.edit { prefs -> prefs[LONG_TAP_ZONE_TOP_LEFT] = action.key }
    }

    val longTapZoneTopCenter: Flow<ReaderTapAction> by lazy {
        dataStore.data.map { prefs ->
            ReaderTapAction.fromKey(prefs[LONG_TAP_ZONE_TOP_CENTER] ?: "gotopage")
        }
    }

    suspend fun setLongTapZoneTopCenter(action: ReaderTapAction) {
        dataStore.edit { prefs -> prefs[LONG_TAP_ZONE_TOP_CENTER] = action.key }
    }

    val longTapZoneTopRight: Flow<ReaderTapAction> by lazy {
        dataStore.data.map { prefs ->
            ReaderTapAction.fromKey(prefs[LONG_TAP_ZONE_TOP_RIGHT] ?: "none")
        }
    }

    suspend fun setLongTapZoneTopRight(action: ReaderTapAction) {
        dataStore.edit { prefs -> prefs[LONG_TAP_ZONE_TOP_RIGHT] = action.key }
    }

    // ===== Theme Extras =====

    /**
     * Observable dynamic color enabled (Material You)
     * Default: false (Use Brand Colors)
     */
    val dynamicColorEnabled: Flow<Boolean> by lazy {
        dataStore.data.map { prefs ->
            prefs[DYNAMIC_COLOR_ENABLED] ?: false
        }
    }

    suspend fun setDynamicColorEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[DYNAMIC_COLOR_ENABLED] = enabled
        }
    }

    // ===== Search History =====

    val recentSearches: Flow<Set<String>> by lazy {
        dataStore.data.map { prefs ->
            prefs[RECENT_SEARCHES].orEmpty()
        }
    }

    /**
     * Add a query to recent searches. Keeps up to 10 recent entries.
     *
     * @param query search query string to add
     */
    suspend fun addRecentSearch(query: String) {
        dataStore.edit { prefs ->
            val current = prefs[RECENT_SEARCHES].orEmpty()
            // Keep max 10, remove duplicates. DataStore returns a Set,
            // order is not guaranteed, UI might need to sort.
            val newSet = (current + query).toList()
                .takeLast(RECENT_SEARCHES_LIMIT).toSet()
            prefs[RECENT_SEARCHES] = newSet
        }
    }

    /**
     * Remove a query from recent searches.
     *
     * @param query the query string to remove
     */
    suspend fun removeRecentSearch(query: String) {
        dataStore.edit { prefs ->
            val current = prefs[RECENT_SEARCHES].orEmpty()
            prefs[RECENT_SEARCHES] = current - query
        }
    }

    /**
     * Clear all recent searches stored in preferences.
     */
    suspend fun clearRecentSearches() {
        dataStore.edit { prefs ->
            prefs.remove(RECENT_SEARCHES)
        }
    }

    // ===== WiFi Sync Preferences =====

    val wifiSyncEnabled: Flow<Boolean> by lazy {
        dataStore.data.map { prefs ->
            prefs[WIFI_SYNC_ENABLED] ?: false
        }
    }

    /**
     * Enable or disable WiFi sync background discovery.
     *
     * @param enabled true to enable WiFi sync, false to disable
     */
    suspend fun setWifiSyncEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[WIFI_SYNC_ENABLED] = enabled
        }
    }

    val wifiSyncHomeSSID: Flow<String> by lazy {
        dataStore.data.map { prefs ->
            prefs[WIFI_SYNC_HOME_SSID] ?: ""
        }
    }

    /**
     * Persist the SSID identifying the user's home WiFi network (used for standby sync heuristics).
     *
     * @param ssid SSID string
     */
    suspend fun setWifiSyncHomeSSID(ssid: String) {
        dataStore.edit { prefs ->
            prefs[WIFI_SYNC_HOME_SSID] = ssid
        }
    }

    val wifiSyncFolderUri: Flow<String?> by lazy {
        dataStore.data.map { prefs ->
            prefs[WIFI_SYNC_FOLDER_URI]
        }
    }

    /**
     * Set or clear the WiFi sync folder SAF URI.
     *
     * @param uri SAF tree URI string, or null to clear the setting
     */
    suspend fun setWifiSyncFolderUri(uri: String?) {
        dataStore.edit { prefs ->
            if (uri != null) {
                prefs[WIFI_SYNC_FOLDER_URI] = uri
            } else {
                prefs.remove(WIFI_SYNC_FOLDER_URI)
            }
        }
    }

    val wifiSyncFolderName: Flow<String> by lazy {
        dataStore.data.map { prefs ->
            prefs[WIFI_SYNC_FOLDER_NAME] ?: ""
        }
    }

    /**
     * Set the human-readable name for the WiFi sync folder.
     *
     * @param name folder display name
     */
    suspend fun setWifiSyncFolderName(name: String) {
        dataStore.edit { prefs ->
            prefs[WIFI_SYNC_FOLDER_NAME] = name
        }
    }

    val wifiSyncDeviceKey: Flow<String> by lazy {
        dataStore.data.map { prefs ->
            prefs[WIFI_SYNC_DEVICE_KEY] ?: ""
        }
    }

    val wifiSyncDeviceName: Flow<String> by lazy {
        dataStore.data.map { prefs ->
            prefs[WIFI_SYNC_DEVICE_NAME] ?: android.os.Build.MODEL
        }
    }

    /**
     * Set the device name used for WiFi sync pairing.
     *
     * @param name device display name
     */
    suspend fun setWifiSyncDeviceName(name: String) {
        dataStore.edit { prefs ->
            prefs[WIFI_SYNC_DEVICE_NAME] = name
        }
    }

    /**
     * Device name source preference: "system" (default), "bluetooth", "model", or "custom"
     * Controls which source is prioritized for device name retrieval.
     */
    val wifiSyncDeviceNameSource: Flow<String> by lazy {
        dataStore.data.map { prefs ->
            prefs[WIFI_SYNC_DEVICE_NAME_SOURCE] ?: "system"
        }
    }

    /**
     * Set the preferred source for device name retrieval.
     *
     * @param source one of: "system", "bluetooth", "model", "custom"
     */
    suspend fun setWifiSyncDeviceNameSource(source: String) {
        dataStore.edit { prefs ->
            prefs[WIFI_SYNC_DEVICE_NAME_SOURCE] = source
        }
    }

    val wifiSyncDeviceId: Flow<String> by lazy {
        dataStore.data.map { prefs ->
            prefs[WIFI_SYNC_DEVICE_ID] ?: ""
        }
    }

    /**
     * Set the persistent WiFi sync device identifier.
     *
     * @param id device id string
     */
    suspend fun setWifiSyncDeviceId(id: String) {
        dataStore.edit { prefs ->
            prefs[WIFI_SYNC_DEVICE_ID] = id
        }
    }

    // ===== WiFi Sync Pairing State =====

    /**
     * Whether this device has ever successfully completed a pairing handshake.
     * Used to determine if we should show "Waiting for desktop" vs "Ready to sync".
     */
    val wifiSyncIsPaired: Flow<Boolean> by lazy {
        dataStore.data.map { prefs ->
            prefs[WIFI_SYNC_IS_PAIRED] ?: false
        }
    }

    /**
     * The hostname/IP of the last desktop we successfully paired with.
     */
    val wifiSyncLastPairedDesktop: Flow<String> by lazy {
        dataStore.data.map { prefs ->
            prefs[WIFI_SYNC_LAST_PAIRED_DESKTOP] ?: ""
        }
    }

    /**
     * Timestamp of the last successful sync completion.
     */
    val wifiSyncLastSyncTimestamp: Flow<Long> by lazy {
        dataStore.data.map { prefs ->
            prefs[WIFI_SYNC_LAST_SYNC_TIMESTAMP] ?: 0L
        }
    }

    /**
     * Record a successful pairing event.
     * Called when Info handshake completes without error.
     */

    /**
     * Record a successful WiFi sync pairing event with a desktop.
     *
     * @param desktopAddress hostname or IP of the paired desktop
     */
    suspend fun recordSuccessfulPairing(desktopAddress: String) {
        dataStore.edit { prefs ->
            prefs[WIFI_SYNC_IS_PAIRED] = true
            prefs[WIFI_SYNC_LAST_PAIRED_DESKTOP] = desktopAddress
        }
    }

    /**
     * Record a successful sync completion.
     */

    /**
     * Record the timestamp of a successful WiFi sync completion.
     */
    suspend fun recordSuccessfulSync() {
        dataStore.edit { prefs ->
            prefs[WIFI_SYNC_LAST_SYNC_TIMESTAMP] = System.currentTimeMillis()
        }
    }

    val wifiSyncBackgroundMode: Flow<BackgroundSyncMode> by lazy {
        dataStore.data.map { prefs ->
            BackgroundSyncMode.fromKey(prefs[WIFI_SYNC_BACKGROUND_MODE] ?: "charging")
        }
    }

    suspend fun setWifiSyncBackgroundMode(mode: BackgroundSyncMode) {
        dataStore.edit { prefs ->
            prefs[WIFI_SYNC_BACKGROUND_MODE] = mode.key
        }
    }

    /**
     * Get or create a unique device key for sync pairing.
     *
     * NOTE: For legacy ComicRack CE compatibility, we MUST use the hard-coded
     * AndroidKey. The desktop validates against this exact key value.
     */
    suspend fun getOrCreateDeviceKey(): String {
        // Always return the legacy key for ComicRack CE compatibility
        // The desktop hardcodes validation against this specific key
        return LEGACY_ANDROID_KEY
    }

    val longTapZoneMiddleLeft: Flow<ReaderTapAction> by lazy {
        dataStore.data.map { prefs ->
            ReaderTapAction.fromKey(prefs[LONG_TAP_ZONE_MIDDLE_LEFT] ?: "none")
        }
    }

    suspend fun setLongTapZoneMiddleLeft(action: ReaderTapAction) {
        dataStore.edit { prefs -> prefs[LONG_TAP_ZONE_MIDDLE_LEFT] = action.key }
    }

    val longTapZoneCenter: Flow<ReaderTapAction> by lazy {
        dataStore.data.map { prefs ->
            ReaderTapAction.fromKey(prefs[LONG_TAP_ZONE_CENTER] ?: "showzoom")
        }
    }

    suspend fun setLongTapZoneCenter(action: ReaderTapAction) {
        dataStore.edit { prefs -> prefs[LONG_TAP_ZONE_CENTER] = action.key }
    }

    val longTapZoneMiddleRight: Flow<ReaderTapAction> by lazy {
        dataStore.data.map { prefs ->
            ReaderTapAction.fromKey(prefs[LONG_TAP_ZONE_MIDDLE_RIGHT] ?: "none")
        }
    }

    suspend fun setLongTapZoneMiddleRight(action: ReaderTapAction) {
        dataStore.edit { prefs -> prefs[LONG_TAP_ZONE_MIDDLE_RIGHT] = action.key }
    }

    val longTapZoneBottomLeft: Flow<ReaderTapAction> by lazy {
        dataStore.data.map { prefs ->
            ReaderTapAction.fromKey(prefs[LONG_TAP_ZONE_BOTTOM_LEFT] ?: "none")
        }
    }

    suspend fun setLongTapZoneBottomLeft(action: ReaderTapAction) {
        dataStore.edit { prefs -> prefs[LONG_TAP_ZONE_BOTTOM_LEFT] = action.key }
    }

    val longTapZoneBottomCenter: Flow<ReaderTapAction> by lazy {
        dataStore.data.map { prefs ->
            ReaderTapAction.fromKey(prefs[LONG_TAP_ZONE_BOTTOM_CENTER] ?: "none")
        }
    }

    suspend fun setLongTapZoneBottomCenter(action: ReaderTapAction) {
        dataStore.edit { prefs -> prefs[LONG_TAP_ZONE_BOTTOM_CENTER] = action.key }
    }

    val longTapZoneBottomRight: Flow<ReaderTapAction> by lazy {
        dataStore.data.map { prefs ->
            ReaderTapAction.fromKey(prefs[LONG_TAP_ZONE_BOTTOM_RIGHT] ?: "none")
        }
    }

    suspend fun setLongTapZoneBottomRight(action: ReaderTapAction) {
        dataStore.edit { prefs -> prefs[LONG_TAP_ZONE_BOTTOM_RIGHT] = action.key }
    }

    // ===== Double Tap Zones =====

    val doubleTapZoneTopLeft: Flow<ReaderTapAction> by lazy {
        dataStore.data.map { prefs ->
            ReaderTapAction.fromKey(prefs[DOUBLE_TAP_ZONE_TOP_LEFT] ?: "none")
        }
    }

    val doubleTapZoneTopCenter: Flow<ReaderTapAction> by lazy {
        dataStore.data.map { prefs ->
            ReaderTapAction.fromKey(prefs[DOUBLE_TAP_ZONE_TOP_CENTER] ?: "none")
        }
    }

    val doubleTapZoneTopRight: Flow<ReaderTapAction> by lazy {
        dataStore.data.map { prefs ->
            ReaderTapAction.fromKey(prefs[DOUBLE_TAP_ZONE_TOP_RIGHT] ?: "none")
        }
    }

    val doubleTapZoneMiddleLeft: Flow<ReaderTapAction> by lazy {
        dataStore.data.map { prefs ->
            ReaderTapAction.fromKey(prefs[DOUBLE_TAP_ZONE_MIDDLE_LEFT] ?: "none")
        }
    }

    val doubleTapZoneCenter: Flow<ReaderTapAction> by lazy {
        dataStore.data.map { prefs ->
            ReaderTapAction.fromKey(prefs[DOUBLE_TAP_ZONE_CENTER] ?: "none")
        }
    }

    val doubleTapZoneMiddleRight: Flow<ReaderTapAction> by lazy {
        dataStore.data.map { prefs ->
            ReaderTapAction.fromKey(prefs[DOUBLE_TAP_ZONE_MIDDLE_RIGHT] ?: "none")
        }
    }

    val doubleTapZoneBottomLeft: Flow<ReaderTapAction> by lazy {
        dataStore.data.map { prefs ->
            ReaderTapAction.fromKey(prefs[DOUBLE_TAP_ZONE_BOTTOM_LEFT] ?: "none")
        }
    }

    val doubleTapZoneBottomCenter: Flow<ReaderTapAction> by lazy {
        dataStore.data.map { prefs ->
            ReaderTapAction.fromKey(prefs[DOUBLE_TAP_ZONE_BOTTOM_CENTER] ?: "none")
        }
    }

    val doubleTapZoneBottomRight: Flow<ReaderTapAction> by lazy {
        dataStore.data.map { prefs ->
            ReaderTapAction.fromKey(prefs[DOUBLE_TAP_ZONE_BOTTOM_RIGHT] ?: "none")
        }
    }

    suspend fun setDoubleTapZoneTopLeft(action: ReaderTapAction) {
        dataStore.edit { prefs -> prefs[DOUBLE_TAP_ZONE_TOP_LEFT] = action.key }
    }

    suspend fun setDoubleTapZoneTopCenter(action: ReaderTapAction) {
        dataStore.edit { prefs -> prefs[DOUBLE_TAP_ZONE_TOP_CENTER] = action.key }
    }

    suspend fun setDoubleTapZoneTopRight(action: ReaderTapAction) {
        dataStore.edit { prefs -> prefs[DOUBLE_TAP_ZONE_TOP_RIGHT] = action.key }
    }

    suspend fun setDoubleTapZoneMiddleLeft(action: ReaderTapAction) {
        dataStore.edit { prefs -> prefs[DOUBLE_TAP_ZONE_MIDDLE_LEFT] = action.key }
    }

    suspend fun setDoubleTapZoneCenter(action: ReaderTapAction) {
        dataStore.edit { prefs -> prefs[DOUBLE_TAP_ZONE_CENTER] = action.key }
    }

    suspend fun setDoubleTapZoneMiddleRight(action: ReaderTapAction) {
        dataStore.edit { prefs -> prefs[DOUBLE_TAP_ZONE_MIDDLE_RIGHT] = action.key }
    }

    suspend fun setDoubleTapZoneBottomLeft(action: ReaderTapAction) {
        dataStore.edit { prefs -> prefs[DOUBLE_TAP_ZONE_BOTTOM_LEFT] = action.key }
    }

    suspend fun setDoubleTapZoneBottomCenter(action: ReaderTapAction) {
        dataStore.edit { prefs -> prefs[DOUBLE_TAP_ZONE_BOTTOM_CENTER] = action.key }
    }

    suspend fun setDoubleTapZoneBottomRight(action: ReaderTapAction) {
        dataStore.edit { prefs -> prefs[DOUBLE_TAP_ZONE_BOTTOM_RIGHT] = action.key }
    }

    // ===== Navigation Panel Preferences =====

    val navPanelTapForward: Flow<ReaderTapAction> by lazy {
        dataStore.data.map { prefs ->
            ReaderTapAction.fromKey(prefs[NAV_PANEL_TAP_FORWARD] ?: "nextbookmark")
        }
    }

    suspend fun setNavPanelTapForward(action: ReaderTapAction) {
        dataStore.edit { prefs -> prefs[NAV_PANEL_TAP_FORWARD] = action.key }
    }

    val navPanelTapBack: Flow<ReaderTapAction> by lazy {
        dataStore.data.map { prefs ->
            ReaderTapAction.fromKey(prefs[NAV_PANEL_TAP_BACK] ?: "previousbookmark")
        }
    }

    suspend fun setNavPanelTapBack(action: ReaderTapAction) {
        dataStore.edit { prefs -> prefs[NAV_PANEL_TAP_BACK] = action.key }
    }

    val navPanelTapNextBook: Flow<ReaderTapAction> by lazy {
        dataStore.data.map { prefs ->
            ReaderTapAction.fromKey(prefs[NAV_PANEL_TAP_NEXTBOOK] ?: "nextcomic")
        }
    }

    suspend fun setNavPanelTapNextBook(action: ReaderTapAction) {
        dataStore.edit { prefs -> prefs[NAV_PANEL_TAP_NEXTBOOK] = action.key }
    }

    val navPanelTapPrevBook: Flow<ReaderTapAction> by lazy {
        dataStore.data.map { prefs ->
            ReaderTapAction.fromKey(prefs[NAV_PANEL_TAP_PREVBOOK] ?: "previouscomic")
        }
    }

    suspend fun setNavPanelTapPrevBook(action: ReaderTapAction) {
        dataStore.edit { prefs -> prefs[NAV_PANEL_TAP_PREVBOOK] = action.key }
    }

    val navPanelLongThumbnail: Flow<ReaderTapAction> by lazy {
        dataStore.data.map { prefs ->
            ReaderTapAction.fromKey(prefs[NAV_PANEL_LONG_THUMBNAIL] ?: "changepagetype")
        }
    }

    suspend fun setNavPanelLongThumbnail(action: ReaderTapAction) {
        dataStore.edit { prefs -> prefs[NAV_PANEL_LONG_THUMBNAIL] = action.key }
    }

    val navPanelLongForward: Flow<ReaderTapAction> by lazy {
        dataStore.data.map { prefs ->
            ReaderTapAction.fromKey(prefs[NAV_PANEL_LONG_FORWARD] ?: "gotopage")
        }
    }

    suspend fun setNavPanelLongForward(action: ReaderTapAction) {
        dataStore.edit { prefs -> prefs[NAV_PANEL_LONG_FORWARD] = action.key }
    }

    val navPanelLongBack: Flow<ReaderTapAction> by lazy {
        dataStore.data.map { prefs ->
            ReaderTapAction.fromKey(prefs[NAV_PANEL_LONG_BACK] ?: "firstpage")
        }
    }

    suspend fun setNavPanelLongBack(action: ReaderTapAction) {
        dataStore.edit { prefs -> prefs[NAV_PANEL_LONG_BACK] = action.key }
    }

    val navPanelLongNextBook: Flow<ReaderTapAction> by lazy {
        dataStore.data.map { prefs ->
            ReaderTapAction.fromKey(prefs[NAV_PANEL_LONG_NEXTBOOK] ?: "none")
        }
    }

    suspend fun setNavPanelLongNextBook(action: ReaderTapAction) {
        dataStore.edit { prefs -> prefs[NAV_PANEL_LONG_NEXTBOOK] = action.key }
    }

    val navPanelLongPrevBook: Flow<ReaderTapAction> by lazy {
        dataStore.data.map { prefs ->
            ReaderTapAction.fromKey(prefs[NAV_PANEL_LONG_PREVBOOK] ?: "none")
        }
    }

    suspend fun setNavPanelLongPrevBook(action: ReaderTapAction) {
        dataStore.edit { prefs -> prefs[NAV_PANEL_LONG_PREVBOOK] = action.key }
    }

    // ===== Hardware Button Enable/Disable =====

    val volumeButtonsEnabled: Flow<Boolean> by lazy {
        dataStore.data.map { prefs ->
            prefs[VOLUME_BUTTONS_ENABLED] ?: true
        }
    }

    suspend fun setVolumeButtonsEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[VOLUME_BUTTONS_ENABLED] = enabled }
    }

    val arrowKeysEnabled: Flow<Boolean> by lazy {
        dataStore.data.map { prefs ->
            prefs[KEYBOARD_ENABLED] ?: true
        }
    }

    suspend fun setKeyboardEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[KEYBOARD_ENABLED] = enabled }
    }

    val gamepadButtonsEnabled: Flow<Boolean> by lazy {
        dataStore.data.map { prefs ->
            prefs[GAMEPAD_BUTTONS_ENABLED] ?: true
        }
    }

    suspend fun setGamepadButtonsEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[GAMEPAD_BUTTONS_ENABLED] = enabled }
    }

    val bluetoothButtonsEnabled: Flow<Boolean> by lazy {
        dataStore.data.map { prefs ->
            prefs[BLUETOOTH_BUTTONS_ENABLED] ?: true
        }
    }

    suspend fun setBluetoothButtonsEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[BLUETOOTH_BUTTONS_ENABLED] = enabled }
    }

    val horizontalSwipeEnabled: Flow<Boolean> by lazy {
        dataStore.data.map { prefs ->
            prefs[HORIZONTAL_SWIPE_ENABLED] ?: false
        }
    }

    suspend fun setHorizontalSwipeEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[HORIZONTAL_SWIPE_ENABLED] = enabled }
    }

    val rtlNavigationEnabled: Flow<Boolean> by lazy {
        dataStore.data.map { prefs ->
            prefs[RTL_NAVIGATION_ENABLED] ?: true
        }
    }

    suspend fun setRtlNavigationEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[RTL_NAVIGATION_ENABLED] = enabled }
    }

    val showTapAreas: Flow<Boolean> by lazy {
        dataStore.data.map { prefs ->
            prefs[SHOW_TAP_AREAS] ?: false
        }
    }

    suspend fun setShowTapAreas(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[SHOW_TAP_AREAS] = enabled }
    }

    // ===== Wallpaper Preferences =====

    /**
     * Observable animation speed (0.25 to 2.0)
     * Default: 1.0 (normal)
     */
    val animationSpeed: Flow<Float> by lazy {
        dataStore.data.map { prefs ->
            (prefs[ANIMATION_SPEED] ?: 1.0f).coerceIn(0.25f, 2.0f)
        }
    }

    suspend fun setAnimationSpeed(speed: Float) {
        val clamped = speed.coerceIn(MIN_ANIMATION_SPEED, MAX_ANIMATION_SPEED)
        dataStore.edit { prefs ->
            prefs[ANIMATION_SPEED] = clamped
        }
    }

    /**
     * Observable parallax effect enabled
     * Default: true
     */
    val parallaxEffectEnabled: Flow<Boolean> by lazy {
        dataStore.data.map { prefs ->
            prefs[PARALLAX_EFFECT] ?: true
        }
    }

    suspend fun setParallaxEffectEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[PARALLAX_EFFECT] = enabled
        }
    }

    /**
     * Observable wallpaper color theme (Auto, Light, Dark)
     * Default: AUTO
     */
    val wallpaperColorTheme: Flow<ColorTheme> by lazy {
        dataStore.data.map { prefs ->
            ColorTheme.values().find { it.key == prefs[WALLPAPER_COLOR_THEME] }
                ?: ColorTheme.AUTO
        }
    }

    suspend fun setWallpaperColorTheme(theme: ColorTheme) {
        dataStore.edit { prefs ->
            prefs[WALLPAPER_COLOR_THEME] = theme.key
        }
    }

    /**
     * Observable low power mode (battery optimization)
     * Default: false
     */
    val lowPowerMode: Flow<Boolean> by lazy {
        dataStore.data.map { prefs ->
            prefs[LOW_POWER_MODE] ?: false
        }
    }

    suspend fun setLowPowerMode(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[LOW_POWER_MODE] = enabled
        }
    }

    /**
     * Observable app theme (System, Light, Dark)
     * Default: SYSTEM
     * Lazy initialization to avoid accessing dataStore before init()
     */
    val appTheme: Flow<AppTheme> by lazy {
        dataStore.data.map { prefs ->
            AppTheme.values().find { it.key == prefs[APP_THEME] }
                ?: AppTheme.SYSTEM
        }
    }

    suspend fun setAppTheme(theme: AppTheme) {
        dataStore.edit { prefs ->
            prefs[APP_THEME] = theme.key
        }
    }

    // ===== Composite Flows =====

    /**
     * All preferences bundled for snapshot consumption
     * Useful for ViewModel state aggregation
     * Lazy initialization to avoid accessing dataStore before init()
     */
    val allPreferences: Flow<UserPreferences> by lazy {
        dataStore.data.map { prefs ->
            UserPreferences(
                libraryViewMode =
                    LibraryViewMode.values().find { it.key == prefs[LIBRARY_VIEW_MODE] }
                        ?: LibraryViewMode.GRID,
                librarySortOrder =
                    SortOrder.values().find { it.key == prefs[LIBRARY_SORT_ORDER] }
                        ?: SortOrder.TITLE,
                libraryPath = prefs[LIBRARY_PATH], // Phase 3.2: Read library path from preferences
                libraryAdditionalFolders = prefs[LIBRARY_ADDITIONAL_FOLDERS].orEmpty(),
                // ===== Phase 8 Task 4: Layout Preferences =====
                libraryLayoutColumns = prefs[LIBRARY_LAYOUT_COLUMNS]?.toIntOrNull() ?: 2,
                libraryGridSpacing = prefs[LIBRARY_GRID_SPACING] ?: 8.0f,
                libraryCardElevation = prefs[LIBRARY_CARD_ELEVATION] ?: 4.0f,
                libraryShowTitles = prefs[LIBRARY_SHOW_TITLES] ?: true,
                libraryAnimateCards = prefs[LIBRARY_ANIMATE_CARDS] ?: true,
                // ===== Original Preferences =====
                autoScanLibrary = prefs[AUTO_SCAN_LIBRARY] ?: true,
                recursiveScan = prefs[RECURSIVE_SCAN] ?: true,
                rememberReadingPosition = prefs[REMEMBER_READING_POSITION] ?: true,
                useDirectFileAccess = prefs[USE_DIRECT_FILE_ACCESS] ?: false,
                readerMode =
                    ReaderMode.values().find { it.key == prefs[READER_MODE] }
                        ?: ReaderMode.PAGE,
                readerFlashCover = prefs[READER_FLASH_COVER] ?: true,
                readerAskRating = prefs[READER_ASK_RATING] ?: true,
                readerShowPageNumbers = prefs[READER_SHOW_PAGE_NUMBERS] ?: true,
                readerKeepScreenOn = prefs[READER_KEEP_SCREEN_ON] ?: true,
                readerDynamicBackground = prefs[READER_DYNAMIC_BACKGROUND] ?: true,
                readerBrightness = (prefs[READER_BRIGHTNESS] ?: 0.5f).coerceIn(0.0f, 1.0f),
                readerBrightnessEnabled = prefs[READER_BRIGHTNESS_ENABLED] ?: true,
                pageFitMode =
                    FitMode.values().find { it.key == prefs[PAGE_FIT_MODE] }
                        ?: FitMode.FIT_PAGE,
                pageTransitionsEnabled = prefs[PAGE_TRANSITIONS_ENABLED] ?: true,
                readingDirection =
                    ReadingDirection.values().find { it.key == prefs[READING_DIRECTION] }
                        ?: ReadingDirection.LTR,
                hapticFeedback = prefs[HAPTIC_FEEDBACK] ?: true,
                tapNavigation = prefs[TAP_NAVIGATION] ?: true,
                doubleTapEnabled = prefs[DOUBLE_TAP_ENABLED] ?: true,
                pinchZoomSensitivity = (prefs[PINCH_ZOOM_SENSITIVITY] ?: 1.0f).coerceIn(0.25f, 2.0f),
                centerZoneSize = ZoneSize.values().find { it.key == prefs[CENTER_ZONE_SIZE] } ?: ZoneSize.MEDIUM,
                // Tap zones - Fixed to follow 1-9 grid (Left=Prev, Right=Next, Center=Menu)
                // Tap zones - Fixed to follow 1-9 grid (Left=Prev, Right=Next, Center=Menu)
                tapZoneTopLeft = ReaderTapAction.fromKey(prefs[TAP_ZONE_TOP_LEFT] ?: "previouspage"),
                tapZoneTopCenter = ReaderTapAction.fromKey(prefs[TAP_ZONE_TOP_CENTER] ?: "none"),
                tapZoneTopRight = ReaderTapAction.fromKey(prefs[TAP_ZONE_TOP_RIGHT] ?: "nextpage"),
                tapZoneMiddleLeft = ReaderTapAction.fromKey(prefs[TAP_ZONE_MIDDLE_LEFT] ?: "previouspage"),
                tapZoneCenter = ReaderTapAction.fromKey(prefs[TAP_ZONE_CENTER] ?: "menu"),
                tapZoneMiddleRight = ReaderTapAction.fromKey(prefs[TAP_ZONE_MIDDLE_RIGHT] ?: "nextpage"),
                tapZoneBottomLeft = ReaderTapAction.fromKey(prefs[TAP_ZONE_BOTTOM_LEFT] ?: "previouspage"),
                tapZoneBottomCenter = ReaderTapAction.fromKey(prefs[TAP_ZONE_BOTTOM_CENTER] ?: "nextpage"),
                tapZoneBottomRight = ReaderTapAction.fromKey(prefs[TAP_ZONE_BOTTOM_RIGHT] ?: "nextpage"),
                // Long tap zones
                longTapZoneTopLeft = ReaderTapAction.fromKey(prefs[LONG_TAP_ZONE_TOP_LEFT] ?: "none"),
                longTapZoneTopCenter = ReaderTapAction.fromKey(prefs[LONG_TAP_ZONE_TOP_CENTER] ?: "gotopage"),
                longTapZoneTopRight = ReaderTapAction.fromKey(prefs[LONG_TAP_ZONE_TOP_RIGHT] ?: "none"),
                longTapZoneMiddleLeft = ReaderTapAction.fromKey(prefs[LONG_TAP_ZONE_MIDDLE_LEFT] ?: "none"),
                longTapZoneCenter = ReaderTapAction.fromKey(prefs[LONG_TAP_ZONE_CENTER] ?: "showzoom"),
                longTapZoneMiddleRight = ReaderTapAction.fromKey(prefs[LONG_TAP_ZONE_MIDDLE_RIGHT] ?: "none"),
                longTapZoneBottomLeft = ReaderTapAction.fromKey(prefs[LONG_TAP_ZONE_BOTTOM_LEFT] ?: "none"),
                longTapZoneBottomCenter = ReaderTapAction.fromKey(prefs[LONG_TAP_ZONE_BOTTOM_CENTER] ?: "none"),
                longTapZoneBottomRight = ReaderTapAction.fromKey(prefs[LONG_TAP_ZONE_BOTTOM_RIGHT] ?: "none"),
                // Double tap zones
                doubleTapZoneTopLeft = ReaderTapAction.fromKey(prefs[DOUBLE_TAP_ZONE_TOP_LEFT] ?: "none"),
                doubleTapZoneTopCenter = ReaderTapAction.fromKey(prefs[DOUBLE_TAP_ZONE_TOP_CENTER] ?: "none"),
                doubleTapZoneTopRight = ReaderTapAction.fromKey(prefs[DOUBLE_TAP_ZONE_TOP_RIGHT] ?: "none"),
                doubleTapZoneMiddleLeft = ReaderTapAction.fromKey(prefs[DOUBLE_TAP_ZONE_MIDDLE_LEFT] ?: "none"),
                doubleTapZoneCenter = ReaderTapAction.fromKey(prefs[DOUBLE_TAP_ZONE_CENTER] ?: "none"),
                doubleTapZoneMiddleRight = ReaderTapAction.fromKey(prefs[DOUBLE_TAP_ZONE_MIDDLE_RIGHT] ?: "none"),
                doubleTapZoneBottomLeft = ReaderTapAction.fromKey(prefs[DOUBLE_TAP_ZONE_BOTTOM_LEFT] ?: "none"),
                doubleTapZoneBottomCenter = ReaderTapAction.fromKey(prefs[DOUBLE_TAP_ZONE_BOTTOM_CENTER] ?: "none"),
                doubleTapZoneBottomRight = ReaderTapAction.fromKey(prefs[DOUBLE_TAP_ZONE_BOTTOM_RIGHT] ?: "none"),
                // Navigation panel
                navPanelTapForward = ReaderTapAction.fromKey(prefs[NAV_PANEL_TAP_FORWARD] ?: "nextbookmark"),
                // Image Adjustments
                readerSaturation = prefs[READER_SATURATION] ?: 1.0f,
                readerContrast = prefs[READER_CONTRAST] ?: 1.0f,
                readerSharpness = prefs[READER_SHARPNESS] ?: 1.0f,
                // Visibility/Behavior
                readerOpenWithMenu = prefs[READER_OPEN_WITH_MENU] ?: true,
                readerShowTapToasts = prefs[READER_SHOW_TAP_TOASTS] ?: true,
                navPanelTapBack = ReaderTapAction.fromKey(prefs[NAV_PANEL_TAP_BACK] ?: "previousbookmark"),
                navPanelTapNextBook = ReaderTapAction.fromKey(prefs[NAV_PANEL_TAP_NEXTBOOK] ?: "nextcomic"),
                navPanelTapPrevBook = ReaderTapAction.fromKey(prefs[NAV_PANEL_TAP_PREVBOOK] ?: "previouscomic"),
                navPanelLongThumbnail = ReaderTapAction.fromKey(prefs[NAV_PANEL_LONG_THUMBNAIL] ?: "changepagetype"),
                navPanelLongForward = ReaderTapAction.fromKey(prefs[NAV_PANEL_LONG_FORWARD] ?: "gotopage"),
                navPanelLongBack = ReaderTapAction.fromKey(prefs[NAV_PANEL_LONG_BACK] ?: "firstpage"),
                navPanelLongNextBook = ReaderTapAction.fromKey(prefs[NAV_PANEL_LONG_NEXTBOOK] ?: "none"),
                navPanelLongPrevBook = ReaderTapAction.fromKey(prefs[NAV_PANEL_LONG_PREVBOOK] ?: "none"),
                // Hardware Keys (Parsed from JSON)
                hardwareKeyBindings =
                    prefs[HARDWARE_KEY_BINDINGS_JSON]?.let { json ->
                        try {
                            hardwareKeyBindingsAdapter.fromJson(json) ?: HardwareKeyBindings()
                        } catch (e: Exception) {
                            HardwareKeyBindings()
                        }
                    } ?: HardwareKeyBindings(),
                // Hardware button enabled/disabled
                volumeButtonsEnabled = prefs[VOLUME_BUTTONS_ENABLED] ?: true,
                keyboardEnabled = prefs[KEYBOARD_ENABLED] ?: true,
                gamepadButtonsEnabled = prefs[GAMEPAD_BUTTONS_ENABLED] ?: true,
                bluetoothButtonsEnabled = prefs[BLUETOOTH_BUTTONS_ENABLED] ?: true,
                // Helper settings
                horizontalSwipeEnabled = prefs[HORIZONTAL_SWIPE_ENABLED] ?: true,
                rtlNavigationEnabled = prefs[RTL_NAVIGATION_ENABLED] ?: true,
                showTapAreas = prefs[SHOW_TAP_AREAS] ?: false,
                // Wallpaper
                animationSpeed = (prefs[ANIMATION_SPEED] ?: 1.0f).coerceIn(0.25f, 2.0f),
                parallaxEffectEnabled = prefs[PARALLAX_EFFECT] ?: true,
                wallpaperColorTheme =
                    ColorTheme.values().find { it.key == prefs[WALLPAPER_COLOR_THEME] }
                        ?: ColorTheme.AUTO,
                lowPowerMode = prefs[LOW_POWER_MODE] ?: false,
                appTheme =
                    AppTheme.values().find { it.key == prefs[APP_THEME] }
                        ?: AppTheme.SYSTEM,
                dynamicColorEnabled = prefs[DYNAMIC_COLOR_ENABLED] ?: false,
                recentSearches = prefs[RECENT_SEARCHES].orEmpty(),
                // WiFi Sync
                wifiSyncEnabled = prefs[WIFI_SYNC_ENABLED] ?: false,
                wifiSyncHomeSSID = prefs[WIFI_SYNC_HOME_SSID] ?: "",
                wifiSyncFolderUri = prefs[WIFI_SYNC_FOLDER_URI],
                wifiSyncFolderName = prefs[WIFI_SYNC_FOLDER_NAME] ?: "",
                // WiFi Sync Identity
                wifiSyncDeviceName = prefs[WIFI_SYNC_DEVICE_NAME] ?: android.os.Build.MODEL,
                wifiSyncDeviceId = prefs[WIFI_SYNC_DEVICE_ID] ?: "",
                // WiFi Sync Pairing State
                wifiSyncIsPaired = prefs[WIFI_SYNC_IS_PAIRED] ?: false,
                wifiSyncLastPairedDesktop = prefs[WIFI_SYNC_LAST_PAIRED_DESKTOP] ?: "",
                wifiSyncLastSyncTimestamp = prefs[WIFI_SYNC_LAST_SYNC_TIMESTAMP] ?: 0L,
                wifiSyncBackgroundMode = BackgroundSyncMode.fromKey(prefs[WIFI_SYNC_BACKGROUND_MODE] ?: "charging"),
                // UI state
                isLoading = false,
                isScanning = false,
                error = null,
            )
        }
    }
}

/**
 * Data class representing all user preferences
 * Snapshot/immutable representation of preferences state at a point in time
 */
data class UserPreferences(
    val libraryViewMode: LibraryViewMode,
    val librarySortOrder: SortOrder,
    val libraryPath: String? = null, // Phase 3.2: Main library folder selected via SAF picker
    val libraryAdditionalFolders: Set<String> = emptySet(),
    // ===== Phase 8 Task 4: Layout Preferences =====
    val libraryLayoutColumns: Int = 2,
    val libraryGridSpacing: Float = 8.0f,
    val libraryCardElevation: Float = 4.0f,
    val libraryShowTitles: Boolean = true,
    val libraryAnimateCards: Boolean = true,
    // ===== Original Preferences =====
    val autoScanLibrary: Boolean,
    val recursiveScan: Boolean,
    val rememberReadingPosition: Boolean,
    val useDirectFileAccess: Boolean = false,
    val readerMode: ReaderMode,
    val readerFlashCover: Boolean,
    val readerAskRating: Boolean,
    val readerShowPageNumbers: Boolean,
    val readerKeepScreenOn: Boolean,
    val readerDynamicBackground: Boolean, // New
    val readerBrightness: Float,
    val readerBrightnessEnabled: Boolean,
    val pageFitMode: FitMode,
    val pageTransitionsEnabled: Boolean,
    val readingDirection: ReadingDirection,
    val hapticFeedback: Boolean,
    val tapNavigation: Boolean,
    val doubleTapEnabled: Boolean,
    val pinchZoomSensitivity: Float,
    val centerZoneSize: ZoneSize = ZoneSize.MEDIUM,
    // Tap zones (9 zones Ã— 2: single + long = 18)
    // Tap zones (9 zones Ã— 3: single + long + double = 27)
    val tapZoneTopLeft: ReaderTapAction,
    val tapZoneTopCenter: ReaderTapAction,
    val tapZoneTopRight: ReaderTapAction,
    val tapZoneMiddleLeft: ReaderTapAction,
    val tapZoneCenter: ReaderTapAction,
    val tapZoneMiddleRight: ReaderTapAction,
    val tapZoneBottomLeft: ReaderTapAction,
    val tapZoneBottomCenter: ReaderTapAction,
    val tapZoneBottomRight: ReaderTapAction,
    val longTapZoneTopLeft: ReaderTapAction,
    val longTapZoneTopCenter: ReaderTapAction,
    val longTapZoneTopRight: ReaderTapAction,
    val longTapZoneMiddleLeft: ReaderTapAction,
    val longTapZoneCenter: ReaderTapAction,
    val longTapZoneMiddleRight: ReaderTapAction,
    val longTapZoneBottomLeft: ReaderTapAction,
    val longTapZoneBottomCenter: ReaderTapAction,
    val longTapZoneBottomRight: ReaderTapAction,
    // Double tap zones
    val doubleTapZoneTopLeft: ReaderTapAction,
    val doubleTapZoneTopCenter: ReaderTapAction,
    val doubleTapZoneTopRight: ReaderTapAction,
    val doubleTapZoneMiddleLeft: ReaderTapAction,
    val doubleTapZoneCenter: ReaderTapAction,
    val doubleTapZoneMiddleRight: ReaderTapAction,
    val doubleTapZoneBottomLeft: ReaderTapAction,
    val doubleTapZoneBottomCenter: ReaderTapAction,
    val doubleTapZoneBottomRight: ReaderTapAction,
    // Navigation panel (5 areas Ã— 2: single + long = 10)
    val navPanelTapForward: ReaderTapAction,
    val navPanelTapBack: ReaderTapAction,
    val navPanelTapNextBook: ReaderTapAction,
    val navPanelTapPrevBook: ReaderTapAction,
    val navPanelLongThumbnail: ReaderTapAction,
    val navPanelLongForward: ReaderTapAction,
    val navPanelLongBack: ReaderTapAction,
    val navPanelLongNextBook: ReaderTapAction,
    val navPanelLongPrevBook: ReaderTapAction,
    // Hardware Key Bindings
    val hardwareKeyBindings: HardwareKeyBindings = HardwareKeyBindings(),
    // Hardware button enabled/disabled state
    val volumeButtonsEnabled: Boolean,
    val keyboardEnabled: Boolean,
    val gamepadButtonsEnabled: Boolean,
    val bluetoothButtonsEnabled: Boolean,
    // Helper settings (3 boolean)
    val horizontalSwipeEnabled: Boolean,
    val rtlNavigationEnabled: Boolean,
    val showTapAreas: Boolean,
    // Wallpaper
    val animationSpeed: Float,
    val parallaxEffectEnabled: Boolean,
    val wallpaperColorTheme: ColorTheme,
    val lowPowerMode: Boolean,
    val appTheme: AppTheme,
    val dynamicColorEnabled: Boolean = false,
    val recentSearches: Set<String> = emptySet(),
    // WiFi Sync
    val wifiSyncEnabled: Boolean = false,
    val wifiSyncHomeSSID: String = "",
    val wifiSyncFolderUri: String? = null,
    val wifiSyncFolderName: String = "",
    // WiFi Sync Identity
    val wifiSyncDeviceName: String = "",
    val wifiSyncDeviceId: String = "",
    // WiFi Sync Pairing State
    val wifiSyncIsPaired: Boolean = false,
    val wifiSyncLastPairedDesktop: String = "",
    val wifiSyncLastSyncTimestamp: Long = 0L,
    val wifiSyncBackgroundMode: BackgroundSyncMode = BackgroundSyncMode.CHARGING,
    // Image Adjustments
    val readerSaturation: Float = 1.0f,
    val readerContrast: Float = 1.0f,
    val readerSharpness: Float = 1.0f,
    // Behavior
    val readerOpenWithMenu: Boolean = true,
    val readerShowTapToasts: Boolean = true,
    // UI state
    val isLoading: Boolean = false,
    val isScanning: Boolean = false,
    val error: String? = null,
)
