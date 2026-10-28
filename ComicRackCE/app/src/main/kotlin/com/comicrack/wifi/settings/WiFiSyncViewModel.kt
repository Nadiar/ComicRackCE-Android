@file:Suppress("unused")

package com.comicrack.wifi.settings

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

/**
 * @deprecated Use [com.comicrack.wifi.ui.WiFiSyncViewModel] instead.
 *
 * This type alias exists for backwards compatibility during migration.
 * Will be removed in a future release.
 */
@Deprecated(
    message = "Moved to ui package",
    replaceWith =
        ReplaceWith(
            "WiFiSyncViewModel",
            "com.comicrack.viewer.wifi.ui.WiFiSyncViewModel",
        ),
)
typealias WiFiSyncViewModel = com.comicrack.wifi.ui.WiFiSyncViewModel
