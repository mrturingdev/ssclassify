package com.mrturingdev.ssclassify.aicore.fallback

import com.mrturingdev.ssclassify.aicore.model.EntityType
import com.mrturingdev.ssclassify.aicore.model.ExtractedEntity
import com.mrturingdev.ssclassify.aicore.model.InferenceSource
import com.mrturingdev.ssclassify.aicore.model.MeaningCategory
import com.mrturingdev.ssclassify.aicore.model.OcrMeaningResult
import com.mrturingdev.ssclassify.aicore.prompt.OcrPromptBuilder

/**
 * Intelligent rule-based extractor that parses meaning, category, headline, and structured
 * entities from OCR text when AICore / Gemini Nano is unavailable, downloading, or unsupported.
 */
object RuleBasedOcrMeaningExtractor {

    private val AMOUNT_REGEX = Regex(
        """(?i)(?:total|grand\s*total|subtotal|amount|due|paid|price|balance|rs\.?|npr|\$|€|£|₹)\s*[:=-]?\s*([$€£₹]?\s*\d+(?:[.,]\d{1,2})?)"""
    )
    private val DATE_REGEX = Regex(
        """(?i)(?:date|dated)?\s*[:=-]?\s*(\b\d{1,4}[/-]\d{1,2}[/-]\d{1,4}\b|\b(?:jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\s+\d{1,2},?\s+\d{2,4}\b)"""
    )
    private val TIME_REGEX = Regex(
        """\b([0-1]?[0-9]|2[0-3]):[0-5][0-9](?::[0-5][0-9])?(?:\s*[ap]m)?\b""",
        RegexOption.IGNORE_CASE
    )
    private val ID_REGEX = Regex(
        """(?i)(?:order|invoice|txn|transaction|ticket|pnr|id|ref|bill|flight|tracking)\s*(?:no\.?|id|#)?\s*[:=-]?\s*([a-z0-9\-_]{4,24})"""
    )
    private val URL_REGEX = Regex(
        """https?://[a-zA-Z0-9.\-_]+(?::\d+)?(?:/[^\s]*)?"""
    )
    private val EMAIL_REGEX = Regex(
        """[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}"""
    )
    private val SENSITIVE_REGEX = Regex(
        """(?i)\b(?:otp|one[- ]time[- ]password|verification code|cvv|cvc|pin|password)\b\s*[:=-]?\s*([0-9a-zA-Z]{4,8})"""
    )

    fun extract(rawOcrText: String): OcrMeaningResult {
        val cleanText = OcrPromptBuilder.sanitizeOcrText(rawOcrText)
        val lines = cleanText.lines().map { it.trim() }.filter { it.isNotBlank() }

        if (lines.isEmpty()) {
            return OcrMeaningResult(
                headline = "No Text Detected",
                message = "The image did not yield readable OCR text.",
                category = MeaningCategory.UNCATEGORIZED,
                cleanText = "",
                confidence = 0.0f,
                source = InferenceSource.RULE_BASED_FALLBACK,
            )
        }

        val category = detectCategory(cleanText)
        val entities = extractEntities(cleanText)
        val isSensitive = SENSITIVE_REGEX.containsMatchIn(rawOcrText)

        val headline = determineHeadline(lines, category, entities)
        val message = synthesizeMessage(headline, category, entities, lines)

        return OcrMeaningResult(
            headline = headline,
            message = message,
            category = category,
            secondaryCategory = determineSecondaryCategory(category, cleanText),
            entities = entities,
            actionItems = extractActionItems(lines),
            cleanText = cleanText,
            confidence = 0.75f,
            isSensitive = isSensitive,
            source = InferenceSource.RULE_BASED_FALLBACK,
        )
    }

    private fun detectCategory(text: String): MeaningCategory {
        val lower = text.lowercase()
        return when {
            listOf("total", "subtotal", "tax", "invoice", "receipt", "paid", "amount due", "balance", "visa", "mastercard", "cashier", "order #").any { it in lower } ->
                MeaningCategory.RECEIPT_EXPENSE

            listOf("flight", "boarding", "gate", "terminal", "seat", "departure", "arrival", "pnr", "booking reference", "hotel", "check-in").any { it in lower } ->
                MeaningCategory.TRAVEL_TICKET

            listOf("yesterday", "today at", "typing...", "delivered", "read", "sent from my", "replying to", "message...").any { it in lower } ||
                    (text.count { it == ':' } >= 3 && listOf("am", "pm").any { it in lower }) ->
                MeaningCategory.CONVERSATION_CHAT

            listOf("likes", "retweet", "repost", "share", "follow", "comment", "trending", "feed", "upvote", "subreddit").any { it in lower } ->
                MeaningCategory.SOCIAL_MEDIA

            listOf("exception", "error:", "stack trace", "fatal", "nullpointer", "syntaxerror", "def ", "fun ", "import ", "const ", "console.log", "git ").any { it in lower } ->
                MeaningCategory.TECHNICAL_CODE

            listOf("hereby", "agreement", "terms and conditions", "certificate", "applicant", "signature", "affidavit", "republic of").any { it in lower } ->
                MeaningCategory.DOCUMENT_FORM

            listOf("event", "calendar", "meeting", "zoom", "google meet", "scheduled for", "rsvp", "webinar").any { it in lower } ->
                MeaningCategory.EVENT_SCHEDULE

            listOf("todo", "to do", "buy:", "remember:", "notes:", "checklist").any { it in lower } ->
                MeaningCategory.MEMO_NOTE

            else -> MeaningCategory.ARTICLE_INFO
        }
    }

    private fun extractEntities(text: String): List<ExtractedEntity> {
        val entities = mutableListOf<ExtractedEntity>()

        AMOUNT_REGEX.findAll(text).map { match -> match.groupValues.getOrElse(1) { match.value } }.distinct().take(2).forEach {
            entities += ExtractedEntity(EntityType.AMOUNT, "Amount", it.trim())
        }

        DATE_REGEX.findAll(text).map { match -> match.groupValues.getOrElse(1) { match.value } }.distinct().take(1).forEach {
            entities += ExtractedEntity(EntityType.DATE_TIME, "Date", it.trim())
        }

        ID_REGEX.findAll(text).map { match -> match.groupValues.getOrElse(1) { match.value } }.distinct().take(1).forEach {
            entities += ExtractedEntity(EntityType.IDENTIFIER, "Reference", it.trim())
        }

        URL_REGEX.findAll(text).map { it.value }.distinct().take(1).forEach {
            entities += ExtractedEntity(EntityType.CONTACT_INFO, "Link", it.trim())
        }

        EMAIL_REGEX.findAll(text).map { it.value }.distinct().take(1).forEach {
            entities += ExtractedEntity(EntityType.CONTACT_INFO, "Email", it.trim())
        }

        return entities
    }

    private fun determineHeadline(
        lines: List<String>,
        category: MeaningCategory,
        entities: List<ExtractedEntity>,
    ): String {
        val amount = entities.firstOrNull { it.type == EntityType.AMOUNT }?.value
        val firstMeaningful = lines.firstOrNull { it.length in 4..50 && !AMOUNT_REGEX.containsMatchIn(it) }
            ?: lines.first()

        return when (category) {
            MeaningCategory.RECEIPT_EXPENSE -> {
                if (amount != null) "$firstMeaningful - $amount" else "$firstMeaningful (Receipt)"
            }
            MeaningCategory.TRAVEL_TICKET -> {
                val ref = entities.firstOrNull { it.type == EntityType.IDENTIFIER }?.value
                if (ref != null) "$firstMeaningful • $ref" else "$firstMeaningful Ticket"
            }
            MeaningCategory.TECHNICAL_CODE -> "Technical Log / Code: $firstMeaningful"
            else -> firstMeaningful
        }
    }

    private fun synthesizeMessage(
        headline: String,
        category: MeaningCategory,
        entities: List<ExtractedEntity>,
        lines: List<String>,
    ): String {
        val details = entities.joinToString(", ") { "${it.label}: ${it.value}" }
        return when {
            details.isNotBlank() -> "$headline. Details: $details."
            lines.size > 1 -> lines.take(3).joinToString(" • ")
            else -> headline
        }
    }

    private fun determineSecondaryCategory(category: MeaningCategory, text: String): String? {
        val lower = text.lowercase()
        return when (category) {
            MeaningCategory.RECEIPT_EXPENSE -> when {
                "coffee" in lower || "cafe" in lower || "starbucks" in lower -> "Coffee & Dining"
                "grocery" in lower || "market" in lower || "supermarket" in lower -> "Groceries"
                "gas" in lower || "fuel" in lower || "station" in lower -> "Fuel"
                else -> "Expense"
            }
            MeaningCategory.TRAVEL_TICKET -> when {
                "flight" in lower || "airline" in lower || "boarding" in lower -> "Flight"
                "train" in lower || "rail" in lower -> "Rail"
                "hotel" in lower || "resort" in lower -> "Lodging"
                else -> "Transit"
            }
            else -> null
        }
    }

    private fun extractActionItems(lines: List<String>): List<String> {
        val actionKeywords = listOf("pay", "due", "verify", "confirm", "click", "rsvp", "check in", "attend")
        return lines.filter { line ->
            val lower = line.lowercase()
            actionKeywords.any { kw -> lower.startsWith(kw) || "please $kw" in lower || "must $kw" in lower }
        }.take(2)
    }
}
