/**
 * Debug utilities for development builds.
 *
 * This file contains a broadcast receiver that allows triggering the WiFi sync flow via ADB.
 *
 * Usage:
 * adb shell am broadcast -a com.comicrack.viewer.DEBUG_START_SYNC \
 *     -n com.comicrack.viewer/.DebugStartReceiver --es sync_folder "content://..."
 *
 * Note: Intended for debug use only and should not be relied upon in production builds.
 */
package com.comicrack.reader

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.comicrack.wifi.service.WiFiSyncService
import timber.log.Timber

/**
 * A development-only receiver that triggers a WiFi sync when a debug broadcast is received.
 *
 * When it receives an Intent with action `com.comicrack.viewer.DEBUG_START_SYNC` it starts
 * [WiFiSyncService] with action [WiFiSyncService.ACTION_START_SYNC]. An optional
 * `sync_folder` extra may be supplied (a stringified content:// URI) and is passed to the service
 * as the Intent data.
 *
 * This receiver is intended for use during development and debugging via ADB shell commands.
 *
 * @see WiFiSyncService
 */
class DebugStartReceiver : BroadcastReceiver() {
    /**
     * Handles the incoming debug broadcast and starts the sync service if appropriate.
     *
     * Recognizes action `com.comicrack.viewer.DEBUG_START_SYNC` and starts
     * [WiFiSyncService] with action [WiFiSyncService.ACTION_START_SYNC]. If a `sync_folder`
     * extra is provided it will be parsed as a URI and forwarded as the service Intent data.
     *
     * @param context the Context used to start the service
     * @param intent the received Intent; may contain the `sync_folder` extra
     */
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        Timber.tag("DebugStartReceiver").i("Received debug broadcast: ${intent.action}")

        if (intent.action == "com.comicrack.viewer.DEBUG_START_SYNC") {
            val syncFolder = intent.getStringExtra("sync_folder")

            val serviceIntent =
                Intent(context, WiFiSyncService::class.java).apply {
                    action = WiFiSyncService.ACTION_START_SYNC
                    if (!syncFolder.isNullOrBlank()) {
                        data = Uri.parse(syncFolder)
                        Timber.i("Using custom sync URI: $syncFolder")
                    } else {
                        Timber.w("No sync_folder provided, service will likely default or fail.")
                    }
                }

            try {
                // Must use startForegroundService for background starts on modern Android
                context.startForegroundService(serviceIntent)
                Timber.i("Starting WiFiSyncService...")
            } catch (e: Exception) {
                Timber.e(e, "Failed to start WiFiSyncService from Debug Receiver")
            }
        }
    }
}
