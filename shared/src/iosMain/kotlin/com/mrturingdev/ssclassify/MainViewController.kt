package com.mrturingdev.ssclassify

import androidx.compose.ui.window.ComposeUIViewController
import com.mrturingdev.ssclassify.widget.DeepLinks
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController = ComposeUIViewController { App() }

/** Opens a screenshot's detail page; called from Swift for widget taps. */
fun openScreenshot(id: String) {
    DeepLinks.pendingScreenshotId = id
}
