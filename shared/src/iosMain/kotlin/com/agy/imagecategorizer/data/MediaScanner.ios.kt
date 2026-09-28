package com.agy.imagecategorizer.data

import com.agy.imagecategorizer.classify.OcrHelper
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
        for (item in assets) {
            val asset = PHAsset.fetchAssetsWithLocalIdentifiers(listOf(item.id), null)
                .firstObject() as? PHAsset ?: continue
            onResult(item, ScreenshotAnalysis(ocrText = ocr.extractText(asset)))
        }
    }

    private fun resourceFilename(asset: PHAsset): String? =
        (PHAssetResource.assetResourcesForAsset(asset).firstOrNull() as? PHAssetResource)
            ?.originalFilename

    private fun NSDate.toMillis(): Long = (timeIntervalSince1970 * 1000.0).toLong()
}