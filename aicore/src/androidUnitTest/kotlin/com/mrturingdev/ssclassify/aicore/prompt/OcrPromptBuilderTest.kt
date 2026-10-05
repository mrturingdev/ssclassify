package com.mrturingdev.ssclassify.aicore.prompt

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OcrPromptBuilderTest {

    @Test
    fun testSanitizeOcrText_filtersStatusBarNoise() {
        val rawOcr = """
            09:41
            100%
            LTE
            Starbucks Coffee #1289
            1x Iced Caramel Macchiato $5.45
            Total: $5.45
            Thank you for visiting!
            5G
        """.trimIndent()

        val sanitized = OcrPromptBuilder.sanitizeOcrText(rawOcr)

        assertFalse(sanitized.contains("09:41"))
        assertFalse(sanitized.contains("100%"))
        assertFalse(sanitized.contains("LTE"))
        assertTrue(sanitized.contains("Starbucks Coffee #1289"))
        assertTrue(sanitized.contains("Total: $5.45"))
    }

    @Test
    fun testBuildMeaningPrompt_containsRequiredJsonInstructions() {
        val rawOcr = "Invoice #INV-2024-001\nTotal Amount Due: $1,250.00\nDue Date: Nov 15, 2026"
        val prompt = OcrPromptBuilder.buildMeaningPrompt(rawOcr)

        assertTrue(prompt.contains("headline"))
        assertTrue(prompt.contains("message"))
        assertTrue(prompt.contains("category"))
        assertTrue(prompt.contains("entities"))
        assertTrue(prompt.contains("Invoice #INV-2024-001"))
        assertTrue(prompt.contains("Total Amount Due: $1,250.00"))
    }

    @Test
    fun testSanitizeOcrText_truncatesExcessiveLength() {
        val longText = "Important information line.\n".repeat(200)
        val sanitized = OcrPromptBuilder.sanitizeOcrText(longText, maxChars = 100)

        assertTrue(sanitized.length <= 150)
        assertTrue(sanitized.contains("[...truncated]"))
    }

    @Test
    fun testSanitizeOcrText_truncationKeepsLargerFontLinesInReadingOrder() {
        val text = (List(10) { "Body detail line number $it" } + "# Flight Delayed" + "## New departure 18:40").joinToString("\n")
        val sanitized = OcrPromptBuilder.sanitizeOcrText(text, maxChars = 100)

        val lines = sanitized.lines()
        assertTrue(lines.containsAll(listOf("# Flight Delayed", "## New departure 18:40")))
        assertTrue(lines.indexOf("# Flight Delayed") < lines.indexOf("## New departure 18:40"))
        assertTrue(lines.first() == "Body detail line number 0")
        assertTrue(lines.last() == "[...truncated]")
    }
}
