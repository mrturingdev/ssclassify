package com.agy.imagecategorizer.android

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.agy.imagecategorizer.data.ThumbnailLoader
import com.agy.imagecategorizer.model.CategorySource
import com.agy.imagecategorizer.model.ImageCategory
import com.agy.imagecategorizer.model.ImageRecord
import com.agy.imagecategorizer.ui.ScreenshotDetailScreen
import org.junit.Rule
import org.junit.Test

class ComposeScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5,
        theme = "android:Theme.Material.Light.NoActionBar",
    )

    private val sampleReceipt = ImageRecord(
        id = "sample_1",
        name = "Screenshot_20260928_Receipt.png",
        folder = "Screenshots",
        relativePath = "Pictures/Screenshots",
        width = 1080,
        height = 2400,
        dateMillis = 1790600000000L,
        category = ImageCategory.Receipts,
        subCategory = "Receipt",
        description = "Objects: receipt. Text: Total $45.00",
        source = CategorySource.Rules,
        ocrText = """
            Bhatbhateni Supermarket
            Tax Invoice / Cash Receipt
            Date: 2026-09-28
            Subtotal: $40.00
            Tax: $5.00
            Total: $45.00
            Thank you for shopping with us!
        """.trimIndent(),
    )

    @Test
    fun screenshotDetailScreenPreview() {
        paparazzi.snapshot {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ScreenshotDetailScreen(
                        image = sampleReceipt,
                        thumbnailLoader = ThumbnailLoader(),
                        onBack = {},
                        onOpenFullscreen = {},
                        onCategoryChange = {},
                    )
                }
            }
        }
    }
}
