package com.comicrack.reader.di

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.comicrack.data.preferences.AppPreferencesRepository
import com.comicrack.reader.cache.TempFileManager
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for ReaderDependencies grouping class.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ReaderDependenciesTest {
    private lateinit var context: Context
    private lateinit var preferencesRepository: AppPreferencesRepository
    private lateinit var tempFileManager: TempFileManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        preferencesRepository = AppPreferencesRepository(context)
        tempFileManager = TempFileManager(context)
    }

    @Test
    fun `ReaderDependencies contains preferencesRepository`() {
        val dependencies = ReaderDependencies(preferencesRepository, tempFileManager)

        assertNotNull(dependencies.preferencesRepository)
    }

    @Test
    fun `ReaderDependencies contains tempFileManager`() {
        val dependencies = ReaderDependencies(preferencesRepository, tempFileManager)

        assertNotNull(dependencies.tempFileManager)
    }

    @Test
    fun `ReaderDependencies provides same instances passed to constructor`() {
        val dependencies = ReaderDependencies(preferencesRepository, tempFileManager)

        assertNotNull(dependencies.preferencesRepository)
        assertNotNull(dependencies.tempFileManager)
    }
}
