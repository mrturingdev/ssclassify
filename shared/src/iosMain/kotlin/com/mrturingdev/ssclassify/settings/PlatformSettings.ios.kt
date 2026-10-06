package com.mrturingdev.ssclassify.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import platform.Foundation.NSBundle
import platform.Foundation.NSURL
import platform.Foundation.NSUserDefaults
import platform.StoreKit.SKStoreReviewController
import platform.UIKit.UIApplication
import platform.UIKit.UISceneActivationStateForegroundActive
import platform.UIKit.UIUserInterfaceStyle
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene

private const val KEY_THEME = "theme_mode"

/** Info.plist key holding the numeric Apple ID from App Store Connect; empty until published. */
private const val APP_STORE_ID_KEY = "SSAppStoreID"

private val defaults get() = NSUserDefaults.standardUserDefaults

actual fun appVersion(): String {
    val bundle = NSBundle.mainBundle
    val name = bundle.objectForInfoDictionaryKey("CFBundleShortVersionString")
    val build = bundle.objectForInfoDictionaryKey("CFBundleVersion")
    return "$name ($build)"
}

actual val storeName = "App Store"

actual val widgetSteps = listOf(
    "Touch and hold an empty spot on your Home Screen until the apps jiggle.",
    "Tap Edit, then Add Widget (on iOS 16 or earlier, tap +).",
    "Search for S.S. Classify, pick Recent Screenshots and tap Add Widget.",
)

actual fun loadThemeMode(): ThemeMode =
    ThemeMode.entries.firstOrNull { it.name == defaults.stringForKey(KEY_THEME) } ?: ThemeMode.System

actual fun saveThemeMode(mode: ThemeMode) {
    defaults.setObject(mode.name, KEY_THEME)
}

actual fun loadString(key: String): String? = defaults.stringForKey(key)

actual fun saveString(key: String, value: String?) {
    if (value == null) defaults.removeObjectForKey(key) else defaults.setObject(value, key)
}

actual fun openStoreReview(writeReview: Boolean) {
    val appId = (NSBundle.mainBundle.objectForInfoDictionaryKey(APP_STORE_ID_KEY) as? String).orEmpty()
    val writeUrl = NSURL.URLWithString("https://apps.apple.com/app/id$appId?action=write-review")
    if (writeReview && appId.isNotBlank() && writeUrl != null) {
        UIApplication.sharedApplication.openURL(writeUrl, emptyMap<Any?, Any?>(), null)
    } else {
        // The system decides whether to show it; it always shows in debug builds.
        activeScene()?.let { SKStoreReviewController.requestReviewInScene(it) }
    }
}

private fun activeScene(): UIWindowScene? =
    UIApplication.sharedApplication.connectedScenes.filterIsInstance<UIWindowScene>()
        .firstOrNull { it.activationState == UISceneActivationStateForegroundActive }

@Composable
actual fun ApplySystemTheme(mode: ThemeMode, dark: Boolean) {
    SideEffect {
        val style: UIUserInterfaceStyle = when (mode) {
            ThemeMode.System -> UIUserInterfaceStyle.UIUserInterfaceStyleUnspecified
            ThemeMode.Light -> UIUserInterfaceStyle.UIUserInterfaceStyleLight
            ThemeMode.Dark -> UIUserInterfaceStyle.UIUserInterfaceStyleDark
        }
        UIApplication.sharedApplication.connectedScenes.filterIsInstance<UIWindowScene>()
            .flatMap { it.windows.filterIsInstance<UIWindow>() }
            .forEach { it.overrideUserInterfaceStyle = style }
    }
}
