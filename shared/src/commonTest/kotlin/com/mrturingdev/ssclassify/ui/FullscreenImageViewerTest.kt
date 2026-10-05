package com.mrturingdev.ssclassify.ui

import androidx.compose.ui.unit.IntSize
import com.mrturingdev.ssclassify.model.CategorySource
import com.mrturingdev.ssclassify.model.ImageCategory
import com.mrturingdev.ssclassify.model.ImageRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FullscreenImageViewerTest {

    private fun testRecord(width: Int, height: Int) = ImageRecord(
        id = "test-1",
        name = "screenshot.png",
        folder = "Screenshots",
        relativePath = "Screenshots/screenshot.png",
        width = width,
        height = height,
        dateMillis = 1000L,
        category = ImageCategory.Other,
        source = CategorySource.Rules,
    )

    @Test
    fun maxPanIsZeroWhenScaleIsOneOrLess() {
        val image = testRecord(1080, 2400)
        val container = IntSize(1080, 2400)

        val (panX1, panY1) = calculateMaxPan(container, image, null, 1f)
        assertEquals(0f, panX1)
        assertEquals(0f, panY1)

        val (panX0, panY0) = calculateMaxPan(container, image, null, 0.8f)
        assertEquals(0f, panX0)
        assertEquals(0f, panY0)
    }

    @Test
    fun maxPanIsZeroWhenContainerSizeIsZero() {
        val image = testRecord(1080, 2400)
        val (panX, panY) = calculateMaxPan(IntSize.Zero, image, null, 2f)
        assertEquals(0f, panX)
        assertEquals(0f, panY)
    }

    @Test
    fun maxPanCalculatesCorrectBoundsForPortraitScreenshotOnMatchingScreen() {
        val image = testRecord(1000, 2000)
        val container = IntSize(1000, 2000)

        val (panX, panY) = calculateMaxPan(container, image, null, 2.5f)
        // Scaled dimensions: 2500 x 5000.
        // maxX = (2500 - 1000) / 2 = 750
        // maxY = (5000 - 2000) / 2 = 1500
        assertEquals(750f, panX)
        assertEquals(1500f, panY)
    }

    @Test
    fun maxPanConstrainsUnscaledAxisWhenImageFitsScreen() {
        // Landscape image 2000x1000 displayed on portrait phone 1000x2000
        // Fitted: width = 1000, height = 500
        val image = testRecord(2000, 1000)
        val container = IntSize(1000, 2000)

        // At scale 2.0:
        // Scaled width = 2000 -> maxX = (2000 - 1000) / 2 = 500
        // Scaled height = 1000 -> fits within 2000 -> maxY = 0
        val (panX, panY) = calculateMaxPan(container, image, null, 2.0f)
        assertEquals(500f, panX)
        assertEquals(0f, panY)

        // At scale 5.0:
        // Scaled width = 5000 -> maxX = (5000 - 1000) / 2 = 2000
        // Scaled height = 2500 -> exceeds 2000 -> maxY = (2500 - 2000) / 2 = 250
        val (panX5, panY5) = calculateMaxPan(container, image, null, 5.0f)
        assertEquals(2000f, panX5)
        assertEquals(250f, panY5)
    }
}
