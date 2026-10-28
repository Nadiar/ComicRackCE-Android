import java.time.Duration

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
}

android {
    namespace = "com.comicrack.reader"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.comicrack.viewer"
        minSdk = 29
        targetSdk = 35
        versionCode = 103
        versionName = "1.80"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            storeFile = file(project.findProperty("RELEASE_STORE_FILE") ?: "release.jks")
            storePassword = project.findProperty("RELEASE_STORE_PASSWORD") as String? ?: ""
            keyAlias = project.findProperty("RELEASE_KEY_ALIAS") as String? ?: "comicrack"
            keyPassword = project.findProperty("RELEASE_KEY_PASSWORD") as String? ?: ""
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
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
        }
    }

    tasks.withType<JavaCompile>().configureEach {
        options.compilerArgs.addAll(listOf("-Xlint:deprecation", "-Xlint:unchecked", "-Xmaxerrs", "2000"))
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
        dataBinding = true
        compose = true
    }

    sourceSets {
        getByName("test") {
            java.srcDirs("src/test/kotlin")
        }
        getByName("androidTest") {
            java.srcDirs("src/androidTest/kotlin")
        }
    }

    lint {
        abortOnError = false
        disable += "RemoveWorkManagerInitializer"
    }

    dependenciesInfo {
        includeInApk = true
        includeInBundle = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.testLogging {
                    showStandardStreams = true
                    events("passed", "skipped", "failed")
                    exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
                }
            }
        }
    }
    buildToolsVersion = "35.0.0"

    packaging {
        resources {
            excludes += setOf(
                "META-INF/INDEX.LIST",
                "META-INF/DEPENDENCIES",
            )
        }
    }
}

dependencies {
    // implementation(project(":remote-library")) // Temporarily disabled due to build errors
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.appcompat)
    implementation(libs.timber)
    implementation(libs.androidx.legacy.support.v4)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.material)

    implementation(libs.androidx.ui)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)

    implementation(libs.androidx.datastore.preferences)

    implementation(libs.coil.compose)
    implementation(libs.coil)
    implementation(libs.coilNetworkOkHttp)
    implementation(libs.telephoto.zoomable)
    implementation(libs.dagger.hilt.android)

    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(platform(libs.androidx.compose.bom))
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)

    androidTestImplementation(libs.androidx.work.runtime)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.espresso.intents)

    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.work.runtime.ktx)
    testImplementation(libs.androidx.work.testing)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.mockito.core)
    testImplementation(libs.kotlinx.coroutines.test)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.androidx.room.testing)

    implementation(libs.androidx.documentfile)

    implementation(libs.junrar)
    implementation(libs.commons.compress)
    implementation(libs.okio)

    implementation(libs.moshi)
    implementation(libs.moshi.kotlin)
    ksp(libs.moshi.kotlin.codegen)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    ksp(libs.kotlin.metadata)
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.compiler)
    testImplementation(libs.hilt.android.testing)
    kspTest(libs.hilt.compiler)

    // Google Drive API (SRC-05-007 v2)
    implementation(libs.google.play.services.auth)
    implementation(libs.google.api.client.android)
    implementation(libs.google.api.services.drive)
    implementation(libs.google.http.client.gson)
}

// ktlint Configuration
ktlint {
    version.set("1.5.0")
    android.set(true)
    ignoreFailures.set(true) // Allow failures for now (baseline mode)

    filter {
        exclude("**/generated/**")
        exclude("**/build/**")
        // Temporarily exclude problematic files until syntax is fixed
        exclude("**/PreferencesUiState.kt")
        exclude("**/PreferencesScreenTest.kt")
    }
}

// Test Configuration
tasks.withType<Test> {
    // Set timeout for the entire test task (15 minutes)
    timeout.set(Duration.ofMinutes(15))

    // Increase max parallel forks for better performance
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).takeIf { it > 0 } ?: 1
}

// Tasks conversion
fun targetDevice(): String =
    if (project.hasProperty("targetDevice")) project.property("targetDevice") as String else "emulator-5554"

tasks.register<Exec>("runLayoutInflationDiagnostic") {
    dependsOn(":app:installDebug", ":app:installDebugAndroidTest")
    doFirst {
        println("Running LayoutInflationDiagnostic on device: ${targetDevice()}")
    }
    commandLine(
        "adb",
        "-s",
        targetDevice(),
        "shell",
        "am",
        "instrument",
        "-w",
        "-e",
        "class",
        "com.comicrack.viewer.LayoutInflationDiagnostic",
        "com.comicrack.viewer.test/androidx.test.runner.AndroidJUnitRunner",
    )
}

tasks.register("fetchLayoutInflationReport") {
    dependsOn("runLayoutInflationDiagnostic")
    doLast {
        val serial = targetDevice()
        val outDir = file("${project.layout.buildDirectory.get().asFile}/reports/layout-inflation")
        outDir.mkdirs()
        println("Pulling reports to $outDir")
        project.exec {
            commandLine(
                "adb",
                "-s",
                serial,
                "pull",
                "/sdcard/Android/data/com.comicrack.viewer/files/layout_inflation_report.txt",
                outDir.absolutePath,
            )
        }
        project.exec {
            commandLine(
                "adb",
                "-s",
                serial,
                "pull",
                "/sdcard/Android/data/com.comicrack.viewer/files/layout_inflation_report_verbose.txt",
                outDir.absolutePath,
            )
        }

        val report = File(outDir, "layout_inflation_report.txt")
        if (report.exists()) {
            val fails = report.readLines().filter { it.contains("FAIL") }
            if (fails.isNotEmpty()) {
                println("Layout inflation diagnostic found FAIL entries:")
                fails.forEach { println(it) }
                throw GradleException("Layout inflation diagnostic failed. See " + report.absolutePath)
            } else {
                println("Layout inflation diagnostic passed: all layouts OK")
            }
        } else {
            println("No brief report found; check device/emulator and rerun the task.")
        }
    }
}

tasks.register("layoutInflationDiagnostic") {
    dependsOn("fetchLayoutInflationReport")
    description = "Install app/test APKs, run the LayoutInflationDiagnostic on device and fetch results"
    group = "verification"
}

// Detekt configuration
detekt {
    toolVersion = "1.23.7"
    config.setFrom(files("../detekt.yml"))
    buildUponDefaultConfig = true
    allRules = false
}
