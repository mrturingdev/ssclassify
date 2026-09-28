package com.agy.imagecategorizer.android

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.agy.imagecategorizer.App
import com.agy.imagecategorizer.data.initAndroid

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
            App(isPermissionGranted = permissionGranted)
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