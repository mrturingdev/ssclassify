package com.agy.imagecategorizer.classify

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
}
