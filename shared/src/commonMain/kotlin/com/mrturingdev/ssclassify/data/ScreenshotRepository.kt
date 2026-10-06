package com.mrturingdev.ssclassify.data

import app.cash.sqldelight.db.SqlDriver
import com.mrturingdev.ssclassify.PERMISSION_DENIED
import com.mrturingdev.ssclassify.classify.CorrectionLearner
import com.mrturingdev.ssclassify.classify.ObjectSource
import com.mrturingdev.ssclassify.classify.OcrTextProcessor
import com.mrturingdev.ssclassify.classify.ScreenshotCategorizer
import com.mrturingdev.ssclassify.db.Screenshot
import com.mrturingdev.ssclassify.db.ScreenshotDatabase
import com.mrturingdev.ssclassify.model.CategorySource
import com.mrturingdev.ssclassify.model.ImageCategory
import com.mrturingdev.ssclassify.model.ImageRecord
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

/** Platform SQLite driver for [ScreenshotDatabase]. */
expect fun createSqlDriver(): SqlDriver

/**
 * Persists analyzed screenshots so a rescan only runs OCR on screenshots that
 * are new or whose file changed, and drops rows for deleted screenshots.
 */
class ScreenshotRepository(
    private val source: ScreenshotSource,
    driver: SqlDriver,
) {
    private val queries = ScreenshotDatabase(driver).screenshotQueries

    /** Everything analyzed so far, newest first, without touching the photo library. */
    suspend fun cached(): List<ImageRecord> = withContext(Dispatchers.IO) { loadAll() }

    /**
     * Full-text search over OCR text and file names, newest first. Every
     * word is a prefix match and all words must match; blank input returns
     * everything, like [cached].
     */
    suspend fun search(input: String): List<ImageRecord> = withContext(Dispatchers.IO) {
        val query = ftsQuery(input) ?: return@withContext loadAll()
        val learner = learnerFor(queries.selectAll(::Row).executeAsList())
        queries.search(query, ::Row).executeAsList().map { it.toRecord(learner) }
    }

    /** Pins a user-chosen category on a screenshot; null resets it to the automatic one. */
    suspend fun setCategory(id: String, category: ImageCategory?) = withContext(Dispatchers.IO) {
        if (category == null) queries.deleteOverride(id) else queries.setOverride(id, category.name)
    }

    /** Permanently deletes screenshots from the device and removes them from the local database. */
    suspend fun deleteScreenshots(ids: List<String>): List<String> = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext emptyList()
        val deleted = source.deleteScreenshots(ids)
        if (deleted.isNotEmpty()) {
            queries.transaction {
                deleted.forEach { id ->
                    queries.deleteById(id)
                    queries.deleteOverride(id)
                }
            }
        }
        deleted
    }

    /**
     * Marks every analyzed screenshot as changed so the next [scan] re-runs
     * OCR on all of them. Category corrections live in their own table and are kept.
     */
    suspend fun markAllForReanalysis() = withContext(Dispatchers.IO) { queries.markAllChanged() }

    suspend fun scan(): ScanOutcome = try {
        if (!source.ensureAccess()) {
            ScanOutcome.Failure(PERMISSION_DENIED)
        } else {
            withContext(Dispatchers.IO) {
                val stats = sync()
                ScanOutcome.Success(loadAll(), stats.takeIf { it.analyzed > 0 })
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        ScanOutcome.Failure(e.message ?: "Unexpected scan error")
    }

    private suspend fun sync(): ScanStats {
        val assets = source.listScreenshots()
        val known = queries.selectStamps().executeAsList().associate { it.id to it.modified_millis }
        val currentIds = assets.mapTo(HashSet()) { it.id }

        queries.transaction {
            known.keys.filterNot { it in currentIds }.forEach {
                queries.deleteById(it)
                queries.deleteOverride(it)
            }
        }

        // Each result is saved as it lands, so an interrupted scan keeps its progress.
        val pending = assets.filter { known[it.id] != it.modifiedMillis }
        var analyzed = 0
        var ocrFailures = 0
        var untitled = 0
        source.analyze(pending) { asset, analysis ->
            analyzed++
            if (analysis.ocrFailed) ocrFailures++
            if (analysis.title == null) untitled++
            queries.transaction {
                queries.deleteById(asset.id)
                queries.insert(
                    Screenshot(
                        id = asset.id,
                        name = asset.name,
                        folder = asset.folder,
                        relative_path = asset.relativePath,
                        width = asset.width.toLong(),
                        height = asset.height.toLong(),
                        date_millis = asset.dateMillis,
                        modified_millis = asset.modifiedMillis,
                        ocr_text = analysis.rawText,
                        sub_category = analysis.subCategory,
                        description = analysis.description,
                        filtered_text = analysis.filteredText,
                        object_source = analysis.objectSource?.name,
                        detail_text = analysis.detailText,
                        title = analysis.title,
                    ),
                )
            }
        }
        return ScanStats(analyzed, ocrFailures, untitled)
    }

    private fun loadAll(): List<ImageRecord> {
        val rows = queries.selectAll(::Row).executeAsList()
        val learner = learnerFor(rows)
        return rows.map { it.toRecord(learner) }
    }

    // ponytail: rebuilt on every load (tokenizes the whole library); cache it and invalidate on sync/setCategory if loads get slow
    private fun learnerFor(library: List<Row>) = CorrectionLearner(
        library = library.map { it.doc },
        corrections = library.mapNotNull { row -> row.override?.let { CorrectionLearner.Correction(row.doc, it) } },
    )

    private fun Row.toRecord(learner: CorrectionLearner): ImageRecord {
        val learned = learner.categorize(doc)
        val auto = learned ?: ScreenshotCategorizer.categorize(filtered, name, sub_category)
        return ImageRecord(
            id = id,
            name = name,
            folder = folder,
            relativePath = relative_path,
            width = width.toInt(),
            height = height.toInt(),
            dateMillis = date_millis,
            category = override ?: auto,
            subCategory = sub_category,
            description = description,
            autoCategory = auto,
            source = when {
                override != null -> CategorySource.User
                learned != null -> CategorySource.Learned
                else -> CategorySource.Rules
            },
            ocrText = filtered,
            rawOcrText = ocr_text,
            detailText = detail_text ?: filtered,
            title = title,
            objectSource = ObjectSource.entries.firstOrNull { it.name == object_source },
        )
    }

    /** Row shape shared by selectAll and search (same columns, so the constructor is the mapper). */
    @Suppress("unused")
    private class Row(
        val id: String,
        val name: String,
        val folder: String,
        val relative_path: String,
        val width: Long,
        val height: Long,
        val date_millis: Long,
        val modified_millis: Long,
        val ocr_text: String,
        val sub_category: String?,
        val description: String?,
        filtered_text: String?,
        val object_source: String?,
        val detail_text: String?,
        val title: String?,
        override_category: String?,
    ) {
        // An override naming a category that no longer exists falls back to automatic, or maps from legacy names.
        val override: ImageCategory? = ImageCategory.entries.firstOrNull { it.name.equals(override_category, ignoreCase = true) }
            ?: when (override_category) {
                "Travel" -> ImageCategory.Travels
                "Food" -> ImageCategory.Foods
                "Other" -> ImageCategory.Uncategorized
                "Code", "Documents" -> ImageCategory.Learning
                "Chat", "Social", "Work" -> ImageCategory.Others
                else -> null
            }

        // Rows from before v4 have no positioned lines; clean their text until the rescan replaces it.
        val filtered: String = filtered_text ?: OcrTextProcessor.cleanText(ocr_text)

        // Category signals come from the filtered text, like the keyword rules.
        val doc = CorrectionLearner.Doc(id, filtered, name)
    }
}

/**
 * Turns free text into a safe FTS4 MATCH expression: each whitespace-separated
 * word becomes a quoted prefix phrase (`"word*"`). Quotes are dropped (FTS4
 * phrases cannot escape them); anything else inside a phrase is plain text to
 * the tokenizer, so operators and punctuation in user input can never cause a
 * syntax error. Null when the input has nothing searchable.
 */
internal fun ftsQuery(input: String): String? =
    input.replace("\"", " ")
        .split(Regex("\\s+"))
        .filter { word -> word.any { it.isLetterOrDigit() } }
        .joinToString(" ") { "\"$it*\"" }
        .ifEmpty { null }
