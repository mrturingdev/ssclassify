package com.mrturingdev.ssclassify.classify

enum class BlankScreenType {
    None,
    BlackScreen,
    WhiteScreen,
}

/**
 * Pure, deterministic detector for screenshots that are just a black screen
 * or plain white screen with nothing on it.
 */
object BlankScreenDetector {

    const val LABEL_BLANK_SCREEN = "Blank Screen"

    fun isBlank(type: BlankScreenType): Boolean = type != BlankScreenType.None

    fun detect(source: ImageContentAnalyzer.PixelSource, rawText: String = ""): BlankScreenType {
        // If there is meaningful OCR text, it is not an empty/blank screen
        if (rawText.trim().isNotEmpty()) return BlankScreenType.None

        val w = source.width
        val h = source.height
        if (w <= 0 || h <= 0) return BlankScreenType.None

        // Sample up to 64x64 points across the image
        val stepX = maxOf(1, w / 64)
        val stepY = maxOf(1, h / 64)

        var totalSamples = 0
        var darkPixels = 0
        var whitePixels = 0
        var sumLuma = 0.0

        for (y in 0 until h step stepY) {
            for (x in 0 until w step stepX) {
                val argb = source.argb(x, y)
                val r = (argb shr 16) and 0xFF
                val g = (argb shr 8) and 0xFF
                val b = argb and 0xFF

                // Relative luminance formula (Rec. 601)
                val luma = 0.299 * r + 0.587 * g + 0.114 * b
                sumLuma += luma
                totalSamples++

                // Black screen check: nearly black (RGB <= 25)
                if (r <= 25 && g <= 25 && b <= 25) {
                    darkPixels++
                }
                // Plain white check: nearly white (RGB >= 235)
                if (r >= 235 && g >= 235 && b >= 235) {
                    whitePixels++
                }
            }
        }

        if (totalSamples == 0) return BlankScreenType.None

        val darkRatio = darkPixels.toDouble() / totalSamples
        val whiteRatio = whitePixels.toDouble() / totalSamples
        val avgLuma = sumLuma / totalSamples

        // At least 98% of sampled pixels must be dark, and average luminance < 20
        if (darkRatio >= 0.98 && avgLuma < 20.0) {
            return BlankScreenType.BlackScreen
        }

        // At least 98% of sampled pixels must be white, and average luminance > 235
        if (whiteRatio >= 0.98 && avgLuma > 235.0) {
            return BlankScreenType.WhiteScreen
        }

        return BlankScreenType.None
    }
}
