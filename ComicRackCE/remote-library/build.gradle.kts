import java.time.Duration // Import for timeouts if needed

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.comicrack.remote"
    compileSdk = 35

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
            // Enable experimental features if you need them for 2.3.0
            freeCompilerArgs.add("-Xallow-unstable-dependencies")
        }
    }

    buildFeatures {
        compose = true
    }

    // REMOVED: composeOptions { kotlinCompilerExtensionVersion = ... }
    // In Kotlin 2.0+, the compiler version is tied to the Kotlin version.

    packaging {
        resources {
            // Fixes the Log4j and duplicate file conflicts we saw yesterday
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/LICENSE*"
            excludes += "/META-INF/NOTICE*"
        }
    }
}

// ... (plugins section stays the same)

dependencies {
    // 1. KOTLIN 2.3.0 FIX (Matches App)
    ksp(libs.kotlin.metadata)

    // 2. GOOGLE DRIVE API (Matches your TOML names)
    implementation(libs.google.play.services.auth)
    implementation(libs.google.api.services.drive)
    implementation(libs.google.api.client)
    implementation(libs.google.auth.library)

    // 3. PERSISTENCE & WORK (Matches App)
    // 3. PERSISTENCE & WORK (Matches App)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime.ktx)

    // 4. HILT (Matched to App naming: 'libs.hilt...')
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // 5. UI & LIFECYCLE (Matches App)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.lifecycle.runtime.compose) // Newly defined in TOML above
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // 6. IMAGE & COROUTINES (Matches App)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.coil.compose)

    // 7. TESTING (Matches App naming: 'libs.androidx...')
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}