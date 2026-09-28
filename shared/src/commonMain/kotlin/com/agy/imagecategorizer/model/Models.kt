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
    /** What the classifier picked; [category] is the user's pick instead when [isCategoryCorrected]. */
    val autoCategory: ImageCategory = category,
    val isCategoryCorrected: Boolean = false,
) {
    val aspectRatio: Float
        get() = if (height > 0) width.toFloat() / height.toFloat() else 0f
}
