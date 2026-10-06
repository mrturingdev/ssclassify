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
    fun qrDetection() {
        assertEquals(
            ImageCategory.QR,
            ScreenshotCategorizer.categorize("Scan QR code to pay at merchant counter"),
        )
        assertEquals(
            ImageCategory.QR,
            ScreenshotCategorizer.categorize(text = "", name = "Screenshot_2026.png", subCategory = "QR Code"),
        )
    }

    @Test
    fun learningText() {
        assertEquals(
            ImageCategory.Learning,
            ScreenshotCategorizer.categorize("fun main() {\n  val x = null\n  return x\n}\nException in thread main"),
        )
        assertEquals(
            ImageCategory.Learning,
            ScreenshotCategorizer.categorize("Course Chapter 1: Introduction to Machine Learning Lecture Quiz"),
        )
    }

    @Test
    fun travelsText() {
        assertEquals(
            ImageCategory.Travels,
            ScreenshotCategorizer.categorize("Boarding pass KTM to DEL Flight YT 123 Gate 4 Seat 12A Departure 10:30"),
        )
    }

    @Test
    fun foodsText() {
        assertEquals(
            ImageCategory.Foods,
            ScreenshotCategorizer.categorize("Dinner menu: pizza, burger, momo and coffee at Himalayan Java"),
        )
    }

    @Test
    fun healthText() {
        assertEquals(
            ImageCategory.Health,
            ScreenshotCategorizer.categorize("Doctor prescription: 500mg paracetamol dose twice daily. Heart rate 72 bpm"),
        )
    }

    @Test
    fun othersFromSamsungFileNameSuffix() {
        assertEquals(
            ImageCategory.Others,
            ScreenshotCategorizer.categorize(text = "", name = "Screenshot_20260101-120000_WhatsApp.jpg"),
        )
    }

    @Test
    fun keywordsMatchWholeWordsOnly() {
        // "total" inside "totally", "bill" inside "billion", "gate" inside "navigate"
        assertEquals(ImageCategory.Uncategorized, ScreenshotCategorizer.categorize("totally a billion ways to navigate"))
    }

    @Test
    fun emptyTextIsUncategorized() {
        assertEquals(ImageCategory.Uncategorized, ScreenshotCategorizer.categorize(""))
    }

    @Test
    fun restaurantBillPrioritizesReceiptsOverFoods() {
        val ocr = """
            Roadhouse Cafe
            Tax Invoice / Cash Receipt
            1x Margherita Pizza    $12.00
            1x Cappuccino Coffee   $4.00
            Subtotal: $16.00
            Tax: $2.08
            Total: $18.08
            Payment: Paid by Card
        """.trimIndent()
        assertEquals(ImageCategory.Receipts, ScreenshotCategorizer.categorize(ocr))
    }

    @Test
    fun foodMenuWithoutReceiptTotalIsFoods() {
        val ocr = """
            Roadhouse Cafe Menu
            Appetizers
            Garlic Bread $4.00
            Margherita Pizza $12.00
            Coffee & Beverages
            Opening hours 10:00 to 22:00
        """.trimIndent()
        assertEquals(ImageCategory.Foods, ScreenshotCategorizer.categorize(ocr))
    }

    @Test
    fun flightPaymentReceiptPrioritizesReceiptsOverTravels() {
        val ocr = """
            Airline Booking Confirmation
            Flight YT 123 KTM to DEL
            Airfare: $120.00
            Taxes: $15.00
            Total Amount Due: $0.00
            Amount Paid: $135.00
            Payment Receipt Txn: 98124
        """.trimIndent()
        assertEquals(ImageCategory.Receipts, ScreenshotCategorizer.categorize(ocr))
    }

    @Test
    fun boardingPassWithoutPaymentTotalIsTravels() {
        val ocr = """
            Yeti Airlines Boarding Pass
            Passenger: Alex Rivera
            Flight YT 123 Gate 4 Seat 12A
            Departure: 10:30 Terminal 1
        """.trimIndent()
        assertEquals(ImageCategory.Travels, ScreenshotCategorizer.categorize(ocr))
    }

    @Test
    fun pharmacyReceiptPrioritizesReceiptsOverHealth() {
        val ocr = """
            City Pharmacy
            Cash Receipt / Tax Invoice
            1x Paracetamol 500mg dose  Rs. 100
            Subtotal: Rs. 100
            Total: Rs. 100
            Paid in Cash
        """.trimIndent()
        assertEquals(ImageCategory.Receipts, ScreenshotCategorizer.categorize(ocr))
    }

    @Test
    fun doctorPrescriptionWithoutTotalIsHealth() {
        val ocr = """
            Dr. Sharma Clinic
            OPD Medical Prescription
            Patient: Alex
            Rx: Tab Paracetamol 500mg dose twice daily
            Follow up after 5 days
        """.trimIndent()
        assertEquals(ImageCategory.Health, ScreenshotCategorizer.categorize(ocr))
    }

    @Test
    fun headingWeightMultiplierPrioritizesTitleHeading() {
        // "# Boarding Pass" has weight 3x (score 3 for Travels).
        // Body has 1 "menu" (score 1 for Foods).
        val ocr = """
            # Boarding Pass
            Look at the menu later
        """.trimIndent()
        assertEquals(ImageCategory.Travels, ScreenshotCategorizer.categorize(ocr, prioritizedText = ocr))
    }
}
