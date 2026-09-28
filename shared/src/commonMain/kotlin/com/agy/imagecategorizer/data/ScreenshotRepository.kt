package com.agy.imagecategorizer.data

import app.cash.sqldelight.db.SqlDriver
import com.agy.imagecategorizer.PERMISSION_DENIED
import com.agy.imagecategorizer.classify.CorrectionLearner
import com.agy.imagecategorizer.classify.ScreenshotCategorizer
import com.agy.imagecategorizer.db.Screenshot
import com.agy.imagecategorizer.db.ScreenshotDatabase
import com.agy.imagecategorizer.model.CategorySource
import com.agy.imagecategorizer.model.ImageCategory
import com.agy.imagecategorizer.model.ImageRecord
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

    suspend fun scan(): ScanOutcome = try {
        if (!source.ensureAccess()) {
            ScanOutcome.Failure(PERMISSION_DENIED)
        } else {
            withContext(Dispatchers.IO) {
                sync()
                ScanOutcome.Success(loadAll())
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        ScanOutcome.Failure(e.message ?: "Unexpected scan error")
    }

    private suspend fun sync() {
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
        source.analyze(pending) { asset, analysis ->
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
                        ocr_text = analysis.ocrText,
                        sub_category = analysis.subCategory,
                        description = analysis.description,
                    ),
                )
            }
        }
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
        val auto = learned ?: ScreenshotCategorizer.categorize(ocr_text, name)
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
            ocrText = ocr_text,
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
        override_category: String?,
    ) {
        // An override naming a category that no longer exists falls back to automatic.
        val override: ImageCategory? = ImageCategory.entries.firstOrNull { it.name == override_category }
        val doc = CorrectionLearner.Doc(id, ocr_text, name)
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
