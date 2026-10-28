@file:Suppress("unused")

package com.comicrack.wifi.settings

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

/**
 * @deprecated Use [com.comicrack.wifi.ui.WiFiSyncSettingsState] instead.
 *
 * This type alias exists for backwards compatibility during migration.
 * Will be removed in a future release.
 */
@Deprecated(
    message = "Moved to ui package",
    replaceWith =
        ReplaceWith(
            "WiFiSyncSettingsState",
            "com.comicrack.viewer.wifi.ui.WiFiSyncSettingsState",
        ),
)
typealias WiFiSyncSettingsState = com.comicrack.wifi.ui.WiFiSyncSettingsState

/**
 * @deprecated Use [com.comicrack.wifi.ui.WiFiSyncSettingsLogic] instead.
 */
@Deprecated(
    message = "Moved to ui package",
    replaceWith =
        ReplaceWith(
            "WiFiSyncSettingsLogic",
            "com.comicrack.viewer.wifi.ui.WiFiSyncSettingsLogic",
        ),
)
typealias WiFiSyncSettingsLogic = com.comicrack.wifi.ui.WiFiSyncSettingsLogic
