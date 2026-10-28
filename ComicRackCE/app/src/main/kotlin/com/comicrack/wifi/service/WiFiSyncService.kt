package com.comicrack.wifi.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.comicrack.data.preferences.AppPreferencesRepository
import com.comicrack.reader.R
import com.comicrack.wifi.SyncIdentity
import com.comicrack.wifi.broadcast.BroadcastManager
import com.comicrack.wifi.file.FileManager
import com.comicrack.wifi.file.SyncFileManager
import com.comicrack.wifi.model.DiscoveredServer
import com.comicrack.wifi.progress.SyncProgressTracker
import com.comicrack.wifi.server.SyncServer
import com.comicrack.wifi.server.SyncServerState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * Foreground service that handles WiFi sync operations.
 *
 * This service operates in STANDBY MODE:
 * 1. Broadcasts UDP presence beacons so desktop can discover us
 * 2. Listens on TCP port 7614 for sync commands from desktop
 * 3. Shows notification with current status
 *
 * IMPORTANT: Android is a PASSIVE server. The desktop initiates all syncs.
 * This service keeps running until explicitly stopped or conditions change.
 */
@AndroidEntryPoint
class WiFiSyncService : Service() {
    companion object {
        private const val TAG = "WiFiSyncService"

        const val ACTION_START_SYNC = "com.comicrack.viewer.wifi.START_SYNC"
        const val ACTION_STOP_SYNC = "com.comicrack.viewer.wifi.STOP_SYNC"
        const val ACTION_SEND_SYNC_REQUEST = "com.comicrack.viewer.wifi.SEND_SYNC_REQUEST"

        private const val NOTIFICATION_CHANNEL_ID = "wifi_sync_channel"
        private const val NOTIFICATION_ID = 7614

        // No timeout - service runs until explicitly stopped
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var syncJob: Job? = null

    private var syncServer: SyncServer? = null
    private var broadcastManager: BroadcastManager? = null
    private val previousStateWasSyncing = MutableStateFlow(false)

    @Inject
    lateinit var progressTracker: SyncProgressTracker

    @Inject
    lateinit var repository: AppPreferencesRepository

    override fun onCreate() {
        super.onCreate()
        Timber.tag(TAG).d("Service created")
        createNotificationChannel()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        Timber.tag(TAG).d("onStartCommand: action=${intent?.action}")

        when (intent?.action) {
            ACTION_START_SYNC -> {
                // Determine sync folder: use intent data or fallback to library default
                startSync(intent.data)
            }
            ACTION_STOP_SYNC -> {
                stopSync()
            }
            ACTION_SEND_SYNC_REQUEST -> {
                sendSyncRequest()
            }
            else -> {
                Timber.tag(TAG).w("Unknown action: ${intent?.action}")
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Timber.tag(TAG).d("Service destroyed")
        stopSync()
        serviceScope.cancel()
    }

    private fun startSync(syncFolderUri: Uri?) {
        if (syncJob?.isActive == true) {
            Timber.tag(TAG).w("Standby session already running")
            return
        }

        // Immediately start foreground to satisfy Android requirements
        startForeground(NOTIFICATION_ID, createNotification("WiFi Sync standby active"))

        syncJob =
            serviceScope.launch {
                try {
                    // Verify conditions before proceeding
                    val isEnabled = repository.wifiSyncEnabled.first()
                    if (!isEnabled) {
                        Timber.tag(TAG).i("WiFi sync is disabled in settings. Stopping service.")
                        stopSync()
                        return@launch
                    }

                    val homeSSID = repository.wifiSyncHomeSSID.first()
                    if (homeSSID.isEmpty()) {
                        Timber.tag(TAG).i("No home SSID configured. Stopping service.")
                        stopSync()
                        return@launch
                    }

                    runStandbySession(syncFolderUri)
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "Standby session failed")
                    updateNotification("Error: ${e.message}")
                    // In case of error, wait and stop to avoid notification hang
                    kotlinx.coroutines.delay(5000)
                    stopSync()
                }
            }
    }

    private suspend fun runStandbySession(_explicitUri: Uri?) =
        kotlinx.coroutines.coroutineScope {
            Timber.tag(TAG).i("════════════════════════════════════════════════════════════")
            Timber.tag(TAG).i("▶ STARTUP: Beginning WiFi Sync standby session")
            Timber.tag(TAG).i("════════════════════════════════════════════════════════════")

            // Resolve library path from preferences
            val libraryPathStr = repository.libraryPath.first()
            if (libraryPathStr.isNullOrBlank()) {
                Timber.tag(TAG).e("No Library Path set in preferences")
                error("No Library folder set. Please configure Library in Settings.")
            }

            val libraryUri = Uri.parse(libraryPathStr)
            Timber.tag(TAG).i("Library URI: $libraryUri")

            // Create sync file manager - all SAF operations are delegated here
            val fileManager =
                SyncFileManager.fromTreeUri(this@WiFiSyncService, libraryUri)
                    ?: error("Failed to initialize SyncFileManager. Check Library permissions.")
            Timber.tag(TAG).i("SyncFileManager initialized")

            // Get device details for virtual INI
            // Device ID is SHA1(model + manufacturer + serial) - matches legacy APK format
            val serialNumber =
                android.provider.Settings.Secure
                    .getString(contentResolver, android.provider.Settings.Secure.ANDROID_ID)
                    ?: "unknown"
            val deviceKey = SyncIdentity.generateDeviceId(serialNumber = serialNumber)

            // Get device name: prioritize system settings, then fallback to bluetooth, then model, then custom
            // Respects user preference for device name source
            val deviceNameSource = repository.wifiSyncDeviceNameSource.first()
            val deviceName =
                getDeviceNameBySource(deviceNameSource)
                    ?: repository.wifiSyncDeviceName.first().ifBlank { android.os.Build.MODEL }
            val metrics = resources.displayMetrics
            val screenInfo = "${metrics.widthPixels},${metrics.heightPixels},${metrics.xdpi},${metrics.ydpi}"

            Timber.tag("WiFiSync").i("▶ IDENTITY: deviceKey=$deviceKey (SHA1 of model+manufacturer+serial)")
            Timber.tag("WiFiSync").i("▶ IDENTITY: deviceName=$deviceName")
            Timber.tag("WiFiSync").i("▶ IDENTITY: serialNumber=$serialNumber")
            Timber.tag("WiFiSync").i("▶ IDENTITY: screenInfo=$screenInfo")

            // Ensure comicrack.ini exists physically and matches current identity
            Timber.tag("WiFiSync").i("▶ INI: Ensuring comicrack.ini exists...")
            ensureIniPhysical(fileManager, deviceName, serialNumber, screenInfo)

            // Perform initial cleanup of stale files (orphans, collisions)
            Timber.tag("WiFiSync").i("▶ CLEANUP: Performing initial cleanup of stale sync files...")
            (fileManager as? SyncFileManager)?.cleanupStaleSyncFiles()

            // Create sync server
            Timber.tag("WiFiSync").i("▶ SERVER: Creating SyncServer instance...")
            syncServer =
                SyncServer(
                    fileManager = fileManager,
                    progressTracker = progressTracker,
                    repository = repository,
                    deviceKey = deviceKey,
                    deviceName = deviceName,
                ).also { server ->
                    // Monitor server state
                    launch {
                        server.state.collect { state ->
                            when (state) {
                                is SyncServerState.Idle -> {
                                    updateNotification("Idle")
                                }
                                is SyncServerState.Listening -> {
                                    updateNotification("Waiting for desktop...")
                                }
                                is SyncServerState.Broadcasting -> {
                                    updateNotification("Broadcasting... (${state.beaconCount} beacons)")
                                }
                                is SyncServerState.Syncing -> {
                                    updateNotification(state.statusText)
                                    // Record pairing on first sync connection
                                    if (state.progress == 0) {
                                        serviceScope.launch {
                                            repository.recordSuccessfulPairing(state.clientAddress)
                                        }
                                    }
                                }
                                is SyncServerState.Error -> {
                                    updateNotification("Error: ${state.message}")
                                }
                            }
                        }
                    }
                }

            // Start TCP Server - runs indefinitely until stopped
            Timber.tag("WiFiSync").i("▶ SERVER: Launching TCP server job on port 7614...")
            val serverJob =
                launch {
                    syncServer?.start()
                }

            // Start broadcast beacon job - runs in parallel
            Timber.tag("WiFiSync").i("▶ BEACON: Starting broadcast manager...")
            broadcastManager = BroadcastManager()
            updateNotification("Ready - Waiting for desktop...")

            val beaconJob =
                launch {
                    Timber.tag("WiFiSync").i("▶ BEACON: Starting discovery broadcast loop...")
                    broadcastManager
                        ?.discoverServers(deviceKey)
                        ?.catch {
                            Timber.tag("WiFiSync").e(it, "▶ BEACON ERROR")
                            Timber.tag(TAG).e(it, "Beacon error")
                        }?.collect { server ->
                            Timber.tag("WiFiSync").i("▶ BEACON: Desktop responded - ${server.endpoint}")
                            Timber.tag(TAG).i("Desktop responded: ${server.endpoint}")
                            // Desktop acknowledged our beacon - we're discoverable
                        }
                }

            Timber.tag("WiFiSync").i("════════════════════════════════════════════════════════════")
            Timber.tag("WiFiSync").i("▶ READY: WiFi Sync standby mode active!")
            Timber.tag("WiFiSync").i("▶ READY: TCP port 7614, UDP beacon on 224.34.123.90:7615")
            Timber.tag("WiFiSync").i("════════════════════════════════════════════════════════════")

            // Monitor for sync completion to record timestamp
            val completionJob =
                launch {
                    syncServer
                        ?.state
                        ?.collect { state ->
                            val isSyncing = state is SyncServerState.Syncing
                            val wasSyncingBefore = previousStateWasSyncing.value
                            previousStateWasSyncing.value = isSyncing

                            // Detect transition from syncing to idle/listening
                            if (wasSyncingBefore && !isSyncing && state is SyncServerState.Listening) {
                                Timber.tag(TAG).i("Sync completed, recording timestamp")
                                repository.recordSuccessfulSync()
                                updateNotification("Ready - Last sync just completed")

                                // Perform cleanup after sync session completes
                                Timber.tag("WiFiSync").i("▶ CLEANUP: Performing post-sync cleanup...")
                                (fileManager as? SyncFileManager)?.cleanupStaleSyncFiles()
                            }
                        }
                }

            // Keep running until cancelled
            Timber.tag(TAG).i("Standby session started. Service will run until stopped.")
            serverJob.join()

            // Cleanup
            beaconJob.cancel()
            completionJob.cancel()
        }

    private fun stopSync() {
        Timber.tag(TAG).d("Stopping sync")
        syncJob?.cancel()
        syncServer?.stop()
        syncServer = null
        broadcastManager = null

        // Ensure foreground state is cleared and service stops
        stopForeground(true)
        stopSelf()
    }

    /**
     * Send a sync request beacon to trigger Desktop-initiated sync.
     *
     * This sends the "ComicRackAndroid:<DeviceID>:Sync" multicast beacon
     * which Desktop's WirelessSyncProvider listens for to initiate a sync session.
     *
     * If the service isn't running (broadcastManager is null), this starts the
     * service first to ensure the beacon can be sent.
     */
    private fun sendSyncRequest() {
        serviceScope.launch(Dispatchers.IO) {
            try {
                // Get device key for the beacon
                val serialNumber =
                    android.provider.Settings.Secure.getString(
                        contentResolver,
                        android.provider.Settings.Secure.ANDROID_ID,
                    ) ?: "unknown"
                val deviceKey =
                    com.comicrack.wifi.SyncIdentity
                        .generateDeviceId(serialNumber = serialNumber)

                // If broadcastManager isn't initialized, the service isn't running
                // Start the service first
                if (broadcastManager == null) {
                    Timber.tag(TAG).i("Sync Request: Service not running, starting it first")
                    startSync(null)
                    // Wait briefly for service to initialize
                    kotlinx.coroutines.delay(1000)
                }

                // Send the sync request beacon
                broadcastManager?.sendSyncRequest(deviceKey)
                Timber.tag(TAG).i("Sync Request: Sent :Sync beacon with deviceKey=$deviceKey")

                // Update notification to show sync was requested
                updateNotification("Sync requested - waiting for desktop...")
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to send sync request")
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    "WiFi Sync",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Shows WiFi sync progress"
                    setShowBadge(false)
                }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(message: String): Notification {
        val stopIntent =
            Intent(this, WiFiSyncService::class.java).apply {
                action = ACTION_STOP_SYNC
            }
        val stopPendingIntent =
            PendingIntent.getService(
                this,
                0,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        return NotificationCompat
            .Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("WiFi Sync")
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop",
                stopPendingIntent,
            ).build()
    }

    /**
     * Ensures comicrack.ini exists physically in the sync folder and matches current identity.
     * Logs any field-level changes during patching.
     */
    private suspend fun ensureIniPhysical(
        fileManager: FileManager,
        deviceName: String,
        serialNumber: String,
        screenInfo: String,
    ) = withContext(Dispatchers.IO) {
        try {
            val virtualContent =
                SyncIdentity.generateIniContent(
                    deviceName = deviceName,
                    serialNumber = serialNumber,
                    screenInfo = screenInfo,
                )

            Timber.tag("WiFiSync").d("▶ INI: Generated virtual content (${virtualContent.length} bytes)")

            if (!fileManager.fileExists(SyncIdentity.INI_FILENAME)) {
                Timber.tag("WiFiSync").i("▶ INI: File missing, creating new ${SyncIdentity.INI_FILENAME}")
                Timber.i("INI Seed: Missing ${SyncIdentity.INI_FILENAME}, creating initial seed.")
                val success =
                    fileManager.writeFile(
                        SyncIdentity.INI_FILENAME,
                        virtualContent.length.toLong(),
                        virtualContent.byteInputStream(),
                    )
                Timber.tag("WiFiSync").i("▶ INI: Create result = $success")
                return@withContext
            }

            Timber.tag("WiFiSync").i("▶ INI: File exists, checking for updates...")
            // Existing file: Read and compare fields
            val physicalData = fileManager.readFile(SyncIdentity.INI_FILENAME) ?: return@withContext
            val physicalContent = physicalData.toString(Charsets.UTF_8)
            Timber.tag("WiFiSync").d("▶ INI: Physical content (${physicalContent.length} bytes)")

            // Basic INI parsing (key=value)
            val physicalFields =
                physicalContent
                    .lines()
                    .filter { it.contains("=") && !it.trim().startsWith("#") }
                    .associate {
                        val parts = it.split("=", limit = 2)
                        parts[0].trim().lowercase() to parts[1].trim()
                    }

            val virtualFields =
                virtualContent
                    .lines()
                    .filter { it.contains("=") && !it.trim().startsWith("#") }
                    .associate {
                        val parts = it.split("=", limit = 2)
                        parts[0].trim().lowercase() to parts[1].trim()
                    }

            // Check if critical fields vary
            val criticalFields = listOf("id", "hash", "name", "version", "edition")
            val changes = mutableListOf<String>()

            for (key in criticalFields) {
                val physicalVal = physicalFields[key]
                val virtualVal = virtualFields[key]
                if (physicalVal != virtualVal) {
                    changes.add("$key: '$physicalVal' -> '$virtualVal'")
                }
            }

            if (changes.isNotEmpty()) {
                Timber.tag("WiFiSync").i("▶ INI: Identity mismatch, patching file...")
                Timber.i("INI Patch: Identity discrepancy detected. Updating ${SyncIdentity.INI_FILENAME}:")
                changes.forEach {
                    Timber.tag("WiFiSync").i("▶ INI:   $it")
                    Timber.i("  - $it")
                }

                fileManager.writeFile(
                    SyncIdentity.INI_FILENAME,
                    virtualContent.length.toLong(),
                    virtualContent.byteInputStream(),
                )
            } else {
                Timber.tag("WiFiSync").i("▶ INI: Identity matches, no patch needed")
                Timber.d("INI Check: Physical identity matches current settings. No patch needed.")
            }
        } catch (e: Exception) {
            Timber.tag("WiFiSync").e(e, "▶ INI ERROR: Failed to ensure INI file")
            Timber.w(e, "INI Startup Sync failed")
        }
    }

    private fun updateNotification(message: String) {
        val notification = createNotification(message)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    /**
     * Retrieve device name according to user preference.
     * Priority is driven by wifiSyncDeviceNameSource preference.
     *
     * Sources:
     * - "system": Settings.System.device_name, Settings.Global.DEVICE_NAME (recommended for Pixel/OEM)
     * - "bluetooth": BluetoothAdapter.getName() (requires BLUETOOTH_CONNECT permission)
     * - "model": Build.MODEL (hardware model name)
     * - "custom": User-provided custom name from preferences
     *
     * Falls back through Settings chain if primary source fails.
     */
    private fun getDeviceNameBySource(preferredSource: String): String? =
        try {
            Timber.tag(TAG).d("getDeviceNameBySource: Using source='$preferredSource'")

            when (preferredSource) {
                "system" -> getSystemDeviceNameSettings()
                "bluetooth" -> getBluetoothDeviceName() ?: getSystemDeviceNameSettings()
                "model" -> android.os.Build.MODEL
                "custom" -> null // Caller will use repository preference
                else -> getSystemDeviceNameSettings() // Default to system
            }
        } catch (e: Exception) {
            Timber.tag(TAG).d("getDeviceNameBySource: Error with source '$preferredSource': ${e.message}")
            null
        }

    /**
     * Try to get device name from Android system settings.
     * Tries Settings.System first (Samsung/OEM), then Settings.Global (Pixel/AOSP).
     */
    private fun getSystemDeviceNameSettings(): String? {
        return try {
            // Try Samsung/OEM system setting (device_name)
            try {
                val samsungName = Settings.System.getString(contentResolver, "device_name")
                if (!samsungName.isNullOrBlank()) {
                    Timber.tag(TAG).d("Using Settings.System.device_name: $samsungName")
                    return samsungName
                }
            } catch (e: Exception) {
                Timber.tag(TAG).d("Settings.System.device_name unavailable: ${e.message}")
            }

            // Try Pixel/AOSP global setting (DEVICE_NAME)
            try {
                val pixelName = Settings.Global.getString(contentResolver, Settings.Global.DEVICE_NAME)
                if (!pixelName.isNullOrBlank()) {
                    Timber.tag(TAG).d("Using Settings.Global.DEVICE_NAME: $pixelName")
                    return pixelName
                }
            } catch (e: Exception) {
                Timber.tag(TAG).d("Settings.Global.DEVICE_NAME unavailable: ${e.message}")
            }

            Timber.tag(TAG).d("No system device name found in Settings")
            null
        } catch (e: Exception) {
            Timber.tag(TAG).d("Exception in getSystemDeviceNameSettings: ${e.message}")
            null
        }
    }

    /**
     * Try to get device name from Bluetooth adapter.
     * Requires BLUETOOTH_CONNECT permission (Android 12+).
     */
    private fun getBluetoothDeviceName(): String? {
        return try {
            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            if (bluetoothAdapter != null && bluetoothAdapter.isEnabled) {
                val btName = bluetoothAdapter.name
                if (!btName.isNullOrBlank()) {
                    Timber.tag(TAG).d("Using BluetoothAdapter.name: $btName")
                    return btName
                }
            }
            Timber.tag(TAG).d("BluetoothAdapter unavailable or disabled")
            null
        } catch (e: SecurityException) {
            Timber.tag(TAG).d(
                "SecurityException accessing BluetoothAdapter.name " +
                    "(needs BLUETOOTH_CONNECT permission): ${e.message}",
            )
            null
        } catch (e: Exception) {
            Timber.tag(TAG).d("Exception getting Bluetooth device name: ${e.message}")
            null
        }
    }
}
