package com.agy.imagecategorizer.android.widget

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.agy.imagecategorizer.data.AndroidApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Decodes the `content://` screenshot [id] as a center-cropped [sizePx] square.
 * Null when the file is gone or unreadable (e.g. photo permission revoked).
 */
suspend fun loadPreview(id: String, sizePx: Int): Bitmap? = withContext(Dispatchers.IO) {
    val resolver = AndroidApp.context.contentResolver
    val uri = Uri.parse(id)
    try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val shortSide = minOf(bounds.outWidth, bounds.outHeight)
        if (shortSide <= 0) return@withContext null

        var sample = 1
        while (shortSide / (sample * 2) >= sizePx) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: return@withContext null

        val side = minOf(decoded.width, decoded.height)
        val square = Bitmap.createBitmap(decoded, (decoded.width - side) / 2, (decoded.height - side) / 2, side, side)
        Bitmap.createScaledBitmap(square, minOf(sizePx, side), minOf(sizePx, side), true)
    } catch (e: Exception) {
        null
    }
}
