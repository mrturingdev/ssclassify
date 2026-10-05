package com.mrturingdev.ssclassify.widget

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mrturingdev.ssclassify.classify.ScreenshotContentSummarizer
import com.mrturingdev.ssclassify.model.ImageCategory
import com.mrturingdev.ssclassify.model.ImageRecord

/** One category the home-screen widget can show; [key] is [ALL_KEY] or an [ImageCategory] name. */
data class WidgetCategory(val key: String, val label: String, val count: Int)

/** A widget tile, captioned like the app's grid card. */
data class WidgetItem(val id: String, val badge: String?, val summary: String?)

/** What the home-screen widgets show, built from the same records and captions as the app grid. */
object WidgetFeed {
    const val ALL_KEY = "all"

    /** The most a widget shows at once (a 3x3 grid). */
    const val MAX_ITEMS = 9

    /** "All" first, then every category that has screenshots, in the app's chip order. */
    fun categories(records: List<ImageRecord>): List<WidgetCategory> {
        val counts = records.groupingBy { it.category }.eachCount()
        return listOf(WidgetCategory(ALL_KEY, "All", records.size)) +
            ImageCategory.entries.mapNotNull { category ->
                counts[category]?.let { WidgetCategory(category.name, category.displayName, it) }
            }
    }

    /** Newest first, at most [limit]. An unknown key (a category that emptied) falls back to All. */
    fun items(records: List<ImageRecord>, key: String, limit: Int = MAX_ITEMS): List<WidgetItem> =
        records.asSequence()
            .filter { key == ALL_KEY || it.category.name == key }
            .take(limit)
            .map { it.toWidgetItem() }
            .toList()

    /** The category [by] steps from [current], wrapping around; unknown keys start from All. */
    fun step(categories: List<WidgetCategory>, current: String, by: Int): String {
        if (categories.isEmpty()) return ALL_KEY
        val index = categories.indexOfFirst { it.key == current }.coerceAtLeast(0)
        return categories[(index + by).mod(categories.size)].key
    }

    /** [key] if it still has screenshots, else All. */
    fun resolve(categories: List<WidgetCategory>, key: String?): WidgetCategory =
        categories.firstOrNull { it.key == key } ?: categories.first()

    private fun ImageRecord.toWidgetItem(): WidgetItem {
        // Same caption rules as ImageCard in HomeScreen.
        val summary = ScreenshotContentSummarizer.summarizeDigest(ocrText, description)
        return WidgetItem(
            id = id,
            badge = subCategory?.takeIf { it.isNotEmpty() },
            summary = summary.takeIf { it.isNotBlank() && it != "No text detected" },
        )
    }
}

/** Tells the platform's home-screen widgets that the analyzed library changed. */
expect fun publishWidgetSnapshot(records: List<ImageRecord>)

/** A screenshot to open in the detail page, requested from outside the app UI (a widget tap). */
object DeepLinks {
    var pendingScreenshotId by mutableStateOf<String?>(null)
}
