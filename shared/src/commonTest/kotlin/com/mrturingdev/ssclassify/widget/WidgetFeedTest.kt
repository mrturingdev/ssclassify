package com.mrturingdev.ssclassify.widget

import com.mrturingdev.ssclassify.model.ImageCategory
import com.mrturingdev.ssclassify.model.ImageRecord
import kotlin.test.Test
import kotlin.test.assertEquals

class WidgetFeedTest {

    private fun record(id: String, category: ImageCategory, sub: String? = null) = ImageRecord(
        id = id,
        name = "$id.png",
        folder = "Screenshots",
        relativePath = "Screenshots/$id.png",
        width = 100,
        height = 200,
        dateMillis = 0,
        category = category,
        subCategory = sub,
    )

    private val records = listOf(
        record("1", ImageCategory.Chat),
        record("2", ImageCategory.Receipts, sub = "Receipt"),
        record("3", ImageCategory.Chat),
    )

    @Test
    fun categoriesStartWithAllThenFollowTheAppOrder() {
        assertEquals(
            listOf(
                WidgetCategory(WidgetFeed.ALL_KEY, "All", 3),
                WidgetCategory("Receipts", "Receipts", 1),
                WidgetCategory("Chat", "Chats", 2),
            ),
            WidgetFeed.categories(records),
        )
    }

    @Test
    fun itemsFilterByCategoryAndKeepTheBadge() {
        assertEquals(listOf("1", "3"), WidgetFeed.items(records, "Chat").map { it.id })
        assertEquals("Receipt", WidgetFeed.items(records, "Receipts").single().badge)
        assertEquals(listOf("1", "2"), WidgetFeed.items(records, WidgetFeed.ALL_KEY, limit = 2).map { it.id })
    }

    @Test
    fun stepWrapsBothWaysAndRecoversFromUnknownKeys() {
        val categories = WidgetFeed.categories(records)
        assertEquals("Receipts", WidgetFeed.step(categories, WidgetFeed.ALL_KEY, 1))
        assertEquals(WidgetFeed.ALL_KEY, WidgetFeed.step(categories, "Chat", 1))
        assertEquals("Chat", WidgetFeed.step(categories, WidgetFeed.ALL_KEY, -1))
        assertEquals("Receipts", WidgetFeed.step(categories, "Travel", 1)) // emptied category
        assertEquals(WidgetFeed.ALL_KEY, WidgetFeed.resolve(categories, "Travel").key)
    }
}
