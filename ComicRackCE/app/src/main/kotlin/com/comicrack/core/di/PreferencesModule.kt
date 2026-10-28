package com.comicrack.core.di

// DOCFIX: doc-only pass â€” 2026-01-15 (docfix/pilot-comments)

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import com.comicrack.data.preferences.AppPreferencesRepository
import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * PreferencesModule â€” Hilt module for preference-related dependencies.
 *
 * Supplies:
 * - SharedPreferences via PreferenceManager (legacy support)
 * - AppPreferencesRepository (DataStore-based preferences)
 * - Moshi (JSON parsing for settings tags)
 *
 * Note: AppPreferencesRepository is a singleton object.
 * It requires initialization with application context on first access.
 */
@Module
@InstallIn(SingletonComponent::class)
object PreferencesModule {
    @Singleton
    @Provides
    fun provideSharedPreferences(
        @ApplicationContext context: Context,
    ): SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    // AppPreferencesRepository is now automatically provided by Hilt via @Inject constructor
    // No explicit provider function needed

    @Singleton
    @Provides
    fun provideMoshi(): Moshi = Moshi.Builder().build()
}
