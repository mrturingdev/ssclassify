package com.mrturingdev.ssclassify.classify

/**
 * iOS implementation of [OcrMeaningProvider].
 * Provides heuristic semantic summarization and highlights extraction on iOS platforms.
 */
actual class OcrMeaningProvider actual constructor() {

    actual suspend fun extractMeaning(
        rawOcrText: String,
        filteredText: String,
        prioritizedText: String,
    ): ExtractedOcrMeaning {
        val input = filteredText.ifBlank { rawOcrText }
        if (input.isBlank()) {
            return ExtractedOcrMeaning(
                headline = "No text detected",
                message = "No readable text found in image.",
            )
        }

        // Font-size-ranked text leads the digest; highlights read the unmarked text.
        val digest = ScreenshotContentSummarizer.summarizeDigest(prioritizedText.ifBlank { input })
        val highlights = ScreenshotContentSummarizer.extractHighlights(input)

        return ExtractedOcrMeaning(
            headline = digest,
            message = digest,
            highlights = highlights,
        )
    }
}
