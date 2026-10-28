package com.comicrack.wifi.worker

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.comicrack.data.preferences.AppPreferencesRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext as HiltApplicationContext
import androidx.hilt.work.HiltWorker
import com.comicrack.wifi.network.NetworkMonitor
import com.comicrack.wifi.network.WiFiState
import com.comicrack.wifi.service.WiFiSyncService
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker that periodically checks if WiFi sync should be started.
 *
 * This worker runs every 15 minutes when the device is on WiFi.
 * If conditions are met (on home network, sync enabled, folder configured),
 * it starts the `WiFiSyncService` foreground service.
 *
 * Notes:
 * - The worker uses `NetworkType.UNMETERED` constraints; WorkManager will respect
 *   device power and Doze modes when scheduling runs.
 * - Starting a foreground service from a worker requires using
 *   `startForegroundService` and the service must promptly call `startForeground`
 *   with a notification to avoid system termination.
 */
@HiltWorker
class SyncCheckWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val appPreferencesRepository: AppPreferencesRepository,
) : CoroutineWorker(appContext, workerParams) {
    companion object {
        private const val TAG = "SyncCheckWorker"

        /**
         * Enqueue periodic sync check work.
         */
        fun enqueue(context: Context) {
            val constraints =
                Constraints
                    .Builder()
                    .setRequiredNetworkType(NetworkType.UNMETERED)
                    .build()

            val request =
                PeriodicWorkRequestBuilder<SyncCheckWorker>(
                    SyncWorkerConfig.REPEAT_INTERVAL_MINUTES,
                    TimeUnit.MINUTES,
                    SyncWorkerConfig.FLEX_INTERVAL_MINUTES,
                    TimeUnit.MINUTES,
                ).setConstraints(constraints)
                    .addTag(SyncWorkerConfig.WORKER_TAG)
                    .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                SyncWorkerConfig.UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )

            Timber.tag(TAG).i("Enqueued periodic sync check")
        }

        /**
         * Cancel periodic sync check work.
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(SyncWorkerConfig.UNIQUE_WORK_NAME)
            Timber.tag(TAG).i("Cancelled periodic sync check")
        }
    }

    override suspend fun doWork(): Result = try {
        Timber.tag(TAG).d("Sync check worker running")

        val conditionCheck = checkSyncConditions()
        when (conditionCheck) {
            is SyncConditionResult.Skip -> {
                Timber.tag(TAG).d(conditionCheck.reason)
                Result.success()
            }
            is SyncConditionResult.Ready -> {
                Timber.tag(TAG).i("Conditions met, starting sync service")
                startSyncService(conditionCheck.syncFolderUri)
                Result.success()
            }
        }
    } catch (e: InterruptedException) {
        Timber.tag(TAG).w(e, "Sync check interrupted")
        Result.retry()
    } catch (e: IllegalStateException) {
        Timber.tag(TAG).e(e, "Sync check failed: invalid state")
        Result.failure()
    } catch (e: IOException) {
        Timber.tag(TAG).e(e, "Sync check failed: I/O error")
        Result.retry()
    }

    private suspend fun checkSyncConditions(): SyncConditionResult = when {
        !appPreferencesRepository.wifiSyncEnabled.first() ->
            SyncConditionResult.Skip("WiFi sync disabled in preferences, skipping")
        else -> validatePrerequisites()
    }

    private suspend fun validatePrerequisites(): SyncConditionResult {
        val syncFolderUriStr = appPreferencesRepository.wifiSyncFolderUri.first()
            ?: return SyncConditionResult.Skip("No sync folder configured, skipping")
        val syncFolderUri = Uri.parse(syncFolderUriStr)

        val homeSSID = appPreferencesRepository.wifiSyncHomeSSID.first()
        if (homeSSID.isEmpty()) {
            return SyncConditionResult.Skip("No home SSID configured, skipping")
        }

        val networkMonitor = NetworkMonitor(applicationContext) { homeSSID }
        val wifiState = networkMonitor.getCurrentWiFiState()

        return validateNetworkState(wifiState, syncFolderUri)
    }

    private fun validateNetworkState(
        wifiState: WiFiState,
        syncFolderUri: Uri
    ): SyncConditionResult = when {
        wifiState !is WiFiState.Connected -> SyncConditionResult.Skip("Not on WiFi, skipping")
        !wifiState.isOnHomeNetwork -> SyncConditionResult.Skip("Not on home network (${wifiState.ssid}), skipping")
        else -> SyncConditionResult.Ready(syncFolderUri)
    }

    private sealed class SyncConditionResult {
        data class Skip(val reason: String) : SyncConditionResult()
        data class Ready(val syncFolderUri: Uri) : SyncConditionResult()
    }

    private fun startSyncService(syncFolderUri: Uri) {
        val intent =
            Intent(applicationContext, WiFiSyncService::class.java).apply {
                action = WiFiSyncService.ACTION_START_SYNC
                data = syncFolderUri
            }
        applicationContext.startForegroundService(intent)
    }
}
