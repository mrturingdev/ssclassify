package com.mrturingdev.ssclassify.data

import com.mrturingdev.ssclassify.classify.ObjectResolver
import com.mrturingdev.ssclassify.classify.ObjectSource
import com.mrturingdev.ssclassify.classify.OcrHelper
import com.mrturingdev.ssclassify.classify.OcrMeaningProvider
import com.mrturingdev.ssclassify.classify.OcrTextProcessor
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970
import platform.Photos.PHAccessLevelReadWrite
import platform.Photos.PHAsset
import platform.Photos.PHAssetMediaSubtypePhotoScreenshot
import platform.Photos.PHAssetMediaTypeImage
import platform.Photos.PHAssetResource
import platform.Photos.PHAuthorizationStatusAuthorized
import platform.Photos.PHAuthorizationStatusLimited
import platform.Photos.PHAuthorizationStatusNotDetermined
import platform.Photos.PHChange
import platform.Photos.PHFetchOptions
import platform.Photos.PHPhotoLibrary
import platform.Photos.PHPhotoLibraryChangeObserverProtocol
import platform.darwin.NSObject
import kotlin.coroutines.resume

actual class MediaScanner actual constructor() : ScreenshotSource {

    actual fun watchChanges(): Flow<Unit> = callbackFlow {
        val library = PHPhotoLibrary.sharedPhotoLibrary()
        val observer = object : NSObject(), PHPhotoLibraryChangeObserverProtocol {
            override fun photoLibraryDidChange(changeInstance: PHChange) {
                trySend(Unit)
            }
        }
        library.registerChangeObserver(observer)
        awaitClose { library.unregisterChangeObserver(observer) }
    }

    override suspend fun ensureAccess(): Boolean {
        var status = PHPhotoLibrary.authorizationStatus()
        if (status == PHAuthorizationStatusNotDetermined) status = requestAccess()
        return isAuthorized(status)
    }

    actual override suspend fun deleteScreenshots(ids: List<String>): List<String> =
        suspendCancellableCoroutine { cont ->
            val library = PHPhotoLibrary.sharedPhotoLibrary()
            val fetchResult = PHAsset.fetchAssetsWithLocalIdentifiers(ids, null)
            val assetsToDelete = mutableListOf<PHAsset>()
            for (i in 0 until fetchResult.count.toInt()) {
                (fetchResult.objectAtIndex(i.toULong()) as? PHAsset)?.let { assetsToDelete += it }
            }
            if (assetsToDelete.isEmpty()) {
                cont.resume(emptyList())
                return@suspendCancellableCoroutine
            }
            library.performChanges({
                platform.Photos.PHAssetChangeRequest.deleteAssets(fetchResult)
            }) { success, _ ->
                if (success) {
                    cont.resume(ids)
                } else {
                    cont.resume(emptyList())
                }
            }
        }

    private suspend fun requestAccess(): Long =
        suspendCancellableCoroutine { cont ->
            PHPhotoLibrary.requestAuthorizationForAccessLevel(PHAccessLevelReadWrite) { status ->
                cont.resume(status)
            }
        }

    private fun isAuthorized(status: Long): Boolean =
        status == PHAuthorizationStatusAuthorized ||
            status == PHAuthorizationStatusLimited

    override suspend fun listScreenshots(): List<ScreenshotAsset> {
        val result = PHAsset.fetchAssetsWithOptions(PHFetchOptions())
        val assets = mutableListOf<ScreenshotAsset>()
        for (i in 0 until result.count.toInt()) {
            val asset = result.objectAtIndex(i.toULong()) as? PHAsset ?: continue
            if (asset.mediaType != PHAssetMediaTypeImage) continue
            if (asset.mediaSubtypes and PHAssetMediaSubtypePhotoScreenshot == 0uL) continue
            val created = asset.creationDate?.toMillis() ?: 0L
            assets += ScreenshotAsset(
                id = asset.localIdentifier,
                name = resourceFilename(asset) ?: "asset_${asset.localIdentifier}",
                folder = "",
                relativePath = "",
                width = asset.pixelWidth.toInt(),
                height = asset.pixelHeight.toInt(),
                dateMillis = created,
                modifiedMillis = asset.modificationDate?.toMillis() ?: created,
            )
        }
        return assets
    }

    override suspend fun analyze(
        assets: List<ScreenshotAsset>,
        onResult: (ScreenshotAsset, ScreenshotAnalysis) -> Unit,
    ) {
        if (assets.isEmpty()) return
        val ocr = OcrHelper()
        val meaningProvider = OcrMeaningProvider()
        for (item in assets) {
            val asset = PHAsset.fetchAssetsWithLocalIdentifiers(listOf(item.id), null)
                .firstObject() as? PHAsset ?: continue
            // No image classifier on iOS yet: the object can only come from the text.
            val lines = ocr.recognizeLines(asset)
            val text = OcrTextProcessor.process(lines.orEmpty())
            val detected = ObjectResolver.fromText(text.filtered)
            val meaning = meaningProvider.extractMeaning(text.raw, text.filtered, text.summary)
            onResult(
                item,
                ScreenshotAnalysis(
                    rawText = text.raw,
                    filteredText = text.filtered,
                    detailText = text.detail,
                    title = text.title,
                    ocrFailed = lines == null,
                    subCategory = detected?.label ?: meaning.subCategory,
                    objectSource = detected?.source ?: if (meaning.subCategory != null) ObjectSource.Ocr else null,
                    description = meaning.message.takeIf { it.isNotBlank() && it != "No text detected" },
                ),
            )
        }
    }

    private fun resourceFilename(asset: PHAsset): String? =
        (PHAssetResource.assetResourcesForAsset(asset).firstOrNull() as? PHAssetResource)
            ?.originalFilename

    private fun NSDate.toMillis(): Long = (timeIntervalSince1970 * 1000.0).toLong()
}