package com.agy.imagecategorizer.settings

import androidx.compose.runtime.Composable

enum class ThemeMode { System, Light, Dark }

// The platform-specific parts of the Settings screen.

/** Shown at the bottom of Settings, e.g. "1.0 (1)". */
expect fun appVersion(): String

/** "Google Play" or "App Store". */
expect val storeName: String

/** How to add the home-screen widget by hand, when the app cannot pin it itself. */
expect val widgetSteps: List<String>

expect fun loadThemeMode(): ThemeMode

expect fun saveThemeMode(mode: ThemeMode)

/**
 * Sends the user to the store to rate the app. Reviews land in Play Console /
 * App Store Connect. [writeReview] asks for a written review rather than a
 * quick star rating where the platform tells the two apart.
 */
expect fun openStoreReview(writeReview: Boolean)

/** Keeps system bars and system UI (alerts, keyboard) in step with the app theme. */
@Composable
expect fun ApplySystemTheme(mode: ThemeMode, dark: Boolean)
