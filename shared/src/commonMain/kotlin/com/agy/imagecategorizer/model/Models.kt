package com.agy.imagecategorizer.model

import com.agy.imagecategorizer.classify.ObjectSource

/** Content categories for screenshots; declaration order breaks classifier ties. */
enum class ImageCategory(val displayName: String) {
    Receipts("Receipts"),
    Finance("Finance"),
    Shopping("Shopping"),
    Travel("Travel"),
    Food("Food"),
    Health("Health"),
    Work("Work"),
    Code("Code"),
    Chat("Chats"),
    Social("Social"),
    Documents("Documents"),
    Other("Uncategorized"),
}

/** Where an [ImageRecord.category] came from, strongest first. */
enum class CategorySource {
    /** The user picked it for this screenshot. */
    User,

    /** Copied from similar screenshots the user corrected. */
    Learned,

    /** Keyword rules over OCR text and file name. */
    Rules,
}

data class ImageRecord(
    val id: String,
    val name: String,
    val folder: String,
    val relativePath: String,
    val width: Int,
    val height: Int,
    val dateMillis: Long,
    val category: ImageCategory,
    val subCategory: String? = null,
    val description: String? = null,
    /** What the app would pick on its own; [category] differs only when [source] is [CategorySource.User]. */
    val autoCategory: ImageCategory = category,
    val source: CategorySource = CategorySource.Rules,
    /** Filtered OCR text (central 90%, reading order, noise removed): what categories and summaries use. */
    val ocrText: String = "",
    /** Everything OCR read from the whole image, unprocessed. */
    val rawOcrText: String = ocrText,
    /** Which engine named [subCategory]; null when nothing did. */
    val objectSource: ObjectSource? = null,
) {
    val isCategoryCorrected: Boolean
        get() = source == CategorySource.User

    val aspectRatio: Float
        get() = if (height > 0) width.toFloat() / height.toFloat() else 0f

    val isBlankScreen: Boolean
        get() = subCategory == "Blank Screen" ||
            description?.contains("black screen", ignoreCase = true) == true ||
            description?.contains("white screen", ignoreCase = true) == true

    val isCleanUpCandidate: Boolean
        get() = isBlankScreen || category == ImageCategory.Other
}
