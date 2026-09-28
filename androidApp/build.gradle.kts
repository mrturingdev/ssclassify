import org.jetbrains.kotlin.gradle.dsl.JvmTarget

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
            implementation(libs.androidx.activity.compose)
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
    namespace = "com.agy.imagecategorizer.android"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.agy.imagecategorizer.android"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }
    buildTypes {
        release {
            isMinifyEnabled = false
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
}