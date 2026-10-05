package com.mrturingdev.ssclassify.android.widget

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.mrturingdev.ssclassify.data.AndroidApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Decodes the `content://` screenshot [id] center-cropped to exactly [widthPx] x [heightPx],
 * the shape of one widget cell, so the launcher shows it like the app's cropped tiles.
 * Null when the file is gone or unreadable (e.g. photo permission revoked).
 */
suspend fun loadPreview(id: String, widthPx: Int, heightPx: Int): Bitmap? = withContext(Dispatchers.IO) {
    val resolver = AndroidApp.context.contentResolver
    val uri = Uri.parse(id)
    try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

        // Largest power-of-two sample that still covers the cell after cropping.
        val cover = maxOf(widthPx.toFloat() / bounds.outWidth, heightPx.toFloat() / bounds.outHeight)
        var sample = 1
        while (cover * sample * 2 <= 1f) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: return@withContext null

        val scale = maxOf(widthPx.toFloat() / decoded.width, heightPx.toFloat() / decoded.height)
        val cropWidth = (widthPx / scale).toInt().coerceIn(1, decoded.width)
        val cropHeight = (heightPx / scale).toInt().coerceIn(1, decoded.height)
        val cropped = Bitmap.createBitmap(
            decoded,
            (decoded.width - cropWidth) / 2,
            (decoded.height - cropHeight) / 2,
            cropWidth,
            cropHeight,
        )
        Bitmap.createScaledBitmap(cropped, widthPx, heightPx, true)
    } catch (e: Exception) {
        null
    }
}
