package com.mrturingdev.ssclassify

import androidx.compose.ui.window.ComposeUIViewController
import com.mrturingdev.ssclassify.telemetry.NoopTelemetrySink
import com.mrturingdev.ssclassify.telemetry.SettingsTelemetryStore
import com.mrturingdev.ssclassify.telemetry.Telemetry
import com.mrturingdev.ssclassify.telemetry.TelemetrySink
import com.mrturingdev.ssclassify.widget.DeepLinks
import platform.UIKit.UIViewController

private var appTelemetry: Telemetry? = null

/**
 * Called once from Swift at launch, before any UI, so early crashes are
 * reported. Swift picks the sink: Sentry in release builds with a DSN,
 * otherwise [NoopTelemetrySink].
 */
fun startTelemetry(sink: TelemetrySink) {
    if (appTelemetry != null) return
    appTelemetry = Telemetry(sink, SettingsTelemetryStore()).also { it.start() }
}

fun MainViewController(): UIViewController = ComposeUIViewController {
    App(telemetry = appTelemetry ?: Telemetry(NoopTelemetrySink, SettingsTelemetryStore()))
}

/** Opens a screenshot's detail page; called from Swift for widget taps. */
fun openScreenshot(id: String) {
    DeepLinks.pendingScreenshotId = id
}
