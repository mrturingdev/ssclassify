package com.mrturingdev.ssclassify.android

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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
import com.mrturingdev.ssclassify.widget.DeepLinks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

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
        openRequestedScreenshot(intent)

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
                )
            }
        }
    }

    // A widget tap while the app is already open (the intent uses SINGLE_TOP).
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openRequestedScreenshot(intent)
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

    /** Re-runs OCR on one screenshot and shares its lines as JSON for calibration fixtures. */
    private fun shareOcrLines(id: String) {
        lifecycleScope.launch {
            val json = withContext(Dispatchers.Default) { ocrLinesJson(this@MainActivity, id) } ?: return@launch
            val send = Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, "ocr-lines.json")
                .putExtra(Intent.EXTRA_TEXT, json)
            startActivity(Intent.createChooser(send, "Export OCR lines"))
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
}