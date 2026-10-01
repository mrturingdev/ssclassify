package com.agy.imagecategorizer.data

import android.appwidget.AppWidgetManager
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.agy.imagecategorizer.classify.OcrHelper
import com.agy.imagecategorizer.classify.ScreenshotCategorizer
import com.agy.imagecategorizer.classify.TensorFlowVisionHelper
import com.agy.imagecategorizer.db.ScreenshotDatabase
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

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

    override suspend fun analyze(
        assets: List<ScreenshotAsset>,
        onResult: (ScreenshotAsset, ScreenshotAnalysis) -> Unit,
    ) {
        // Runs at the end of every scan, after sync() has written or dropped rows.
        if (assets.isEmpty()) return refreshWidgets()
        val context = AndroidApp.context
        val tfHelper = TensorFlowVisionHelper(context)
        val ocrHelper = OcrHelper()
        try {
            for (asset in assets) {
                onResult(asset, analyzeOne(context, Uri.parse(asset.id), tfHelper, ocrHelper))
            }
        } finally {
            tfHelper.close()
            ocrHelper.close()
            refreshWidgets()
        }
    }

    /** Asks this app's home-screen widgets to re-read the cache; the receiver lives in :androidApp. */
    private fun refreshWidgets() {
        val context = AndroidApp.context
        context.sendBroadcast(Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).setPackage(context.packageName))
    }

    // OCR text drives the category; TF Vision labels feed the sub-category
    private fun analyzeOne(
        context: Context,
        mediaUri: Uri,
        tfHelper: TensorFlowVisionHelper,
        ocrHelper: OcrHelper,
    ): ScreenshotAnalysis {
        var ocrText = ""
        var subCategory: String? = null
        var description = ""

        try {
            val bitmap = loadBitmap(context, mediaUri) ?: return ScreenshotAnalysis("")

            // 1. TF Vision
            val results = tfHelper.classify(bitmap)
            val bestMatch = results.maxByOrNull { it.confidence }

            val tfLabels = results.filter { it.confidence > 0.15f }.take(3).map { it.label }
            if (tfLabels.isNotEmpty()) {
                description += "Objects: ${tfLabels.joinToString(", ")}. "
            }

            if (bestMatch != null && bestMatch.confidence > 0.3f) {
                // Extract the specific prominent object as the subCategory
                subCategory = bestMatch.label.split(",").first().replaceFirstChar { it.uppercase() }
            }

            // 2. OCR Text Recognition
            val rawText = ocrHelper.extractText(bitmap)
            ocrText = rawText
            val text = rawText.lowercase()
            if (text.isNotEmpty()) {
                val cleanText = rawText.replace("\n", " ").trim()
                val snippet = if (cleanText.length > 50) cleanText.take(47) + "..." else cleanText
                description += "Text: \"$snippet\""

                // Extract explicitly mentioned category if present
                val categoryRegex = Regex("category:\\s*([a-zA-Z]+)", RegexOption.IGNORE_CASE)
                val match = categoryRegex.find(rawText)

                if (match != null) {
                    subCategory = match.groupValues[1].replaceFirstChar { it.uppercase() }
                } else {
                    // Extract the specific keyword from the text rather than broad buckets
                    val specificKeywords = listOf(
                        "invoice", "receipt", "ticket", "boarding pass", "menu",
                        "balance", "retweet", "reply", "payment", "order",
                    )
                    val foundKeyword = specificKeywords.firstOrNull { text.contains(it) }
                    if (foundKeyword != null) {
                        subCategory = foundKeyword.split(" ").joinToString(" ") { word ->
                            word.replaceFirstChar { c -> c.uppercase() }
                        }
                    }
                }
            }

            bitmap.recycle() // Prevent memory leaks
        } catch (e: Exception) {
            // Ignore thumbnail/TF/OCR errors
        }
        return ScreenshotAnalysis(ocrText, subCategory, description.takeIf { it.isNotEmpty() })
    }

    // Use 512x512 so OCR has enough resolution to read text
    private fun loadBitmap(context: Context, mediaUri: Uri): Bitmap? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.contentResolver.loadThumbnail(mediaUri, android.util.Size(512, 512), null)
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Thumbnails.getThumbnail(
                context.contentResolver, ContentUris.parseId(mediaUri),
                MediaStore.Images.Thumbnails.MINI_KIND, null,
            )
        }
}

fun hasPhotoAccess(context: Context): Boolean {
    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        android.Manifest.permission.READ_MEDIA_IMAGES
    } else {
        android.Manifest.permission.READ_EXTERNAL_STORAGE
    }
    return context.checkSelfPermission(permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
}
