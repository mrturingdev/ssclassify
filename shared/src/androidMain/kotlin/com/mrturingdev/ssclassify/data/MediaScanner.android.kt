package com.mrturingdev.ssclassify.data

import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.mrturingdev.ssclassify.classify.BlankScreenDetector
import com.mrturingdev.ssclassify.classify.BlankScreenType
import com.mrturingdev.ssclassify.classify.DetectedObject
import com.mrturingdev.ssclassify.classify.ImageContentAnalyzer
import com.mrturingdev.ssclassify.classify.ObjectResolver
import com.mrturingdev.ssclassify.classify.ObjectSource
import com.mrturingdev.ssclassify.classify.OcrHelper
import com.mrturingdev.ssclassify.classify.OcrLine
import com.mrturingdev.ssclassify.classify.OcrMeaningProvider
import com.mrturingdev.ssclassify.classify.OcrTextProcessor
import com.mrturingdev.ssclassify.classify.QrCodeDetector
import com.mrturingdev.ssclassify.classify.ScreenshotCategorizer
import com.mrturingdev.ssclassify.classify.TensorFlowVisionHelper
import com.mrturingdev.ssclassify.db.ScreenshotDatabase
import com.mrturingdev.ssclassify.telemetry.AiCoreState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext

object AndroidApp {
    lateinit var context: Context
}

fun initAndroid(context: Context) {
    AndroidApp.context = context.applicationContext
}

actual fun createSqlDriver(): SqlDriver =
    AndroidSqliteDriver(ScreenshotDatabase.Schema, AndroidApp.context, "screenshots.db")

actual class MediaScanner actual constructor() : ScreenshotSource {

    actual fun watchChanges(): Flow<Unit> = callbackFlow {
        val resolver = AndroidApp.context.contentResolver
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        resolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            observer,
        )
        awaitClose { resolver.unregisterContentObserver(observer) }
    }

    // MainActivity requests the runtime permission before the UI starts.
    override suspend fun ensureAccess(): Boolean = hasPhotoAccess(AndroidApp.context)

    actual override suspend fun deleteScreenshots(ids: List<String>): List<String> = withContext(Dispatchers.IO) {
        val resolver = AndroidApp.context.contentResolver
        val deleted = mutableListOf<String>()
        for (id in ids) {
            try {
                val uri = Uri.parse(id)
                val count = resolver.delete(uri, null, null)
                if (count > 0) {
                    deleted += id
                }
            } catch (_: Exception) {
                // If permission denied or missing, do not abort remaining deletes
            }
        }
        deleted
    }

    override suspend fun listScreenshots(): List<ScreenshotAsset> {
        val resolver = AndroidApp.context.contentResolver
        val projection = buildList {
            add(MediaStore.Images.Media._ID)
            add(MediaStore.Images.Media.DISPLAY_NAME)
            add(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            add(MediaStore.Images.Media.WIDTH)
            add(MediaStore.Images.Media.HEIGHT)
            add(MediaStore.Images.Media.DATE_TAKEN)
            add(MediaStore.Images.Media.DATE_ADDED)
            add(MediaStore.Images.Media.DATE_MODIFIED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Images.Media.RELATIVE_PATH)
            }
        }
        val selection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "${MediaStore.Images.Media.IS_PENDING}=0"
        } else {
            null
        }

        val assets = mutableListOf<ScreenshotAsset>()
        resolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection.toTypedArray(),
            selection,
            null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC",
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val bucketCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
            val dateTakenCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val dateModifiedCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
            val relPathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                cursor.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)
            } else -1

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val name = cursor.getString(nameCol) ?: "image_$id"
                val bucket = cursor.getString(bucketCol) ?: ""
                val relativePath = if (relPathCol >= 0) cursor.getString(relPathCol) ?: "" else bucket
                if (!ScreenshotCategorizer.isScreenshot(name, relativePath, bucket)) continue

                val dateTaken = cursor.getLong(dateTakenCol)
                val dateAdded = cursor.getLong(dateAddedCol)
                assets += ScreenshotAsset(
                    id = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id).toString(),
                    name = name,
                    folder = bucket,
                    relativePath = relativePath,
                    width = cursor.getInt(widthCol),
                    height = cursor.getInt(heightCol),
                    dateMillis = if (dateTaken > 0) dateTaken else dateAdded * 1000L,
                    modifiedMillis = cursor.getLong(dateModifiedCol) * 1000L,
                )
            }
        }
        return assets
    }

    private var lastAiCoreState = AiCoreState.NotOnPlatform
    override val aiCoreState: AiCoreState get() = lastAiCoreState

    override suspend fun analyze(
        assets: List<ScreenshotAsset>,
        onResult: (ScreenshotAsset, ScreenshotAnalysis) -> Unit,
    ) {
        if (assets.isEmpty()) return
        val context = AndroidApp.context
        val tfHelper = TensorFlowVisionHelper(context)
        val ocrHelper = OcrHelper()
        val meaningProvider = OcrMeaningProvider()
        try {
            for (asset in assets) {
                onResult(asset, analyzeOne(context, Uri.parse(asset.id), asset.name, tfHelper, ocrHelper, meaningProvider))
            }
            lastAiCoreState = meaningProvider.aiCoreState
        } finally {
            tfHelper.close()
            ocrHelper.close()
        }
    }

    /**
     * One OCR pass over the whole image yields both the raw text and the
     * filtered central-90% text. The object comes from that text when it names
     * one; TensorFlow Lite only runs for text-light (image-first) screenshots.
     * Android AICore extracts proper meaning and synthesized message.
     */
    private suspend fun analyzeOne(
        context: Context,
        mediaUri: Uri,
        name: String,
        tfHelper: TensorFlowVisionHelper,
        ocrHelper: OcrHelper,
        meaningProvider: OcrMeaningProvider,
    ): ScreenshotAnalysis {
        val bitmap = try {
            loadBitmap(context, mediaUri)
        } catch (e: Exception) {
            null
        } ?: return ScreenshotAnalysis("", ocrFailed = true)

        try {
            val recognized = ocrHelper.recognizeLines(bitmap)
            val ocrFailed = recognized == null
            val lines = recognized.orEmpty()
            val text = OcrTextProcessor.process(lines)

            // --- QR Code detection (runs first; if found we skip other classifiers) ---
            val qrResult = QrCodeDetector.detect(bitmap)
            if (qrResult != null) {
                return ScreenshotAnalysis(
                    rawText = text.raw,
                    filteredText = text.filtered,
                    detailText = text.detail,
                    title = qrResult.title ?: text.title,
                    subCategory = qrResult.subLabel,
                    ocrFailed = ocrFailed,
                    objectSource = ObjectSource.Ocr,
                    description = qrResult.description,
                )
            }

            val detected = ObjectResolver.fromText(text.filtered)
                ?: if (ObjectResolver.needsImageModel(text.filtered)) classifyImageRegion(bitmap, lines, tfHelper) else null

            // Detect solid black or plain white screen with no text
            val blankType = if (detected == null && text.filtered.isBlank()) {
                val pixelSource = object : ImageContentAnalyzer.PixelSource {
                    override val width: Int = bitmap.width
                    override val height: Int = bitmap.height
                    override fun argb(x: Int, y: Int): Int = bitmap.getPixel(x, y)
                }
                BlankScreenDetector.detect(pixelSource, text.raw)
            } else {
                BlankScreenType.None
            }

            val finalDetected = when (blankType) {
                BlankScreenType.BlackScreen, BlankScreenType.WhiteScreen ->
                    DetectedObject(BlankScreenDetector.LABEL_BLANK_SCREEN, ObjectSource.Ocr)
                BlankScreenType.None -> detected
            }

            // Extract semantic meaning via AICore
            val meaning = meaningProvider.extractMeaning(text.raw, text.filtered, text.summary)

            val category = ScreenshotCategorizer.categorize(
                text = text.filtered,
                name = name,
                subCategory = finalDetected?.label,
                prioritizedText = text.summary,
            )
            val resolvedSubCategory = finalDetected?.label
                ?: meaning.subCategory
                ?: ObjectResolver.resolveSubCategory(text.filtered, category)
            val finalDescription = when (blankType) {
                BlankScreenType.BlackScreen -> "Solid black screen with no text."
                BlankScreenType.WhiteScreen -> "Plain white screen with no text."
                BlankScreenType.None -> if (meaning.message.isNotBlank() && meaning.message != "No text detected") {
                    meaning.message
                } else {
                    describe(finalDetected, text.filtered)
                }
            }

            return ScreenshotAnalysis(
                rawText = text.raw,
                filteredText = text.filtered,
                detailText = text.detail,
                title = meaning.headline.takeIf { meaning.fromAiCore && it.isNotBlank() } ?: text.title,
                subCategory = resolvedSubCategory,
                objectSource = finalDetected?.source ?: if (meaning.subCategory != null) ObjectSource.Ocr else null,
                description = finalDescription,
                ocrFailed = ocrFailed,
            )
        } catch (e: Exception) {
            return ScreenshotAnalysis("", ocrFailed = true) // a failed model run must not abort the whole scan
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * Classifies only the photo inside the screenshot: the tallest text-free
     * band of the focus region, trimmed of blank rows. On the whole screen
     * MobileNet sees mostly white UI and answers "web site". No band, no photo:
     * the model is skipped.
     */
    private fun classifyImageRegion(
        bitmap: Bitmap,
        lines: List<OcrLine>,
        tfHelper: TensorFlowVisionHelper,
    ): DetectedObject? {
        val (bandTop, bandBottom) = OcrTextProcessor.textFreeBand(lines) ?: return null
        val left = (bitmap.width * OcrTextProcessor.FOCUS_MARGIN).toInt()
        val right = bitmap.width - left
        var top = (bitmap.height * bandTop).toInt()
        var bottom = (bitmap.height * bandBottom).toInt()
        while (top < bottom && isBlankRow(bitmap, top, left, right)) top++
        while (bottom > top && isBlankRow(bitmap, bottom - 1, left, right)) bottom--
        if (bottom - top < bitmap.height * 0.1f) return null // only blank space, no photo
        val region = Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
        return try {
            ObjectResolver.fromImageLabels(tfHelper.classify(region))
        } finally {
            region.recycle()
        }
    }

    /** A row whose sampled pixels all match its first pixel (within a small tolerance) is background. */
    private fun isBlankRow(bitmap: Bitmap, y: Int, left: Int, right: Int): Boolean {
        val reference = bitmap.getPixel(left, y)
        var x = left
        while (x < right) {
            val pixel = bitmap.getPixel(x, y)
            val diff = kotlin.math.abs(Color.red(pixel) - Color.red(reference)) +
                kotlin.math.abs(Color.green(pixel) - Color.green(reference)) +
                kotlin.math.abs(Color.blue(pixel) - Color.blue(reference))
            if (diff > BLANK_ROW_TOLERANCE) return false
            x += 8
        }
        return true
    }

    private fun describe(detected: DetectedObject?, filtered: String): String? {
        val parts = buildList {
            if (detected?.source == ObjectSource.TensorFlowLite) add("Object: ${detected.label}.")
            val flat = filtered.replace("\n", " ").trim()
            if (flat.isNotEmpty()) add("Text: \"${if (flat.length > 50) flat.take(47) + "..." else flat}\"")
        }
        return parts.joinToString(" ").ifEmpty { null }
    }

    private companion object {
        const val BLANK_ROW_TOLERANCE = 24
    }
}

private const val OCR_MAX_SIDE = 2048

/**
 * Decodes close to full resolution for OCR: screenshot text needs its real
 * pixel height (a 512 px thumbnail shrinks a 1080x2340 screen to 236 px wide).
 * Downsamples by powers of two only above [OCR_MAX_SIDE].
 */
private fun loadBitmap(context: Context, mediaUri: Uri): Bitmap? {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(mediaUri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= OCR_MAX_SIDE) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return resolver.openInputStream(mediaUri)?.use { BitmapFactory.decodeStream(it, null, options) }
}

/**
 * Debug fixture export: one screenshot's OCR lines as JSON, exactly what
 * [OcrTextProcessor.process] receives, so calibration tests can replay them
 * without a device. Blocking; null when the image cannot be read.
 */
suspend fun ocrLinesJson(context: Context, id: String): String? {
    val bitmap = loadBitmap(context, Uri.parse(id)) ?: return null
    val ocr = OcrHelper()
    val lines = try {
        ocr.recognizeLines(bitmap).orEmpty()
    } finally {
        ocr.close()
    }
    val array = org.json.JSONArray()
    for (line in lines) {
        array.put(
            org.json.JSONObject()
                .put("text", line.text)
                .put("left", line.left.toDouble())
                .put("top", line.top.toDouble())
                .put("right", line.right.toDouble())
                .put("bottom", line.bottom.toDouble()),
        )
    }
    // The decoded QR payload too, so payment-QR parsing can be checked against real codes.
    val qr = QrCodeDetector.detect(bitmap)?.detail
    return org.json.JSONObject().put("lines", array).put("qr", qr ?: org.json.JSONObject.NULL).toString(2)
}

fun hasPhotoAccess(context: Context): Boolean {
    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        android.Manifest.permission.READ_MEDIA_IMAGES
    } else {
        android.Manifest.permission.READ_EXTERNAL_STORAGE
    }
    return context.checkSelfPermission(permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
}
