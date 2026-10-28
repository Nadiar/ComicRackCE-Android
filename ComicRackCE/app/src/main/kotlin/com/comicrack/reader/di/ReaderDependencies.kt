package com.comicrack.reader.di

import com.comicrack.data.preferences.AppPreferencesRepository
import com.comicrack.reader.cache.TempFileManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Grouping class for reader-related dependencies.
 *
 * Simplifies dependency injection by providing a single point to inject
 * all reader dependencies rather than injecting them individually.
 *
 * Benefits:
 * - Reduces boilerplate in Activities/ViewModels
 * - Makes it easier to add new reader dependencies
 * - Provides a clear API surface for what the reader needs
 */
@Singleton
class ReaderDependencies @Inject constructor(
    val preferencesRepository: AppPreferencesRepository,
    val tempFileManager: TempFileManager,
)
