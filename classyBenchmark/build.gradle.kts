import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidTest)
    alias(libs.plugins.kotlinAndroid)
}

android {
    namespace = "com.mrturingdev.ssclassify.benchmark"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        // Macrobenchmark needs API 23+; frame timing via FrameTimingMetric needs API 24+ (the app's floor).
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        create("benchmark") {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
    }

    targetProjectPath = ":androidApp"
    experimentalProperties["android.experimental.self-instrumenting"] = true

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

dependencies {
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.uiautomator)
}

// Only the benchmark variant is meaningful; a debug target app would measure debug-mode Compose.
androidComponents {
    beforeVariants(selector().all()) { it.enable = it.buildType == "benchmark" }
}
