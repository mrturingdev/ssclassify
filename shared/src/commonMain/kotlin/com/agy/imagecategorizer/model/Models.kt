package com.agy.imagecategorizer.model

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
    val ocrText: String = "",
) {
    val isCategoryCorrected: Boolean
        get() = source == CategorySource.User

    val aspectRatio: Float
        get() = if (height > 0) width.toFloat() / height.toFloat() else 0f
}
