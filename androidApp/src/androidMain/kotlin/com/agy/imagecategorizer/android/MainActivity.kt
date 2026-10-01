package com.agy.imagecategorizer.android

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.agy.imagecategorizer.App
import com.agy.imagecategorizer.android.widget.RecentScreenshotsWidgetProvider
import com.agy.imagecategorizer.data.initAndroid
import com.agy.imagecategorizer.widget.DeepLinks

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
            App(isPermissionGranted = permissionGranted, pinWidget = widgetPinner())
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

    private fun hasPhotoPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, photoPermission()) == PackageManager.PERMISSION_GRANTED

    private fun photoPermission(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
}