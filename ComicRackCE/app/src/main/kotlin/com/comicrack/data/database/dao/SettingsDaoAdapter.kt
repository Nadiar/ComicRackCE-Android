package com.comicrack.data.database.dao

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

import com.comicrack.data.database.entities.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking

/**
 * Synchronous wrapper adapter for SettingsDao.
 *
 * Provides blocking wrappers for suspend functions to support Java callers
 * that cannot use coroutines directly.
 *
 * WARNING: Using runBlocking() on the main thread will cause ANR.
 * Only use this adapter in non-UI contexts (initialization, background threads).
 *
 * For UI code, use the suspend functions directly or expose Flow-based methods.
 */
class SettingsDaoAdapter(
    private val dao: SettingsDao,
) {
    /**
     * Blocking version of insert().
     * @param settings The Settings object to insert
     */
    fun insertSync(settings: Settings) =
        runBlocking {
            dao.insert(settings)
        }

    /**
     * Blocking version of update().
     * @param settings The Settings object with updated values
     */
    fun updateSync(settings: Settings) =
        runBlocking {
            dao.update(settings)
        }

    /**
     * Blocking version of getSettings().
     * @return Settings object, or null if not initialized
     */
    fun getSettingsSync(): Settings? =
        runBlocking {
            dao.getSettings()
        }

    /**
     * Blocking version of getSettingsCount().
     * @return Count of settings records (should always be 0 or 1)
     */
    fun getSettingsCountSync(): Int =
        runBlocking {
            dao.getSettingsCount()
        }

    /**
     * Blocking version of getTheme().
     * @return Current theme setting (may be null)
     */
    fun getThemeSync(): String? =
        runBlocking {
            dao.getTheme()
        }

    /**
     * Blocking version of getLibraryViewMode().
     * @return Current library view mode (may be null)
     */
    fun getLibraryViewModeSync(): String? =
        runBlocking {
            dao.getLibraryViewMode()
        }

    /**
     * Blocking version of getLibraryZoomLevel().
     * @return Current library zoom level
     */
    fun getLibraryZoomLevelSync(): Float =
        runBlocking {
            dao.getLibraryZoomLevel()
        }

    /**
     * Blocking version of isHapticFeedbackEnabled().
     * @return True if haptic feedback is enabled
     */
    fun isHapticFeedbackEnabledSync(): Boolean =
        runBlocking {
            dao.isHapticFeedbackEnabled()
        }

    /**
     * Blocking version of getReaderBrightness().
     * @return Current reader brightness level
     */
    fun getReaderBrightnessSync(): Int =
        runBlocking {
            dao.getReaderBrightness()
        }

    /**
     * Blocking version of deleteAll().
     */
    fun deleteAllSync() =
        runBlocking {
            dao.deleteAll()
        }

    /**
     * Non-blocking reactive observer.
     * @return Flow that emits Settings whenever it changes (may be null initially)
     */
    fun observeSettings(): Flow<Settings?> = dao.observeSettings()
}
