package com.comicrack.core.storage.saf

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.annotation.RequiresApi

/**
 * Utility for handling "All Files Access" (MANAGE_EXTERNAL_STORAGE) permission.
 *
 * This is a fallback when the SAF picker doesn't work properly on some devices/Android versions.
 * With this permission, the app can use traditional File paths instead of SAF URIs.
 */
object AllFilesAccessHelper {
    /**
     * Check if the app has "All Files Access" permission.
     * Always returns true on Android 10 and below (not needed).
     */
    fun hasAllFilesAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true // Not needed on Android 10 and below
        }

    /**
     * Check if we should offer the All Files Access option.
     * Only relevant for Android 11+.
     */
    fun shouldOfferAllFilesAccess(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    /**
     * Create an intent to open the system settings for granting All Files Access.
     * The user must manually toggle the permission.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    fun createAllFilesAccessIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
            data = Uri.parse("package:${context.packageName}")
        }

    /**
     * Get the external storage root path.
     * Only useful when All Files Access is granted.
     */
    fun getExternalStorageRoot(): String = Environment.getExternalStorageDirectory().absolutePath

    /**
     * Convert a traditional file path to a content URI that works with our system.
     * Returns a "file://" URI when All Files Access is granted.
     */
    fun pathToUri(path: String): Uri = Uri.parse("file://$path")
}
