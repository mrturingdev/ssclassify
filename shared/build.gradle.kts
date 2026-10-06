import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.sqldelight)
}

// SENTRY_DSN from local.properties (or a CI secret written there) as a Kotlin constant,
// so it isn't committed. Without it the constant is empty and telemetry stays off.
val telemetryConfig = tasks.register("generateTelemetryConfig") {
    val dsn = providers.fileContents(rootProject.layout.projectDirectory.file("local.properties")).asText
        .map { text -> Properties().apply { load(text.reader()) }.getProperty("SENTRY_DSN").orEmpty() }
        .orElse("")
    val outDir = layout.buildDirectory.dir("generated/telemetry/kotlin")
    inputs.property("dsn", dsn)
    outputs.dir(outDir)
    doLast {
        val file = outDir.get().file("com/mrturingdev/ssclassify/telemetry/TelemetryConfig.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            |package com.mrturingdev.ssclassify.telemetry
            |
            |/** Generated from local.properties by :shared:generateTelemetryConfig. */
            |object TelemetryConfig {
            |    /** Empty when the build has no DSN: telemetry then sends nothing. */
            |    const val SENTRY_DSN: String = "${dsn.get()}"
            |}
            |""".trimMargin(),
        )
    }
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
            binaryOption("bundleId", "com.mrturingdev.ssclassify.shared")
        }
    }

    sourceSets {
        commonMain {
            kotlin.srcDir(telemetryConfig)
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.material.icons.extended)
            implementation(libs.compose.ui)
            implementation(libs.compose.ui.backhandler)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.ui.tooling.preview)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.sqldelight.runtime)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        androidUnitTest.dependencies {
            implementation(libs.sqldelight.sqlite.driver)
        }
        androidMain.dependencies {
            implementation(project(":aicore"))
            implementation(libs.compose.ui.tooling.preview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.tensorflow.lite.task.vision)
            implementation(libs.mlkit.text.recognition)
            implementation(libs.mlkit.barcode.scanning)
            implementation(libs.sqldelight.android.driver)
        }
        iosMain.dependencies {
            implementation(libs.sqldelight.native.driver)
        }
    }
}

sqldelight {
    databases {
        create("ScreenshotDatabase") {
            packageName.set("com.mrturingdev.ssclassify.db")
        }
    }
}

android {
    namespace = "com.mrturingdev.ssclassify"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    packaging {
        jniLibs {
            useLegacyPackaging = false // Enforces uncompressed, 16KB-aligned native libs
        }
    }

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}