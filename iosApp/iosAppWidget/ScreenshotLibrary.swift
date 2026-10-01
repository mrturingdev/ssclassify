import Photos
import UIKit

/// Read-only Photos access for the widget. Mirrors MediaScanner.listScreenshots()
/// (images with the screenshot subtype) without linking the Kotlin framework.
enum ScreenshotLibrary {

    /// Never requests access: an extension cannot present the prompt.
    static var hasAccess: Bool {
        let status = PHPhotoLibrary.authorizationStatus(for: .readWrite)
        return status == .authorized || status == .limited
    }

    /// The newest screenshots, newest first.
    static func recent(limit: Int) -> [PHAsset] {
        let options = PHFetchOptions()
        options.predicate = NSPredicate(
            format: "mediaType == %d AND (mediaSubtypes & %d) != 0",
            PHAssetMediaType.image.rawValue,
            PHAssetMediaSubtype.photoScreenshot.rawValue
        )
        options.sortDescriptors = [NSSortDescriptor(key: "creationDate", ascending: false)]
        options.fetchLimit = limit
        let result = PHAsset.fetchAssets(with: options)
        return (0..<result.count).map { result.object(at: $0) }
    }

    /// A square-cropped thumbnail of [pixels] x [pixels], or nil when only iCloud has it.
    static func thumbnail(for asset: PHAsset, pixels: CGFloat) async -> UIImage? {
        let options = PHImageRequestOptions()
        options.deliveryMode = .opportunistic
        options.resizeMode = .fast
        options.isNetworkAccessAllowed = false

        return await withCheckedContinuation { continuation in
            var best: UIImage?
            var resumed = false
            PHImageManager.default().requestImage(
                for: asset,
                targetSize: CGSize(width: pixels, height: pixels),
                contentMode: .aspectFill,
                options: options
            ) { image, info in
                // Opportunistic delivery may call back twice: a degraded image, then the final one.
                best = image ?? best
                let degraded = (info?[PHImageResultIsDegradedKey] as? Bool) ?? false
                guard !degraded, !resumed else { return }
                resumed = true
                continuation.resume(returning: best)
            }
        }
    }
}
