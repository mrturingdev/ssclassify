package com.mrturingdev.ssclassify.classify

import com.mrturingdev.ssclassify.model.ImageCategory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScreenshotCategorizerTest {

    @Test
    fun detectsScreenshotsByNameOrFolder() {
        assertTrue(ScreenshotCategorizer.isScreenshot("Screenshot_20260101-120000.png", "", ""))
        assertTrue(ScreenshotCategorizer.isScreenshot("IMG_1.png", "Pictures/Screenshots/", "Screenshots"))
        assertTrue(ScreenshotCategorizer.isScreenshot("Captura de pantalla 2026.png", "", ""))
        assertTrue(ScreenshotCategorizer.isScreenshot("Bildschirmfoto 2026.png", "", ""))
    }

    @Test
    fun ignoresCameraAndDownloadedImages() {
        assertFalse(ScreenshotCategorizer.isScreenshot("IMG_20260101_120000.jpg", "DCIM/Camera/", "Camera"))
        assertFalse(ScreenshotCategorizer.isScreenshot("glass_photo.jpg", "Download/", "Download"))
        assertFalse(ScreenshotCategorizer.isScreenshot("IMG-2026-WA0001.jpg", "WhatsApp/Media/WhatsApp Images/", "WhatsApp Images"))
    }

    @Test
    fun receiptText() {
        assertEquals(
            ImageCategory.Receipts,
            ScreenshotCategorizer.categorize("Receipt #123\nSubtotal 450.00\nTax 58.50\nTotal 508.50\nPaid"),
        )
    }

    @Test
    fun travelText() {
        assertEquals(
            ImageCategory.Travel,
            ScreenshotCategorizer.categorize("Boarding pass KTM to DEL Flight YT 123 Gate 4 Seat 12A Departure 10:30"),
        )
    }

    @Test
    fun codeText() {
        assertEquals(
            ImageCategory.Code,
            ScreenshotCategorizer.categorize("fun main() {\n  val x = null\n  return x\n}\nException in thread main"),
        )
    }

    @Test
    fun chatFromSamsungFileNameSuffix() {
        assertEquals(
            ImageCategory.Chat,
            ScreenshotCategorizer.categorize(text = "", name = "Screenshot_20260101-120000_WhatsApp.jpg"),
        )
    }

    @Test
    fun keywordsMatchWholeWordsOnly() {
        // "total" inside "totally", "bill" inside "billion", "gate" inside "navigate"
        assertEquals(ImageCategory.Other, ScreenshotCategorizer.categorize("totally a billion ways to navigate"))
    }

    @Test
    fun emptyTextIsUncategorized() {
        assertEquals(ImageCategory.Other, ScreenshotCategorizer.categorize(""))
    }
}
