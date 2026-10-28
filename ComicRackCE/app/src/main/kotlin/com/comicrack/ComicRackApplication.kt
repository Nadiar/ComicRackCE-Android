package com.comicrack

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

import android.app.Application
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.request.crossfade
import com.comicrack.core.di.HiltWorkerFactory
import com.comicrack.data.database.ComicRackDatabase
import com.comicrack.data.preferences.AppPreferencesRepository
import com.comicrack.reader.BuildConfig
import com.comicrack.reader.image.ComicCoverFetcher
import com.comicrack.reader.image.ComicPageFetcher
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ComicRack application entry point with Hilt dependency injection.
 *
 * Annotated with @HiltAndroidApp to enable Hilt initialization.
 * Hilt generates the application component and injects dependencies into any
 * @HiltAndroidApp-annotated Activities, Services, Fragments, etc.
 *
 * Configures WorkManager with HiltWorkerFactory to enable dependency injection
 * in Worker classes.
 *
 * Phase 4, Task 4.7: Dependency Injection
 *
 * DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)
 */
@HiltAndroidApp
class ComicRackApplication :
    Application(),
    Configuration.Provider,
    SingletonImageLoader.Factory {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    // Legacy fields for compatibility
    /**
     * Legacy field retained for Java compatibility. Prefer `instance` or DI where possible.
     * @deprecated Kept for compatibility; avoid new usages and plan to remove once references are migrated.
     */
    @Deprecated("Retained for legacy Java compatibility", level = DeprecationLevel.WARNING)
    @JvmField
    var g: String? = null

    /**
     * Legacy field retained for Java compatibility. Prefer `instance` or DI where possible.
     * @deprecated Kept for compatibility; avoid new usages and plan to remove once references are migrated.
     */
    @Deprecated("Retained for legacy Java compatibility", level = DeprecationLevel.WARNING)
    @JvmField
    var h: String? = null

    // Application-scoped coroutine scope for background tasks
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        instance = this
        i = this

        // Plant Timber for debug logging
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        try {
            // AppPreferencesRepository is now automatically injected via Hilt
            // No manual initialization needed

            // Initialize Room database and force schema creation
            // Room is lazy - schema is only created when a DAO is first accessed
            applicationScope.launch {
                try {
                    val db = ComicRackDatabase.getInstance(this@ComicRackApplication)
                    // Trigger schema creation by accessing the database
                    val count = db.comicBookDao().getTotalComicCount()
                    Timber.tag("ComicRackApp").d("Database initialized, comic count: $count")
                } catch (e: Exception) {
                    Timber.tag("ComicRackApp").e(e, "Database initialization failed")
                }
            }

            Timber.tag("ComicRackApp").d("Core initialization complete")
        } catch (e: Exception) {
            Timber.tag("ComicRackApp").w(e, "Core init failed")
        }
    }

    override val workManagerConfiguration: Configuration
        get() =
            Configuration
                .Builder()
                .setWorkerFactory(workerFactory)
                .setMinimumLoggingLevel(android.util.Log.INFO)
                .build()

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        Timber.tag("ComicRackApp").d("newImageLoader() called - creating ImageLoader with custom fetchers")
        Timber.d("Creating ImageLoader with custom fetchers")
        return ImageLoader
            .Builder(context)
            .components {
                // Cover fetcher for library grid (extracts first page as cover)
                // Uses archive:// scheme to bypass Coil's FileMapper
                add(ComicCoverFetcher.Factory(this@ComicRackApplication))
                // Page fetcher for reader (streams raw bytes for efficient decoding)
                add(ComicPageFetcher.Factory(this@ComicRackApplication))
            }.memoryCache {
                MemoryCache
                    .Builder()
                    .maxSizePercent(context, 0.25) // 25% of available memory
                    .build()
            }.diskCache {
                DiskCache
                    .Builder()
                    .directory(cacheDir.resolve("coil_cache"))
                    .maxSizeBytes(256L * 1024 * 1024) // 256 MB disk cache
                    .build()
            }.crossfade(true)
            .build()
    }

    /**
     * Legacy method retained for compatibility with Java classes.
     *
     * @deprecated Use explicit feature flags or dedicated health checks instead. This stub
     * always returns `true` and exists for Java compatibility only.
     */
    @Deprecated("Legacy compatibility method; avoid usage", level = DeprecationLevel.WARNING)
    fun d(): Boolean = true

    companion object {
        lateinit var instance: ComicRackApplication
            private set

        /**
         * Legacy static instance retained for compatibility with Java classes.
         */
        @JvmField var i: ComicRackApplication? = null
    }
}
