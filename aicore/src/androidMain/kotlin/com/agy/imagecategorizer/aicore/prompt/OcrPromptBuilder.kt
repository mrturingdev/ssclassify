package com.agy.imagecategorizer.aicore.prompt

import com.agy.imagecategorizer.aicore.model.MeaningCategory

/**
 * Builds optimized prompts for Android AICore / Gemini Nano to extract semantic meaning,
 * summary message, category, and structured entities from raw OCR text.
 */
object OcrPromptBuilder {

    /** Maximum character limit for OCR text input to stay well within Gemini Nano's context window. */
    const val DEFAULT_MAX_CHARS = 3000

    /** Status bar and radio noise tokens frequently injected into raw OCR. */
    private val STATUS_NOISE_REGEX = Regex(
        "(?i)^([0-2]?[0-9]:[0-5][0-9](\\s*[ap]m)?|\\d{1,3}%|lte\\+?|5g\\+?|4g|3g|wifi|volte|battery|am|pm)$"
    )

    /**
     * Constructs a prompt instructing Gemini Nano to interpret the OCR text and return
     * a clean, structured JSON object.
     */
    fun buildMeaningPrompt(rawOcrText: String, maxChars: Int = DEFAULT_MAX_CHARS): String {
        val sanitizedText = sanitizeOcrText(rawOcrText, maxChars)

        return """
You are an on-device AI reading OCR text extracted from a mobile screenshot or image.
Your task is to understand what this image represents, extract its actual message and meaning, remove OCR noise (status bar, battery, icons, glitches), and output a valid JSON object.

RAW OCR TEXT:
---
$sanitizedText
---

INSTRUCTIONS:
1. "headline": A crisp 3-8 word title describing what this document/screen is (e.g. "Whole Foods Grocery Receipt", "Delta Flight 1284 Boarding Pass", "WhatsApp Conversation with Alex").
2. "message": A 1-2 sentence coherent summary of the main message or meaning (e.g. "Total purchase of $34.50 at Whole Foods paid on Oct 12 with MasterCard." or "Flight departing JFK at 14:15 Gate B2, boarding at 13:35.").
3. "category": Choose exactly ONE: ${MeaningCategory.entries.joinToString(", ") { it.name }}.
4. "secondaryCategory": Specific sub-type (e.g. "Grocery", "Airline", "Code Error", "Social Post").
5. "confidence": A float between 0.0 and 1.0.
6. "entities": Array of key extracted objects:
   - "type": Choose one of ["AMOUNT", "DATE_TIME", "ORGANIZATION_SENDER", "IDENTIFIER", "LOCATION", "CONTACT_INFO", "ACTION_ITEM", "OTHER"].
   - "label": Short name (e.g. "Total Amount", "Flight Date", "Merchant", "Order #", "Departure Gate").
   - "value": The extracted value.
7. "actionItems": String array of deadlines, actions, or tasks (e.g. ["Pay before Oct 20"]).
8. "isSensitive": true if OTP, card password, or private credentials are in text, else false.
9. "cleanText": The actual text reconstructed into natural, readable paragraphs with OCR noise and status bar icons removed.

OUTPUT ONLY THE JSON OBJECT, NO EXTRA COMMENTARY:
```json
{
  "headline": "...",
  "message": "...",
  "category": "...",
  "secondaryCategory": "...",
  "confidence": 0.95,
  "entities": [
    {"type": "...", "label": "...", "value": "..."}
  ],
  "actionItems": [],
  "isSensitive": false,
  "cleanText": "..."
}
```
""".trimIndent()
    }

    /**
     * Cleans up obvious noise (status bar, excessive blank lines) and truncates length.
     */
    fun sanitizeOcrText(rawText: String, maxChars: Int = DEFAULT_MAX_CHARS): String {
        if (rawText.isBlank()) return ""

        val cleanedLines = rawText.lines()
            .map { it.trim() }
            .filter { line ->
                line.isNotEmpty() && !isNoiseLine(line)
            }

        val joined = cleanedLines.joinToString("\n")
        return if (joined.length > maxChars) {
            joined.take(maxChars).trimEnd() + "\n[...truncated]"
        } else {
            joined
        }
    }

    private fun isNoiseLine(line: String): Boolean {
        if (line.length <= 1) return true
        if (STATUS_NOISE_REGEX.matches(line)) return true
        val tokens = line.split(Regex("\\s+")).filter { it.isNotBlank() }
        return tokens.isNotEmpty() && tokens.all { STATUS_NOISE_REGEX.matches(it) }
    }
}
