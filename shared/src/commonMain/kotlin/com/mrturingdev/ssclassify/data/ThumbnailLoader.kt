package com.mrturingdev.ssclassify.data

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Decodes a downscaled thumbnail for a given image id. The `id` is the same
 * string stored in [com.mrturingdev.ssclassify.model.ImageRecord.id].
 */
expect class ThumbnailLoader() {
    suspend fun load(id: String, sizePx: Int): ImageBitmap?
}