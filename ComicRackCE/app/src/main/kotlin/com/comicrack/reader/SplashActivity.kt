package com.comicrack.reader

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.comicrack.data.preferences.AppPreferencesRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * SplashActivity - Modernized with SplashScreen API (Android 12+) and Compose.
 * Handles startup initialization, library path migration, and navigation.
 */
@AndroidEntryPoint
class SplashActivity : AppCompatActivity() {
    @Inject
    lateinit var appPreferencesRepository: AppPreferencesRepository

    /**
     * Coroutine job that performs asynchronous initialization work.
     * This job is cancelled in [onDestroy] or when startup is aborted.
     */
    private var initializationJob: Job? = null

    /**
     * True while a library path migration flow is active and user interaction is required.
     */
    private var migrationActive = false

    /**
     * Set to true when the library is ready and the splash screen may be dismissed.
     */
    private var libraryReady = false

    /**
     * Activity entry point. Installs the platform splash screen and coordinates startup flows.
     * Keeps the splash screen visible while initialization or migration is in progress and
     * triggers library migration handling and asynchronous application initialization.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        splashScreen.setKeepOnScreenCondition {
            !libraryReady && !migrationActive
        }

        setResult(RESULT_OK)

        handleLibraryPathMigration()
        initializeApplication()
    }

    /**
     * Handle library path migration if needed.
     * Uses AppPreferencesRepository to check if library path is configured.
     * If not configured, triggers migration flow.
     */
    private fun handleLibraryPathMigration() {
        try {
            lifecycleScope.launch(Dispatchers.Main) {
                appPreferencesRepository.libraryPath.collect { currentPath ->
                    val isMigrationNeeded = currentPath.isNullOrEmpty()

                    if (isMigrationNeeded) {
                        Timber.tag("SplashActivity").i("Library path not configured - triggering migration")
                        migrationActive = true
                    }
                }
            }
        } catch (e: Exception) {
            Timber.tag("SplashActivity").w(e, "Error checking migration status")
        }
    }

    /**
     * Initialize the application using lifecycle-aware coroutines.
     * Runs manager and preferences initialization on a background thread.
     */
    private fun initializeApplication() {
        if (migrationActive) {
            Timber.tag("SplashActivity").i("Migration active â€” deferring startup to allow user to pick a folder")
            return
        }

        initializationJob =
            lifecycleScope.launch(Dispatchers.Default) {
                try {
                    // Cleanup duplicate library paths
                    try {
                        appPreferencesRepository.sanitizeLibraryPaths()
                    } catch (e: Exception) {
                        Timber.tag("SplashActivity").w(e, "Failed to sanitize paths")
                    }

                    // Mark library as ready
                    libraryReady = true

                    // Switch to main thread to navigate
                    withContext(Dispatchers.Main) {
                        onInitializationComplete()
                    }
                } catch (e: Exception) {
                    Timber.tag("SplashActivity").w(e, "Manager init failed")
                    libraryReady = true // Proceed even if init fails

                    withContext(Dispatchers.Main) {
                        onInitializationComplete()
                    }
                }
            }
    }

    /**
     * Called when initialization is complete. Transitions to the main library activity.
     */
    private fun onInitializationComplete() {
        if (libraryReady) {
            try {
                Timber.tag("SplashActivity").i("Initialization complete, launching library")
                val intent = Intent(this@SplashActivity, ComposeLibraryActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                startActivity(intent)
            } catch (t: Throwable) {
                Timber.tag("SplashActivity").w(t, "Failed to start ComposeLibraryActivity")
            } finally {
                finish()
            }
        }
    }

    /**
     * Lifecycle hook called when the activity is destroyed. Cancels any pending
     * initialization job to avoid leaks and unnecessary work.
     */
    override fun onDestroy() {
        super.onDestroy()
        initializationJob?.cancel()
    }

    /**
     * Handles back navigation during startup. Cancels initialization and
     * signals cancellation result so callers know startup did not complete.
     */
    override fun onBackPressed() {
        setResult(RESULT_CANCELED)
        initializationJob?.cancel()
        super.onBackPressed()
    }
}
