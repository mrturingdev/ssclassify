package com.mrturingdev.ssclassify.settings

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.mrturingdev.ssclassify.data.AndroidApp

private const val KEY_THEME = "theme_mode"

private val context: Context get() = AndroidApp.context
private val prefs get() = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

actual fun appVersion(): String {
    val info = context.packageManager.getPackageInfo(context.packageName, 0)
    val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        info.longVersionCode
    } else {
        @Suppress("DEPRECATION")
        info.versionCode.toLong()
    }
    return "${info.versionName} ($code)"
}

actual val storeName = "Google Play"

actual val widgetSteps = listOf(
    "Touch and hold an empty spot on your home screen.",
    "Tap Widgets.",
    "Find S.S. Classify and drag Recent screenshots onto your home screen.",
)

actual fun loadThemeMode(): ThemeMode =
    ThemeMode.entries.firstOrNull { it.name == prefs.getString(KEY_THEME, null) } ?: ThemeMode.System

actual fun saveThemeMode(mode: ThemeMode) {
    prefs.edit().putString(KEY_THEME, mode.name).apply()
}

actual fun loadString(key: String): String? = prefs.getString(key, null)

actual fun saveString(key: String, value: String?) {
    prefs.edit().apply { if (value == null) remove(key) else putString(key, value) }.apply()
}

// Play has one review flow for stars and text, so [writeReview] changes nothing here.
actual fun openStoreReview(writeReview: Boolean) {
    val id = context.packageName
    try {
        context.startActivity(viewIntent("market://details?id=$id"))
    } catch (_: ActivityNotFoundException) {
        // No Play Store app (e.g. an emulator without Google Play).
        context.startActivity(viewIntent("https://play.google.com/store/apps/details?id=$id"))
    }
}

private fun viewIntent(uri: String) =
    Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

@Composable
actual fun ApplySystemTheme(mode: ThemeMode, dark: Boolean) {
    val view = LocalView.current
    SideEffect {
        // Not an Activity in previews and screenshot tests.
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
}
