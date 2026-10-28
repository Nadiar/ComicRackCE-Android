package com.comicrack.core.di

import com.comicrack.data.database.ComicRackDatabase
import com.comicrack.data.database.dao.GoogleDriveAuthDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing authentication-related dependencies.
 *
 * Phase 3: Google Drive Authentication (SRC-05-007 v2)
 */
@Module
@InstallIn(SingletonComponent::class)
object AuthModule {

    @Singleton
    @Provides
    fun provideGoogleDriveAuthDao(database: ComicRackDatabase): GoogleDriveAuthDao =
        database.googleDriveAuthDao()
}
