package com.mrturingdev.ssclassify.classify

import com.mrturingdev.ssclassify.classify.ImageContentAnalyzer.PixelSource
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Synthetic-grid tests for [ImageContentAnalyzer]. Grids are small, opaque,
 * fully deterministic ARGB matrices generated in pure Kotlin so commonTest
 * needs no cinterop and no image loading.
 */
class ImageContentAnalyzerTest {

    /** Packed white (opaque, 0xFFFFFFFF). */
    private val WHITE = (0xFF shl 24) or 0xFFFFFF
    /** Packed black (opaque, 0xFF000000). */
    private val BLACK = (0xFF shl 24)
    /** Dark text gray (0xFF202020). */
    private val DARK = (0xFF shl 24) or 0x202020

    /** A solid-color [PixelSource] sized [w] x [h]. */
    private fun solid(w: Int, h: Int, argb: Int): PixelSource =
        object : PixelSource {
            override val width: Int get() = w
            override val height: Int get() = h
            override fun argb(x: Int, y: Int): Int = argb
        }

    /** A white card with several dark horizontal text-like lines. */
    private fun documentGrid(w: Int = 320, h: Int = 480): PixelSource =
        object : PixelSource {
            override val width: Int get() = w
            override val height: Int get() = h
            override fun argb(x: Int, y: Int): Int {
                val line = y % 7 == 3
                return if (line) DARK else WHITE
            }
        }

    /** A busy gradient photo-like grid (sine waves). */
    private fun photoGrid(w: Int = 320, h: Int = 480): PixelSource =
        object : PixelSource {
            override val width: Int get() = w
            override val height: Int get() = h
            override fun argb(x: Int, y: Int): Int {
                val t = (x.toDouble() / w) * 6.0
                val rr = (sin(t) * 0.5 + 0.5) * 255.0
                val gg = (cos(t + PI / 3.0) * 0.5 + 0.5) * 255.0
                val bb = ((x + y) % 251).toDouble()
                return (0xFF shl 24) or (rr.toInt() shl 16) or (gg.toInt() shl 8) or bb.toInt()
            }
        }

    /** An opaque flat grid reads as document-like, not photo. */
    @Test
    fun flatSolidIsDocumentLike() {
        val f = ImageContentAnalyzer.analyze(solid(64, 64, WHITE))
        assertTrue(f.isOpaque)
        assertEquals(64, f.width)
        assertEquals(64, f.height)
        assertTrue(f.documentLike > 0.6f, "white flat grid should read document-like")
        assertTrue(f.photoLike < 0.4f, "white flat grid should not read photo-like")
    }

    /** Text-like stripes raise screenshot affinity above photo affinity. */
    @Test
    fun textStripesReadMoreScreenshotThanPhoto() {
        val doc = ImageContentAnalyzer.analyze(documentGrid())
        val photo = ImageContentAnalyzer.analyze(photoGrid())
        assertTrue(
            doc.screenshotLike > photo.screenshotLike,
            "text card must score higher screenshotLike than a photo grid",
        )
        assertTrue(
            photo.photoLike > doc.photoLike,
            "photo grid must score higher photoLike than a text card",
        )
    }

    /** Downsampled grids keep tiny and huge images comparable. */
    @Test
    fun samplerNormalizesSize() {
        val small = ImageContentAnalyzer.analyze(documentGrid(240, 320))
        val large = ImageContentAnalyzer.analyze(documentGrid(2400, 3200))
        assertEquals(small.width, large.width, "sampler must normalize width")
        assertTrue(
            kotlin.math.abs(small.documentLike - large.documentLike) < 0.15f,
            "document affinity must be size independent",
        )
    }

    /** Empty / 1-px grids degrade gracefully. */
    @Test
    fun degenerateGridsDoNotCrash() {
        val empty = ImageContentAnalyzer.analyze(
            object : PixelSource {
                override val width: Int get() = 0
                override val height: Int get() = 0
                override fun argb(x: Int, y: Int): Int = 0
            },
        )
        assertEquals(0, empty.width)
        assertEquals(0, empty.height)
        val one = ImageContentAnalyzer.analyze(solid(1, 1, BLACK))
        assertEquals(1, one.width)
        assertEquals(1, one.height)
    }

    /** Landscape grids must scan edges within bounds (width > height). */
    @Test
    fun landscapeGridDoesNotCrash() {
        val features = ImageContentAnalyzer.analyze(documentGrid(w = 480, h = 320))
        assertEquals(96, features.width)
        assertEquals(64, features.height)
        assertTrue(features.edgeDensity > 0f)
    }
}
