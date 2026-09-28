package com.agy.imagecategorizer.data

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Decodes a downscaled thumbnail for a given image id. The `id` is the same
 * string stored in [com.agy.imagecategorizer.model.ImageRecord.id].
 */
expect class ThumbnailLoader() {
    suspend fun load(id: String, sizePx: Int): ImageBitmap?
}