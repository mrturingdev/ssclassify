package com.mrturingdev.ssclassify.classify

import com.mrturingdev.ssclassify.model.ImageCategory

/**
 * On-device raster analysis with no dependencies beyond the Kotlin standard
 * library. It reduces any decoded image down to a small sampled grid and
 * computes a handful of deterministic, explainable statistics that separate
 * "flat / text-heavy / low-detail" images (screenshots, documents, receipts,
 * billboards) from "rich natural content" (photos, artwork).
 *
 * Pure: [analyze] only reads [PixelSource.argb]. This keeps it unit-testable
 * in commonTest with synthetic pixel grids and reusable from every platform
 * without any cinterop.
 */
object ImageContentAnalyzer {

    /**
     * A small, downsampled view over a decoded image. Only [PixelSource.argb]
     * is needed; platforms implement it from their native bitmap/pixmap
     * (Android getPixels, iOS skia Pixmap.getColor).
     */
    interface PixelSource {
        val width: Int
        val height: Int

        /** Returns the packed color (0xAARRGGBB) at (x, y). */
        fun argb(x: Int, y: Int): Int
    }

    /** An in-memory ARGB grid backing a [PixelSource]. */
    data class ArgbGrid(
        override val width: Int,
        override val height: Int,
        private val pixels: IntArray,
    ) : PixelSource {
        override fun argb(x: Int, y: Int): Int = pixels[y * width + x]
    }

    /**
     * Deterministic, platform-independent summary of a decoded image. The
     * trailing *Like scores are 0..1 preferences; metadata heuristics keep
     * priority and content only nudges ambiguous cases (ScreenshotCategorizer).
     */
    data class ContentFeatures(
        val isOpaque: Boolean,
        val width: Int,
        val height: Int,
        val flatRatio: Float,
        val paletteSize: Int,
        val avgSaturation: Float,
        val avgBrightness: Float,
        val whiteRatio: Float,
        val vividRatio: Float,
        val edgeDensity: Float,
        val textLineDensity: Float,
        val documentLike: Float,
        val screenshotLike: Float,
        val photoLike: Float,
    )

    /** Copies a [PixelSource] into an in-memory grid capped at [maxDim]. */
    fun copyGrid(source: PixelSource, maxDim: Int = 96): ArgbGrid {
        val w = source.width
        val h = source.height
        if (w <= 0 || h <= 0) return ArgbGrid(0, 0, intArrayOf())
        val scale = minOf(maxDim.toFloat() / w, maxDim.toFloat() / h, 1f)
        if (scale >= 1f && w * h <= 4_000_000) {
            val n = w * h
            val pixels = IntArray(n)
            var i = 0
            for (y in 0 until h) {
                for (x in 0 until w) {
                    pixels[i++] = source.argb(x, y)
                }
            }
            return ArgbGrid(w, h, pixels)
        }
        val sw = maxOf(1, (w * scale).toInt())
        val sh = maxOf(1, (h * scale).toInt())
        val n = sw * sh
        val pixels = IntArray(n)
        var i = 0
        for (y in 0 until sh) {
            val sy = (y * h) / sh
            for (x in 0 until sw) {
                val sx = (x * w) / sw
                pixels[i++] = source.argb(sx, sy)
            }
        }
        return ArgbGrid(sw, sh, pixels)
    }

    /** Computes deterministic content statistics; see [ContentFeatures]. */
    fun analyze(source: PixelSource): ContentFeatures {
        val grid = copyGrid(source, 96)
        val w = grid.width
        val h = grid.height
        if (w == 0 || h == 0) {
            return ContentFeatures(
                isOpaque = true,
                width = grid.width,
                height = grid.height,
                flatRatio = 0f,
                paletteSize = 0,
                avgSaturation = 0f,
                avgBrightness = 0f,
                whiteRatio = 0f,
                vividRatio = 0f,
                edgeDensity = 0f,
                textLineDensity = 0f,
                documentLike = 0f,
                screenshotLike = 0f,
                photoLike = 0f,
            )
        }

        val n = w * h
        val luma = FloatArray(n)
        val sat = FloatArray(n)
        val palette = HashMap<Int, Int>(512)
        var sumLuma = 0.0
        var sumSat = 0.0
        var white = 0
        var vivid = 0
        var i = 0
        for (y in 0 until h) {
            for (x in 0 until w) {
                val argb = grid.argb(x, y)
                val rr = (argb shr 16) and 0xFF
                val gg = (argb shr 8) and 0xFF
                val bb = argb and 0xFF
                val l = 0.299 * rr + 0.587 * gg + 0.114 * bb
                luma[i] = l.toFloat()
                sumLuma += l
                val maxC = maxOf(rr, gg, bb)
                val minC = minOf(rr, gg, bb)
                val s = if (maxC == 0) 0.0 else (maxC - minC).toDouble() / maxC
                sat[i] = s.toFloat()
                sumSat += s
                if (rr >= 230 && gg >= 230 && bb >= 230) white++
                if (s >= 0.45 && l >= 90) vivid++
                val key = ((rr shr 4) shl 8) or ((gg shr 4) shl 4) or (bb shr 4)
                palette[key] = (palette[key] ?: 0) + 1
                i++
            }
        }
        val avgSaturation = (sumSat / n).toFloat()
        val avgBrightness = (sumLuma / n / 255.0).toFloat()
        val whiteRatio = white.toFloat() / n
        val vividRatio = vivid.toFloat() / n
        val paletteSize = palette.size

        var flatRatio = 0f
        if (w >= 3 && h >= 3) {
            val area = (w - 2) * (h - 2)
            var flat = 0
            for (y in 1 until h - 1) {
                for (x in 1 until w - 1) {
                    if (sat[y * w + x] < 0.2f) flat++
                }
            }
            flatRatio = flat.toFloat() / area
        }

        var edges = 0
        var axisLines = 0
        var count = 0
        for (y in 1 until h) {
            for (x in 1 until w) {
                val dH = kotlin.math.abs(luma[y * w + x] - luma[y * w + x - 1])
                val dV = kotlin.math.abs(luma[y * w + x] - luma[(y - 1) * w + x])
                val strong = dH > 18f || dV > 18f
                if (strong) edges++
                if (strong && (dH >= 3f * dV + 40f || dV >= 3f * dH + 40f)) axisLines++
                count++
            }
        }
        val edgeDensity = edges.toFloat() / count
        val textLineDensity = axisLines.toFloat() / count

        val documentLike =
            whiteRatio.coerceAtMost(0.75f) * 0.6f +
                flatRatio.coerceAtMost(0.6f) * 0.4f
        val screenshotLike =
            flatRatio.coerceAtMost(0.55f) * 0.6f +
                textLineDensity.coerceAtMost(0.35f) * 0.4f
        val photoLike =
            vividRatio.coerceAtMost(0.5f) * 0.4f +
                (1f - flatRatio).coerceIn(0f, 1f) * 0.3f +
                (1f - documentLike).coerceIn(0f, 1f) * 0.3f

        return ContentFeatures(
            isOpaque = true,
            width = w,
            height = h,
            flatRatio = flatRatio,
            paletteSize = paletteSize,
            avgSaturation = avgSaturation,
            avgBrightness = avgBrightness,
            whiteRatio = whiteRatio,
            vividRatio = vividRatio,
            edgeDensity = edgeDensity,
            textLineDensity = textLineDensity,
            documentLike = documentLike,
            screenshotLike = screenshotLike,
            photoLike = photoLike,
        )
    }
}
