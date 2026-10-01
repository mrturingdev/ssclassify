package com.agy.imagecategorizer.classify

import kotlin.test.Test
import kotlin.test.assertEquals

class OcrTextProcessorTest {

    /** A line spanning [left, right] horizontally, 2% of the image tall, centered at [centerY]. */
    private fun line(text: String, centerY: Float, left: Float = 0.1f, right: Float = 0.9f) =
        OcrLine(text, left, centerY - 0.01f, right, centerY + 0.01f)

    @Test
    fun statusAndNavBarsStayInRawButLeaveFiltered() {
        val lines = listOf(
            line("10:38 LTE 85%", centerY = 0.02f),
            line("Boarding pass", centerY = 0.30f),
            line("Gate 4", centerY = 0.40f),
            line("Home Back", centerY = 0.975f),
        )
        val text = OcrTextProcessor.process(lines)
        assertEquals("10:38 LTE 85%\nBoarding pass\nGate 4\nHome Back", text.raw)
        assertEquals("Boarding pass\nGate 4", text.filtered)
    }

    @Test
    fun sideMarginsAreOutsideTheFocusRegion() {
        val lines = listOf(
            line("Receipt", centerY = 0.3f),
            line("|", centerY = 0.5f, left = 0.0f, right = 0.04f),
            line("Scrollbar", centerY = 0.5f, left = 0.96f, right = 1.0f),
        )
        assertEquals("Receipt", OcrTextProcessor.process(lines).filtered)
    }

    @Test
    fun splitColumnsAreRejoinedInReadingOrder() {
        // ML Kit often returns a receipt's label column, then its value column.
        val lines = listOf(
            line("Subtotal", 0.40f, 0.1f, 0.3f),
            line("Tax", 0.45f, 0.1f, 0.2f),
            line("Total", 0.50f, 0.1f, 0.2f),
            line("450.00", 0.401f, 0.7f, 0.9f),
            line("58.50", 0.452f, 0.7f, 0.9f),
            line("508.50", 0.498f, 0.7f, 0.9f),
        )
        val text = OcrTextProcessor.process(lines)
        assertEquals("Subtotal 450.00\nTax 58.50\nTotal 508.50", text.filtered)
        assertEquals("Subtotal\nTax\nTotal\n450.00\n58.50\n508.50", text.raw, "raw keeps engine order")
    }

    @Test
    fun meaninglessTokensGoButContentTimesAndPercentagesStay() {
        val lines = listOf(
            line("• Departure 10:30 | x", 0.3f),
            line("20% off ﬁ@#% — today", 0.4f),
            line("10:30 85% LTE", 0.5f), // a status bar that landed inside the region
            line("• | —", 0.6f),
            line("I paid a \$12.99 bill", 0.7f),
        )
        assertEquals(
            "Departure 10:30\n20% off today\nI paid a \$12.99 bill",
            OcrTextProcessor.process(lines).filtered,
        )
    }

    @Test
    fun cleanTextFiltersTokensWithoutGeometry() {
        assertEquals("Total 508.50\nMenu", OcrTextProcessor.cleanText("Total | 508.50\n• —\n12:45 LTE\nMenu"))
    }

    @Test
    fun emptyInputGivesEmptyText() {
        assertEquals(OcrText("", ""), OcrTextProcessor.process(emptyList()))
    }
}
