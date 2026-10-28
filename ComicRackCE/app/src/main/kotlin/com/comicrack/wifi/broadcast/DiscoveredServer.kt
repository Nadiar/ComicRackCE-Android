@file:Suppress("unused")

package com.comicrack.wifi.broadcast

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

/**
 * @deprecated Use [com.comicrack.wifi.model.DiscoveredServer] instead.
 *
 * This type alias exists for backwards compatibility during migration.
 * Will be removed in a future release.
 */
@Deprecated(
    message = "Moved to model package",
    replaceWith =
        ReplaceWith(
            "DiscoveredServer",
            "com.comicrack.viewer.wifi.model.DiscoveredServer",
        ),
)
typealias DiscoveredServer = com.comicrack.wifi.model.DiscoveredServer
