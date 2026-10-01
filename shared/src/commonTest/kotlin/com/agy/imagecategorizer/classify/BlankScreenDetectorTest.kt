package com.agy.imagecategorizer.classify

import com.agy.imagecategorizer.classify.ImageContentAnalyzer.PixelSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BlankScreenDetectorTest {

    private val WHITE = (0xFF shl 24) or 0xFFFFFF
    private val NEAR_WHITE = (0xFF shl 24) or 0xFAFAFA
    private val BLACK = (0xFF shl 24)
    private val NEAR_BLACK = (0xFF shl 24) or 0x050505
    private val RED = (0xFF shl 24) or 0xFF0000
    private val DARK_GRAY = (0xFF shl 24) or 0x303030

    private fun solid(w: Int, h: Int, argb: Int): PixelSource =
        object : PixelSource {
            override val width: Int get() = w
            override val height: Int get() = h
            override fun argb(x: Int, y: Int): Int = argb
        }

    @Test
    fun detectsSolidBlackScreen() {
        val result = BlankScreenDetector.detect(solid(100, 200, BLACK), rawText = "")
        assertEquals(BlankScreenType.BlackScreen, result)
        assertTrue(BlankScreenDetector.isBlank(result))
    }

    @Test
    fun detectsNearBlackScreen() {
        val result = BlankScreenDetector.detect(solid(100, 200, NEAR_BLACK), rawText = "")
        assertEquals(BlankScreenType.BlackScreen, result)
        assertTrue(BlankScreenDetector.isBlank(result))
    }

    @Test
    fun detectsSolidWhiteScreen() {
        val result = BlankScreenDetector.detect(solid(100, 200, WHITE), rawText = "")
        assertEquals(BlankScreenType.WhiteScreen, result)
        assertTrue(BlankScreenDetector.isBlank(result))
    }

    @Test
    fun detectsNearWhiteScreen() {
        val result = BlankScreenDetector.detect(solid(100, 200, NEAR_WHITE), rawText = "")
        assertEquals(BlankScreenType.WhiteScreen, result)
        assertTrue(BlankScreenDetector.isBlank(result))
    }

    @Test
    fun rejectsScreenWithOcrTextEvenIfWhite() {
        val result = BlankScreenDetector.detect(solid(100, 200, WHITE), rawText = "Hello World")
        assertEquals(BlankScreenType.None, result)
        assertFalse(BlankScreenDetector.isBlank(result))
    }

    @Test
    fun rejectsColoredScreen() {
        val result = BlankScreenDetector.detect(solid(100, 200, RED), rawText = "")
        assertEquals(BlankScreenType.None, result)
        assertFalse(BlankScreenDetector.isBlank(result))
    }

    @Test
    fun rejectsDarkGrayScreen() {
        val result = BlankScreenDetector.detect(solid(100, 200, DARK_GRAY), rawText = "")
        assertEquals(BlankScreenType.None, result)
        assertFalse(BlankScreenDetector.isBlank(result))
    }

    @Test
    fun rejectsCheckerboardOrMixedImage() {
        val mixed = object : PixelSource {
            override val width: Int get() = 100
            override val height: Int get() = 100
            override fun argb(x: Int, y: Int): Int = if ((x + y) % 2 == 0) BLACK else WHITE
        }
        val result = BlankScreenDetector.detect(mixed, rawText = "")
        assertEquals(BlankScreenType.None, result)
        assertFalse(BlankScreenDetector.isBlank(result))
    }
}
