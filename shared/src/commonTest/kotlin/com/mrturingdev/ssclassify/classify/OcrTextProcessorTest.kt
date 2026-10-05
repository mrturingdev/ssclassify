package com.mrturingdev.ssclassify.classify

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

    @Test
    fun largerFontRowsAreMarkedAsHeadingsInReadingOrder() {
        fun sized(text: String, centerY: Float, height: Float) =
            OcrLine(text, 0.1f, centerY - height / 2, 0.9f, centerY + height / 2)
        val lines = listOf(
            sized("Order confirmed", centerY = 0.35f, height = 0.05f),
            sized("Total 42.00", centerY = 0.45f, height = 0.025f),
            sized("Ships in 2 days", centerY = 0.55f, height = 0.02f),
            sized("Contact support", centerY = 0.65f, height = 0.02f),
        )
        val text = OcrTextProcessor.process(lines)
        assertEquals("Order confirmed\nTotal 42.00\nShips in 2 days\nContact support", text.filtered)
        assertEquals("# Order confirmed\n## Total 42.00\nShips in 2 days\nContact support", text.summary)
    }

    @Test
    fun summaryReadsTheMiddleFirstThenWidensWithoutDroppingText() {
        fun sized(text: String, centerY: Float, height: Float) =
            OcrLine(text, 0.1f, centerY - height / 2, 0.9f, centerY + height / 2)
        val lines = listOf(
            sized("Starbucks", centerY = 0.08f, height = 0.05f), // outer ring
            sized("Ordered at", centerY = 0.15f, height = 0.02f), // second ring
            sized("Iced latte", centerY = 0.30f, height = 0.02f), // middle ring
            sized("Total 5.45", centerY = 0.50f, height = 0.02f), // middle ring
        )
        val text = OcrTextProcessor.process(lines)
        assertEquals("Starbucks\nOrdered at\nIced latte\nTotal 5.45", text.filtered)
        assertEquals("Iced latte\nTotal 5.45\nOrdered at\n# Starbucks", text.summary)
        // The only title is outside the middle, so detail keeps reading order.
        assertEquals(text.filtered, text.detail)
    }

    @Test
    fun allCapsPromotesARowOneLevel() {
        val lines = listOf(
            line("BOARDING PASS", centerY = 0.40f), // body size, caps -> emphasized
            line("Gate 4", centerY = 0.45f),
            line("Seat 12A", centerY = 0.50f),
            OcrLine("DELAYED", 0.1f, 0.5375f, 0.9f, 0.5625f), // emphasized size, caps -> title
            line("OK", centerY = 0.60f), // too few letters to count as caps
        )
        val text = OcrTextProcessor.process(lines)
        assertEquals("## BOARDING PASS\nGate 4\nSeat 12A\n# DELAYED\nOK", text.summary)
    }

    @Test
    fun detailMovesOuterRowsToTheBottomWhenTheMiddleHasATitle() {
        fun sized(text: String, centerY: Float, height: Float = 0.02f) =
            OcrLine(text, 0.1f, centerY - height / 2, 0.9f, centerY + height / 2)
        val lines = listOf(
            sized("Inbox", centerY = 0.10f), // outer
            sized("Search mail", centerY = 0.15f), // outer
            sized("Payment received", centerY = 0.40f, height = 0.05f), // middle title
            sized("You got 120.00 from Sam", centerY = 0.50f),
            sized("Reply", centerY = 0.85f), // outer
        )
        val text = OcrTextProcessor.process(lines)
        assertEquals("Inbox\nSearch mail\nPayment received\nYou got 120.00 from Sam\nReply", text.filtered)
        assertEquals("Payment received\nYou got 120.00 from Sam\nInbox\nSearch mail\nReply", text.detail)
    }

    @Test
    fun aBigButtonInTheMiddleIsNotASolidTitle() {
        fun sized(text: String, centerY: Float, height: Float = 0.02f) =
            OcrLine(text, 0.1f, centerY - height / 2, 0.9f, centerY + height / 2)
        val lines = listOf(
            sized("Payment received", centerY = 0.10f), // outer
            sized("You got 120.00 from Sam", centerY = 0.40f),
            sized("DONE", centerY = 0.50f, height = 0.05f), // title-size, but one short word
            sized("Share receipt", centerY = 0.55f),
        )
        val text = OcrTextProcessor.process(lines)
        assertEquals(text.filtered, text.detail)
    }

    @Test
    fun titleIsTheLargestSolidWordyRowOutsideTheEdgeBand() {
        fun sized(text: String, centerY: Float, height: Float = 0.02f) =
            OcrLine(text, 0.1f, centerY - height / 2, 0.9f, centerY + height / 2)
        val lines = listOf(
            sized("Bank of Kathmandu", centerY = 0.07f, height = 0.05f), // title-size, but in the edge band
            sized("NPR 5,000.00", centerY = 0.30f, height = 0.06f), // largest, but an amount
            sized("PAY", centerY = 0.35f, height = 0.05f), // title-size but not solid
            sized("Transfer successful", centerY = 0.45f, height = 0.04f), // title-size, middle
            sized("Amount 5,000.00", centerY = 0.55f),
            sized("Reference 88123", centerY = 0.60f),
            sized("From savings", centerY = 0.65f),
            sized("Fee 0.00", centerY = 0.70f),
        )
        assertEquals("Transfer successful", OcrTextProcessor.process(lines).title)
    }

    @Test
    fun titleFallsBackToEmphasizedThenNull() {
        fun sized(text: String, centerY: Float, height: Float = 0.02f) =
            OcrLine(text, 0.1f, centerY - height / 2, 0.9f, centerY + height / 2)
        val emphasized = listOf(
            sized("Gate 4", centerY = 0.40f),
            sized("Boarding now", centerY = 0.45f, height = 0.025f),
            sized("Seat 12A", centerY = 0.50f),
        )
        assertEquals("Boarding now", OcrTextProcessor.process(emphasized).title)
        val flat = listOf(sized("Gate 4", centerY = 0.40f), sized("Seat 12A", centerY = 0.50f))
        assertEquals(null, OcrTextProcessor.process(flat).title)
    }
}
