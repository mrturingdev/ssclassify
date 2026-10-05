@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.mrturingdev.ssclassify.data

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import org.jetbrains.skia.Image as SkiaImage
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.Photos.PHAsset
import platform.Photos.PHImageContentModeAspectFill
import platform.Photos.PHImageManager
import platform.Photos.PHImageRequestOptions
import platform.Photos.PHImageRequestOptionsDeliveryModeHighQualityFormat
import platform.Photos.PHImageRequestOptionsResizeModeFast
import platform.UIKit.UIImage
import platform.UIKit.UIImagePNGRepresentation
import platform.posix.memcpy
import kotlin.coroutines.resume

actual class ThumbnailLoader actual constructor() {

    private val manager = PHImageManager.defaultManager()
    private val mutex = Mutex()

    // A fling requests dozens of thumbnails at once; bound the decodes so they don't starve the UI.
    private val decodeDispatcher = Dispatchers.IO.limitedParallelism(3)

    // Insertion order doubles as recency: a hit is re-inserted at the end.
    private val cache = LinkedHashMap<String, ImageBitmap>()
    private var cachedBytes = 0L

    actual suspend fun load(id: String, sizePx: Int): ImageBitmap? = withContext(decodeDispatcher) {
        val key = "$id@$sizePx"
        // Lock only the cache, so hits never queue behind a slow Photos fetch.
        mutex.withLock {
            cache.remove(key)?.let { hit ->
                cache[key] = hit
                return@withContext hit
            }
        }
        fetch(id, sizePx)?.also { mutex.withLock { put(key, it) } }
    }

    private fun put(key: String, bitmap: ImageBitmap) {
        cache[key] = bitmap
        cachedBytes += bitmap.bytes
        val oldest = cache.keys.iterator()
        while (cachedBytes > MAX_CACHE_BYTES && cache.size > 1) {
            val evicted = oldest.next()
            cachedBytes -= cache.getValue(evicted).bytes
            oldest.remove()
        }
    }

    private suspend fun fetch(id: String, sizePx: Int): ImageBitmap? {
        val asset = PHAsset.fetchAssetsWithLocalIdentifiers(listOf(id), null)
            .firstObject() as? PHAsset
            ?: return null

        // High quality returns at least the requested size; fast format hands back
        // whatever small cached thumbnail exists, which looks blurry in the grid.
        val options = PHImageRequestOptions().apply {
            deliveryMode = PHImageRequestOptionsDeliveryModeHighQualityFormat
            resizeMode = PHImageRequestOptionsResizeModeFast
            synchronous = true
        }
        // sizePx is already in pixels, as on Android.
        val targetSize = CGSizeMake(sizePx.toDouble(), sizePx.toDouble())

        return suspendCancellableCoroutine { cont ->
            manager.requestImageForAsset(
                asset,
                targetSize,
                PHImageContentModeAspectFill,
                options,
            ) { result, _ ->
                val bitmap = result?.let { decodeToBitmap(it) }
                cont.resume(bitmap)
            }
        }
    }

    // PNG, not JPEG: lossless, so screenshot text stays crisp.
    private fun decodeToBitmap(image: UIImage): ImageBitmap? {
        val data = UIImagePNGRepresentation(image) ?: return null
        return SkiaImage.makeFromEncoded(data.toByteArray()).toComposeImageBitmap()
    }

    private companion object {
        const val MAX_CACHE_BYTES = 96L * 1024 * 1024
    }
}

private val ImageBitmap.bytes: Long get() = width.toLong() * height * 4

private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size <= 0) return ByteArray(0)
    val result = ByteArray(size)
    val src = bytes() ?: return result
    result.usePinned { dst ->
        memcpy(dst.addressOf(0), src, length)
    }
    return result
}
