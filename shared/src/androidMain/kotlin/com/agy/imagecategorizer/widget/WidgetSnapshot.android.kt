package com.agy.imagecategorizer.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import com.agy.imagecategorizer.data.AndroidApp
import com.agy.imagecategorizer.model.ImageRecord

// The widget runs in this app's process and reads the cache itself, so a bare
// update broadcast is enough; the receiver lives in :androidApp.
actual fun publishWidgetSnapshot(records: List<ImageRecord>) {
    val context = AndroidApp.context
    context.sendBroadcast(Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).setPackage(context.packageName))
}
