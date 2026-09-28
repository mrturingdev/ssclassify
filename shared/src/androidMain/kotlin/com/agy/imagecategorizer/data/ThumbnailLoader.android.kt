package com.agy.imagecategorizer.data

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

    private val cache = object : LruCache<String, ImageBitmap>(64) {
        override fun sizeOf(key: String, value: ImageBitmap): Int =
            value.width * value.height * 4 / 1024
    }

    actual suspend fun load(id: String, sizePx: Int): ImageBitmap? = withContext(Dispatchers.IO) {
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
                    decoder.setTargetSampleSize(computeSampleSize(info.size.width, info.size.height, sizePx))
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