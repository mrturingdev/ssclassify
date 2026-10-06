package com.mrturingdev.ssclassify.android

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.mrturingdev.ssclassify.App
import com.mrturingdev.ssclassify.android.widget.RecentScreenshotsWidgetProvider
import com.mrturingdev.ssclassify.data.initAndroid
import com.mrturingdev.ssclassify.data.ocrLinesJson
import com.mrturingdev.ssclassify.telemetry.NoopTelemetrySink
import com.mrturingdev.ssclassify.telemetry.SettingsTelemetryStore
import com.mrturingdev.ssclassify.telemetry.Telemetry
import com.mrturingdev.ssclassify.telemetry.TelemetryConfig
import com.mrturingdev.ssclassify.widget.DeepLinks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {

    private lateinit var telemetry: Telemetry

    private var contentSet = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        setAppContent(permissionGranted = granted)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        initAndroid(this)
        telemetry = appTelemetry(applicationContext)
        openRequestedScreenshot(intent)
        exportRequestedOcrLines(intent)

        if (hasPhotoPermission()) {
            setAppContent(permissionGranted = true)
        } else {
            permissionLauncher.launch(photoPermission())
        }
    }

    private fun setAppContent(permissionGranted: Boolean) {
        if (contentSet) return
        contentSet = true
        setContent {
            // Exposes Compose test tags as resource ids so :classyBenchmark (UiAutomator) can find them.
            Box(Modifier.semantics { testTagsAsResourceId = true }) {
                App(
                    isPermissionGranted = permissionGranted,
                    pinWidget = widgetPinner(),
                    exportOcrLines = if (isDebuggable()) ::shareOcrLines else null,
                    telemetry = telemetry,
                )
            }
        }
    }

    // A widget tap while the app is already open (the intent uses SINGLE_TOP).
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openRequestedScreenshot(intent)
        exportRequestedOcrLines(intent)
    }

    private fun openRequestedScreenshot(intent: Intent?) {
        intent?.getStringExtra(RecentScreenshotsWidgetProvider.EXTRA_SCREENSHOT_ID)?.let {
            DeepLinks.pendingScreenshotId = it
        }
    }

    /** Pins the recent-screenshots widget, or null when the launcher cannot. */
    private fun widgetPinner(): (() -> Unit)? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        val manager = getSystemService(AppWidgetManager::class.java)
        if (manager?.isRequestPinAppWidgetSupported != true) return null
        val provider = ComponentName(this, RecentScreenshotsWidgetProvider::class.java)
        return { manager.requestPinAppWidget(provider, null, null) }
    }

    private fun isDebuggable(): Boolean = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    /**
     * Re-runs OCR on one screenshot, saves its lines as JSON for calibration
     * fixtures (files/ocr-lines/, readable with `adb shell run-as`) and shares them.
     */
    private fun shareOcrLines(id: String) {
        lifecycleScope.launch {
            val json = withContext(Dispatchers.Default) { saveOcrLines(id) } ?: return@launch
            val send = Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, "ocr-lines.json")
                .putExtra(Intent.EXTRA_TEXT, json)
            startActivity(Intent.createChooser(send, "Export OCR lines"))
        }
    }

    private suspend fun saveOcrLines(id: String): String? {
        val json = ocrLinesJson(this, id) ?: return null
        File(filesDir, "ocr-lines").apply { mkdirs() }.resolve("${Uri.parse(id).lastPathSegment}.json").writeText(json)
        return json
    }

    /**
     * Debug only: `adb shell am start -n <pkg>/.MainActivity --es export_ocr_ids content://...,content://...`
     * saves each screenshot's OCR lines without opening the share sheet.
     */
    private fun exportRequestedOcrLines(intent: Intent?) {
        if (!isDebuggable()) return
        val ids = intent?.getStringExtra(EXTRA_EXPORT_OCR_IDS)?.split(',')?.filter { it.isNotBlank() } ?: return
        lifecycleScope.launch(Dispatchers.Default) {
            for (id in ids) {
                // One unreadable screenshot must not stop the rest (or crash the app).
                runCatching { saveOcrLines(id) }.onFailure { Log.w("OcrExport", "skipped $id", it) }
            }
        }
    }

    private fun hasPhotoPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, photoPermission()) == PackageManager.PERMISSION_GRANTED

    private fun photoPermission(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    private companion object {
        const val EXTRA_EXPORT_OCR_IDS = "export_ocr_ids"
    }
}

private var processTelemetry: Telemetry? = null

/**
 * One telemetry per process, started on first use so crashes before any UI
 * are covered. Release builds with a DSN report to Sentry; debug and
 * benchmark builds, and builds without a DSN, send nothing.
 */
private fun appTelemetry(context: android.content.Context): Telemetry = processTelemetry ?: run {
    val dsn = TelemetryConfig.SENTRY_DSN
    val sink = if (BuildConfig.BUILD_TYPE == "release" && dsn.isNotEmpty()) SentryTelemetrySink(context, dsn) else NoopTelemetrySink
    Telemetry(sink, SettingsTelemetryStore()).also {
        it.start()
        processTelemetry = it
    }
}
