package com.comicrack.core.di

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.comicrack.core.scanner.LibraryService
import com.comicrack.core.sync.RemoteSyncService
import com.comicrack.core.work.LibraryScanWorker
import com.comicrack.core.work.RemoteSyncWorker
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

/**
 * Hilt-aware WorkerFactory for WorkManager dependency injection.
 *
 * WorkManager requires a custom WorkerFactory to inject dependencies into Worker classes.
 * This factory integrates Hilt with WorkManager by:
 * 1. Getting the Hilt component from the application context
 * 2. Extracting the WorkerDependencies entry point
 * 3. Using those dependencies to construct workers
 *
 * Phase 4, Task 4.7: Dependency Injection
 *
 * DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)
 */
class HiltWorkerFactory
    @Inject
    constructor() : WorkerFactory() {
        /**
         * Entry point to access Hilt-managed dependencies for workers.
         */
        @EntryPoint
        @InstallIn(SingletonComponent::class)
        interface WorkerDependencies {
            fun libraryService(): LibraryService
            fun remoteSyncService(): RemoteSyncService
        }

        override fun createWorker(
            appContext: Context,
            workerClassName: String,
            workerParameters: WorkerParameters,
        ): ListenableWorker? {
            // Get Hilt component
            val hiltEntryPoint =
                EntryPointAccessors.fromApplication(
                    appContext,
                    WorkerDependencies::class.java,
                )

            // Route to appropriate worker constructor
            return when (workerClassName) {
                LibraryScanWorker::class.java.name -> {
                    LibraryScanWorker(
                        context = appContext,
                        params = workerParameters,
                        libraryService = hiltEntryPoint.libraryService(),
                    )
                }
                RemoteSyncWorker::class.java.name -> {
                    RemoteSyncWorker(
                        context = appContext,
                        params = workerParameters,
                        syncService = hiltEntryPoint.remoteSyncService(),
                    )
                }
                else -> null
            }
        }
    }
