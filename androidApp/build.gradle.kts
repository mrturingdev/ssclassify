import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.paparazzi)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
    sourceSets {
        androidMain.dependencies {
            implementation(project(":shared"))
            implementation(project(":aicore"))
            implementation(libs.androidx.activity.compose)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(libs.sentry.android.core)
        }
        androidUnitTest.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.kotlin.test)
        }
    }
}

android {
    namespace = "com.mrturingdev.ssclassify.android"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.mrturingdev.ssclassify.android"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }
    val signingProps = Properties().apply {
        val f = rootProject.file("local.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }
    signingConfigs {
        create("upload") {
            val store = signingProps.getProperty("SS_STORE_FILE")
            if (store != null) {
                storeFile = rootProject.file(store)
                storePassword = signingProps.getProperty("SS_STORE_PASSWORD")
                keyAlias = signingProps.getProperty("SS_KEY_ALIAS")
                keyPassword = signingProps.getProperty("SS_KEY_PASSWORD")
            }
        }
    }
    // BuildConfig.BUILD_TYPE limits telemetry to release builds (not debug or benchmark).
    buildFeatures {
        buildConfig = true
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            // Only sign when a keystore is configured locally, so CI/other machines still build unsigned.
            if (signingProps.getProperty("SS_STORE_FILE") != null) {
                signingConfig = signingConfigs.getByName("upload")
            }
        }
        // Release-like build for :classyBenchmark. Debug-signed so it installs over a dev build and keeps its data.
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
    }
    packaging {
        jniLibs {
            useLegacyPackaging = false // Enforces uncompressed, 16KB-aligned native libs
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

tasks.withType<Test>().configureEach {
    // Required workaround for Paparazzi and Gradle 9 compatibility
    reports.html.required = false
    // Play requires targetSdk 36, which forces compileSdk 36, and no Paparazzi release
    // (through 2.0.0-alpha01) ships a layoutlib for API 36. Skip the golden-image test
    // instead of failing the build; delete this filter once Paparazzi supports API 36.
    if (libs.versions.android.compileSdk.get().toInt() > 35) {
        filter { excludeTestsMatching("*ComposeScreenshotTest*") }
    }
}