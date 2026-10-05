package com.mrturingdev.ssclassify.classify

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TextFreeBandTest {

    private fun line(top: Float, bottom: Float) = OcrLine("x", 0.1f, top, 0.9f, bottom)

    @Test
    fun findsTheTallestGapBetweenTextInsideTheFocusRegion() {
        // Caption at the top, photo in the middle, a button near the bottom.
        val band = OcrTextProcessor.textFreeBand(listOf(line(0.10f, 0.13f), line(0.85f, 0.88f)))
        assertEquals(0.13f to 0.85f, band)
    }

    @Test
    fun statusBarTextOutsideTheRegionDoesNotSplitIt() {
        assertEquals(0.05f to 0.95f, OcrTextProcessor.textFreeBand(listOf(line(0.0f, 0.03f))))
    }

    @Test
    fun denseTextHasNoBand() {
        val lines = (0 until 20).map { i -> line(0.05f + i * 0.045f, 0.05f + i * 0.045f + 0.03f) }
        assertNull(OcrTextProcessor.textFreeBand(lines))
    }
}
