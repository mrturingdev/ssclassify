package com.mrturingdev.ssclassify.aicore.parser

import com.mrturingdev.ssclassify.aicore.model.EntityType
import com.mrturingdev.ssclassify.aicore.model.ExtractedEntity
import com.mrturingdev.ssclassify.aicore.model.InferenceSource
import com.mrturingdev.ssclassify.aicore.model.MeaningCategory
import com.mrturingdev.ssclassify.aicore.model.OcrMeaningResult
import org.json.JSONArray
import org.json.JSONObject

/**
 * Parses Gemini Nano's response into a strongly typed [OcrMeaningResult].
 * Handles markdown backticks, leading/trailing explanations, and partial/broken JSON safely.
 */
object OcrMeaningParser {

    /**
     * Parses the LLM raw text output into [OcrMeaningResult].
     *
     * @param llmResponse The raw string response from AICore / Gemini Nano.
     * @param rawOcrFallback The original OCR text used as a fallback if fields are missing.
     */
    fun parse(llmResponse: String, rawOcrFallback: String = ""): OcrMeaningResult {
        val jsonString = extractJsonPayload(llmResponse)
        if (jsonString != null) {
            try {
                val json = JSONObject(jsonString)
                return parseJsonObject(json, rawOcrFallback)
            } catch (_: Exception) {
                // Fall through to heuristic string recovery
            }
        }

        // If JSON extraction failed or was malformed, recover fields from raw output
        return recoverFromPlainText(llmResponse, rawOcrFallback)
    }

    private fun parseJsonObject(json: JSONObject, rawOcrFallback: String): OcrMeaningResult {
        val headline = json.optString("headline").takeIf { it.isNotBlank() }
            ?: extractFirstHeading(rawOcrFallback)

        val message = json.optString("message").takeIf { it.isNotBlank() }
            ?: headline

        val categoryRaw = json.optString("category")
        val category = MeaningCategory.fromString(categoryRaw)

        val secondaryCategory = json.optString("secondaryCategory").takeIf { it.isNotBlank() }

        val confidence = json.optDouble("confidence", 0.90).toFloat().coerceIn(0.0f, 1.0f)
        val isSensitive = json.optBoolean("isSensitive", false)

        val cleanText = json.optString("cleanText").takeIf { it.isNotBlank() }
            ?: rawOcrFallback

        val entities = mutableListOf<ExtractedEntity>()
        val entitiesArray: JSONArray? = json.optJSONArray("entities")
        if (entitiesArray != null) {
            for (i in 0 until entitiesArray.length()) {
                val item = entitiesArray.optJSONObject(i) ?: continue
                val typeStr = item.optString("type")
                val label = item.optString("label")
                val value = item.optString("value")
                if (value.isNotBlank()) {
                    entities += ExtractedEntity(
                        type = EntityType.fromString(typeStr),
                        label = label.ifBlank { "Detail" },
                        value = value.trim(),
                    )
                }
            }
        }

        val actionItems = mutableListOf<String>()
        val actionsArray: JSONArray? = json.optJSONArray("actionItems")
        if (actionsArray != null) {
            for (i in 0 until actionsArray.length()) {
                val action = actionsArray.optString(i)
                if (action.isNotBlank()) {
                    actionItems += action.trim()
                }
            }
        }

        return OcrMeaningResult(
            headline = headline,
            message = message,
            category = category,
            secondaryCategory = secondaryCategory,
            entities = entities,
            actionItems = actionItems,
            cleanText = cleanText,
            confidence = confidence,
            isSensitive = isSensitive,
            source = InferenceSource.AICORE_GEMINI_NANO,
        )
    }

    /**
     * Extracts JSON block from markdown code fences or curly braces.
     */
    internal fun extractJsonPayload(text: String): String? {
        val trimmed = text.trim()
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            return trimmed
        }

        // Look for markdown fence ```json ... ```
        val fenceMatch = Regex("""```(?:json)?\s*(\{[\s\S]*?\})\s*```""").find(trimmed)
        if (fenceMatch != null) {
            return fenceMatch.groupValues[1]
        }

        // Look for outer curly braces
        val startIdx = trimmed.indexOf('{')
        val lastIdx = trimmed.lastIndexOf('}')
        if (startIdx != -1 && lastIdx > startIdx) {
            return trimmed.substring(startIdx, lastIdx + 1)
        }

        return null
    }

    /**
     * Recovers structured data if LLM returned plain text instead of valid JSON.
     */
    private fun recoverFromPlainText(text: String, rawOcrFallback: String): OcrMeaningResult {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        val headline = lines.firstOrNull() ?: extractFirstHeading(rawOcrFallback)
        val message = lines.drop(1).take(2).joinToString(" ").ifBlank { headline }

        return OcrMeaningResult(
            headline = headline,
            message = message,
            category = MeaningCategory.UNCATEGORIZED,
            entities = emptyList(),
            actionItems = emptyList(),
            cleanText = rawOcrFallback.ifBlank { text },
            confidence = 0.5f,
            isSensitive = false,
            source = InferenceSource.AICORE_GEMINI_NANO,
        )
    }

    private fun extractFirstHeading(text: String): String {
        return text.lines()
            .map { it.trim() }
            .firstOrNull { it.length in 3..60 }
            ?: "Extracted OCR Content"
    }
}
