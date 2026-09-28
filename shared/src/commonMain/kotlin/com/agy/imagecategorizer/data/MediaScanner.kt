package com.agy.imagecategorizer.data

import com.agy.imagecategorizer.model.ImageRecord
import kotlinx.coroutines.flow.Flow

sealed interface ScanOutcome {
    data class Success(val images: List<ImageRecord>) : ScanOutcome
    data class Failure(val reason: String) : ScanOutcome
}

/** Cheap photo-library metadata; [modifiedMillis] changes whenever the file does. */
data class ScreenshotAsset(
    val id: String,
    val name: String,
    val folder: String,
    val relativePath: String,
    val width: Int,
    val height: Int,
    val dateMillis: Long,
    val modifiedMillis: Long,
)

/** Result of the expensive on-device pass (OCR, plus image labels on Android). */
data class ScreenshotAnalysis(
    val ocrText: String,
    val subCategory: String? = null,
    val description: String? = null,
)

/** Where screenshots come from; [ScreenshotRepository] only talks to this. */
interface ScreenshotSource {
    /** Requests access if it was never asked; false when denied. */
    suspend fun ensureAccess(): Boolean

    /** Lists screenshots only, metadata only. Blocking I/O. */
    suspend fun listScreenshots(): List<ScreenshotAsset>

    /** Analyzes [assets] one by one, reporting each as soon as it is done. Blocking. */
    suspend fun analyze(assets: List<ScreenshotAsset>, onResult: (ScreenshotAsset, ScreenshotAnalysis) -> Unit)
}

/**
 * Platform media-library access. Each platform provides a real implementation
 * (MediaStore on Android, PHPhotoLibrary on iOS).
 */
expect class MediaScanner() : ScreenshotSource {
    fun watchChanges(): Flow<Unit>
}
