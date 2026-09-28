@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.agy.imagecategorizer.data

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import org.jetbrains.skia.Image as SkiaImage
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.Photos.PHAsset
import platform.Photos.PHImageContentModeAspectFill
import platform.Photos.PHImageManager
import platform.Photos.PHImageRequestOptions
import platform.Photos.PHImageRequestOptionsDeliveryModeFastFormat
import platform.Photos.PHImageRequestOptionsResizeModeFast
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIScreen
import platform.posix.memcpy
import kotlin.coroutines.resume

actual class ThumbnailLoader actual constructor() {

    private val manager = PHImageManager.defaultManager()
    private val mutex = Mutex()
    private val cache = mutableMapOf<String, ImageBitmap>()

    actual suspend fun load(id: String, sizePx: Int): ImageBitmap? = mutex.withLock {
        cache[id] ?: fetch(id, sizePx).also { bitmap ->
            if (bitmap != null) cache[id] = bitmap
        }
    }

    private suspend fun fetch(id: String, sizePx: Int): ImageBitmap? {
        val asset = PHAsset.fetchAssetsWithLocalIdentifiers(listOf(id), null)
            .firstObject() as? PHAsset
            ?: return null

        val options = PHImageRequestOptions().apply {
            deliveryMode = PHImageRequestOptionsDeliveryModeFastFormat
            resizeMode = PHImageRequestOptionsResizeModeFast
            synchronous = true
        }
        val scale = UIScreen.mainScreen.scale
        val targetSize = CGSizeMake(sizePx.toDouble() * scale, sizePx.toDouble() * scale)

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

    private fun decodeToBitmap(image: UIImage): ImageBitmap? {
        val data = UIImageJPEGRepresentation(image, 0.85) ?: return null
        return SkiaImage.makeFromEncoded(data.toByteArray()).toComposeImageBitmap()
    }
}

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