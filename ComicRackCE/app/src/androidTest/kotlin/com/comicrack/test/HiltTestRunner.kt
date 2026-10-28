package com.comicrack.test

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.testing.HiltTestApplication

/**
 * Custom Test Runner for Hilt-enabled instrumentation tests.
 *
 * This runner enables Hilt dependency injection in instrumentation tests by using
 * HiltTestApplication instead of the default Application class.
 *
 * Usage in build.gradle:
 * ```
 * defaultConfig {
 *     testInstrumentationRunner "com.comicrack.test.HiltTestRunner"
 * }
 * ```
 *
 * All instrumentation tests using this runner must be annotated with @HiltAndroidTest.
 */
class HiltTestRunner : AndroidJUnitRunner() {
    override fun newApplication(
        cl: ClassLoader?,
        className: String?,
        context: Context?,
    ): Application = super.newApplication(cl, HiltTestApplication::class.java.name, context)
}
