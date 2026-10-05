package com.mrturingdev.ssclassify.data

import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

actual class ThumbnailLoader actual constructor() {

    private val maxMemoryKb = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSizeKb = (maxMemoryKb / 8).coerceIn(32 * 1024, 128 * 1024)
    private val cache = object : LruCache<String, ImageBitmap>(cacheSizeKb) {
        override fun sizeOf(key: String, value: ImageBitmap): Int =
            value.width * value.height * 4 / 1024
    }

    // A fling requests dozens of thumbnails at once; unbounded IO threads starve the UI thread of CPU.
    private val decodeDispatcher = Dispatchers.IO.limitedParallelism(3)

    actual suspend fun load(id: String, sizePx: Int): ImageBitmap? = withContext(decodeDispatcher) {
        val key = "$id@$sizePx"
        cache.get(key) ?: decode(id, sizePx)?.also { cache.put(key, it) }
    }

    private fun decode(id: String, sizePx: Int): ImageBitmap? {
        val resolver = AndroidApp.context.contentResolver
        val uri = Uri.parse(id)
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(resolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    // Exact size, short side = sizePx: power-of-two sampling left screenshots ~3x too large.
                    val (w, h) = info.size.width to info.size.height
                    val scale = sizePx.toFloat() / minOf(w, h)
                    if (scale < 1f) {
                        decoder.setTargetSize((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1))
                    }
                }.asImageBitmap()
            } else {
                decodeLegacy(resolver, uri, sizePx)
            }
        } catch (_: IOException) {
            null
        } catch (_: SecurityException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun decodeLegacy(resolver: android.content.ContentResolver, uri: Uri, sizePx: Int): ImageBitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = computeSampleSize(bounds.outWidth, bounds.outHeight, sizePx)
        }
        resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)?.let { return it.asImageBitmap() }
        }
        return null
    }

    private fun computeSampleSize(width: Int, height: Int, sizePx: Int): Int {
        if (sizePx <= 0) return 1
        var sample = 1
        while (width / (sample * 2) >= sizePx || height / (sample * 2) >= sizePx) {
            sample *= 2
        }
        return sample
    }
}