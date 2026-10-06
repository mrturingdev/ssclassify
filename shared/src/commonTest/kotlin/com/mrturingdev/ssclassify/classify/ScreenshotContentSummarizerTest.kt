package com.mrturingdev.ssclassify.classify

import com.mrturingdev.ssclassify.model.ImageCategory
import com.mrturingdev.ssclassify.model.ImageRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScreenshotContentSummarizerTest {

    @Test
    fun extractsCleanDigestFromReceiptOcr() {
        val ocr = """
            10:45 AM  100%
            Bhatbhateni Supermarket
            Tax Invoice / Cash Receipt
            Date: 2026-09-15
            Item 1   $10.00
            Item 2   $40.00
            Subtotal: $50.00
            Total: $50.00
            Thank you for shopping
        """.trimIndent()
        val digest = ScreenshotContentSummarizer.summarizeDigest(ocr)
        assertTrue(digest.contains("Bhatbhateni Supermarket") || digest.contains("Total: $50.00"))
        assertTrue(!digest.startsWith("10:45 AM"))
    }

    @Test
    fun extractsDetectedHighlights() {
        val ocr = """
            Airline Yeti Flight YT 123
            Date: 2026-09-20
            Total: $120.50
            PNR: 98ABC
        """.trimIndent()
        val highlights = ScreenshotContentSummarizer.extractHighlights(ocr)
        val keys = highlights.map { it.first }
        assertTrue(keys.contains("Total") || keys.contains("Amount"))
    }

    @Test
    fun extractsLastPriorityAmountInHighlights() {
        val ocr = """
            Subtotal: $25.00
            Tax: $2.50
            Total: $27.50
        """.trimIndent()
        val highlights = ScreenshotContentSummarizer.extractHighlights(ocr)
        val amount = highlights.firstOrNull { it.first == "Amount" }?.second
        assertEquals("Total: $27.50", amount)
    }

    @Test
    fun fallbacksGracefullyWhenOcrEmpty() {
        val fallback = "Objects: phone, screen."
        val digest = ScreenshotContentSummarizer.summarizeDigest("", fallback)
        assertEquals(fallback, digest)
    }

    @Test
    fun formatsCleanOcrTextPreservingStructure() {
        val ocr = """
            
            Header Line  
               
            Second Line
            
        """.trimIndent()
        val clean = ScreenshotContentSummarizer.formatCleanOcrText(ocr)
        assertEquals("Header Line\nSecond Line", clean)
    }

    @Test
    fun truncatesLongDigestWithEllipsis() {
        val ocr = "This is an extremely long title line for an article that goes on and on and on and contains lots of text • Total: $100.00"
        val digest = ScreenshotContentSummarizer.summarizeDigest(ocr)
        assertTrue(digest.endsWith("..."))
        assertTrue(digest.length <= 90)
    }

    @Test
    fun digestLeadsWithLargestFontLine() {
        val digest = ScreenshotContentSummarizer.summarizeDigest("Settings\nAccount\n# Storage almost full\nFree up space soon")
        assertEquals("Storage almost full • Settings", digest)
    }

    @Test
    fun extractsCompanyNameFromLabeledMerchantField() {
        val ocr = """
            Scan & Pay
            Merchant: Himalayan Java Coffee
            Fonepay Accepted Here
            Terminal ID: 88712
        """.trimIndent()
        val company = ScreenshotContentSummarizer.extractCompanyName(ocr)
        assertEquals("Himalayan Java Coffee", company)
    }

    @Test
    fun extractsCompanyNameWithCorporateSuffix() {
        val ocr = """
            Scan to Pay
            Bhatbhateni Supermarket Pvt. Ltd.
            Merchant ID: 12345
        """.trimIndent()
        val company = ScreenshotContentSummarizer.extractCompanyName(ocr)
        assertEquals("Bhatbhateni Supermarket Pvt. Ltd.", company)
    }

    @Test
    fun extractsCompanyNameWithBusinessKeyword() {
        val ocr = """
            Roadhouse Cafe
            Scan QR Code
            Any Bank App Accepted
        """.trimIndent()
        val company = ScreenshotContentSummarizer.extractCompanyName(ocr)
        assertEquals("Roadhouse Cafe", company)
    }

    @Test
    fun returnsNullCompanyNameWhenOnlyGenericQrTokensPresent() {
        val ocr = """
            Scan to Pay
            Fonepay
            Accepted Here
            Mobile Banking
        """.trimIndent()
        val company = ScreenshotContentSummarizer.extractCompanyName(ocr)
        assertEquals(null, company)
    }

    @Test
    fun previewTextUsesCompanyNameForQrCode() {
        val ocr = """
            Scan & Pay
            Payee: Everest Bakery
            Scan here
        """.trimIndent()
        val preview = ScreenshotContentSummarizer.previewText(ocrText = ocr, isQr = true)
        assertEquals("Everest Bakery", preview)
    }

    @Test
    fun previewTextFallsBackToDigestForQrCodeWithoutCompanyName() {
        val ocr = """
            Scan & Pay
            Quick Payment System
        """.trimIndent()
        val preview = ScreenshotContentSummarizer.previewText(ocrText = ocr, isQr = true)
        assertTrue(preview.contains("Quick Payment System"))
    }

    @Test
    fun previewLeadsWithStoredTitleAndAmount() {
        val ocr = "Inbox\nOrder confirmed\nTotal 42.00\nShips in 2 days"
        assertEquals("Order confirmed • Total 42.00", ScreenshotContentSummarizer.previewText(ocr, title = "Order confirmed"))
        assertEquals("Shipping update", ScreenshotContentSummarizer.previewText("Ships in 2 days", title = "Shipping update"))
    }

    @Test
    fun qrCompanyNameBeatsStoredTitle() {
        val ocr = "Scan & Pay\nPayee: Everest Bakery"
        assertEquals("Everest Bakery", ScreenshotContentSummarizer.previewText(ocr, isQr = true, title = "Scan & Pay"))
    }

    @Test
    fun highlightKeywordsMustBeWholeWordsAndIdsNeedADigit() {
        val ocr = "MEET UTOPAI X.\nElo scores from blind preference votes in our Video Arena.\n5K paying users 300 a day"
        assertEquals(emptyList(), ScreenshotContentSummarizer.extractHighlights(ocr))
        assertEquals(
            listOf("Reference" to "Order #ORD-10001"),
            ScreenshotContentSummarizer.extractHighlights("Payment successful\nOrder #ORD-10001"),
        )
    }

    @Test
    fun amountsKeepTheirThousandsAndLakhGrouping() {
        assertEquals(
            "Payment successful • Rs. 1,250.00",
            ScreenshotContentSummarizer.previewText("Payment successful\nAmount Rs. 1,250.00", title = "Payment successful"),
        )
        assertEquals(
            listOf("Amount" to "NPR 1,25,000.00"),
            ScreenshotContentSummarizer.extractHighlights("Loan statement\nTotal NPR 1,25,000.00"),
        )
    }

    @Test
    fun abbreviatedFiguresAreNotAmounts() {
        assertEquals(
            "3. Build a life dashboard",
            ScreenshotContentSummarizer.previewText("3. Build a life dashboard\nRevenue $1.2M\n25K", title = "3. Build a life dashboard"),
        )
    }

    @Test
    fun paymentQrPreviewUsesThePayeeDecodedFromTheCode() {
        val image = ImageRecord(
            id = "a", name = "Screenshot.png", folder = "", relativePath = "", width = 1, height = 1, dateMillis = 0,
            category = ImageCategory.QR,
            subCategory = PaymentQrParser.SUB_CATEGORY,
            ocrText = "Scan to Pay\nMerchant: Blurry Standee Text\nWe Accept",
            title = "HIMALAYAN PHARMA",
        )
        assertEquals("HIMALAYAN PHARMA", ScreenshotContentSummarizer.previewText(image))
    }
}
