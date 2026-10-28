package com.comicrack.wifi

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Centralized identity constants for WiFi sync protocol.
 *
 * These values MUST match what the ComicRack CE desktop expects exactly.
 * The desktop validates these during handshake (Info command) and
 * expects them to match values in comicrack.ini.
 *
 * @see com.comicrack.wifi.protocol.CommandHandler
 */
object SyncIdentity {
    /**
     * Sync protocol version code.
     * Must match the Version field in comicrack.ini.
     * Desktop compares this against Device.Version during ValidateWifi.
     */
    const val SYNC_VERSION = 104

    /**
     * Edition string for the INI file.
     * Desktop parses this to determine SyncAppEdition.AndroidFull.
     */
    const val SYNC_EDITION = "Android Full"

    /**
     * INI filename expected by desktop for device identification.
     */
    const val INI_FILENAME = "comicrack.ini"

    /**
     * Legacy Android device key.
     * This is the SHA1 certificate fingerprint the desktop validates against.
     * Desktop hardcodes validation in WirelessSyncProvider.ValidateWifi().
     *
     * Note: Both AndroidKey and AndroidDebugKey are accepted by desktop.
     * We use the production AndroidKey for compatibility.
     */
    @Suppress("MaxLineLength") // Required legacy key - cannot be shortened
    const val LEGACY_ANDROID_KEY = "3082019d30820106a00302010202044e7b1922300d06092a864886f70d010105050030133111300f060355040a130863596f20536f6674301e170d3131303932323131313635305a170d3336303931353131313635305a30133111300f060355040a130863596f20536f667430819f300d06092a864886f70d010101050003818d00308189028181008d81ffb74008a048c517275a464db26461df06a3c85675b6ffa8bea15ec9288eec1ef1bf7616d09b7265bf1c5666473342c2a96ca385769592d73a21595335e5173c69ae5bb7aebd29387e9635ce30bdf11afff71145570b6577799ecac6100bcf0b2c4df6fe34fb8a418b5511c6a56c97b15c544269e91478ee24633ef063090203010001300d06092a864886f70d010105050003818100802cf8770c7af0744f9680b54da88b56eb1d6a48e8d446ce746817fe959991dc1f882323c6015edd4d48f28cfe3a94e30b75c92855b01a8c48354aae8e13a0b949390133c07b09419ac73d0b0b3dc13b2838fe9eae4b171c8022cb47ead602771560277cde7ad61e1a9ce5dee880d0226ed8cc71f36fb376d271a3cb61f1128b"

    /**
     * Generates a unique device ID using SHA1(model + manufacturer + serial).
     * This matches the legacy APK format exactly.
     * Uses trimZeros format to match Desktop's ToHexString(trimZeros: true).
     */
    fun generateDeviceId(
        model: String = android.os.Build.MODEL,
        manufacturer: String = android.os.Build.MANUFACTURER,
        serialNumber: String = "unknown",
    ): String {
        val input = model + manufacturer + serialNumber
        return calculateSha1TrimZeros(input)
    }

    /**
     * Generates the content for comicrack.ini.
     * Use this for virtualizing the file in the WiFi sync protocol.
     *
     * Format matches legacy APK (Java Properties format):
     * - Comment line: #ComicRack Device Information
     * - Comment line: #<timestamp>
     * - ID = SHA1(model + manufacturer + serial)
     * - Hash = SHA1(model + manufacturer + serial + edition + version) with trimZeros
     */
    fun generateIniContent(
        deviceName: String,
        model: String = android.os.Build.MODEL,
        manufacturer: String = android.os.Build.MANUFACTURER,
        serialNumber: String = "unknown",
        screenInfo: String = "0,0,0,0",
    ): String {
        // ID is SHA1 of model+manufacturer+serial (matches legacy APK)
        val deviceId = generateDeviceId(model, manufacturer, serialNumber)

        // Hash includes edition and version, with trimZeros format
        val hashInput = model + manufacturer + serialNumber + SYNC_EDITION + SYNC_VERSION
        val hash = calculateSha1TrimZeros(hashInput)

        // Java Properties timestamp format: "EEE MMM dd HH:mm:ss zzz yyyy"
        val timestampFormat = SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy", Locale.US)
        val timestamp = timestampFormat.format(Date())

        // Format matches legacy APK - Java Properties format with # comments
        return """
            #ComicRack Device Information
            #$timestamp
            ID=$deviceId
            Name=$deviceName
            Model=$model
            Manufacturer=$manufacturer
            Serial=$serialNumber
            Edition=$SYNC_EDITION
            Version=$SYNC_VERSION
            Screen=$screenInfo
            Hash=$hash
            Capabilities=WEBP
            """.trimIndent()
    }

    /**
     * Calculate standard SHA1 hash (full 40-char hex, lowercase).
     */
    fun calculateSha1(input: String): String =
        try {
            val digest = MessageDigest.getInstance("SHA-1")
            val hash = digest.digest(input.toByteArray())
            hash.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            ""
        }

    /**
     * Calculate SHA1 hash with trimZeros format matching desktop's ToHexString(trimZeros: true).
     * Each byte is formatted as "{0:x}" not "{0:x2}", so leading zeros per-byte are trimmed.
     * E.g., byte 0x0A becomes "a" not "0a".
     */
    private fun calculateSha1TrimZeros(input: String): String =
        try {
            val digest = MessageDigest.getInstance("SHA-1")
            val hash = digest.digest(input.toByteArray())
            // Format each byte as hex without leading zero padding (matching desktop trimZeros)
            hash.joinToString("") { "%x".format(it) }
        } catch (e: Exception) {
            ""
        }
}
