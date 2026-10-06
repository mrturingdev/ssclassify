package com.mrturingdev.ssclassify.classify

import com.mrturingdev.ssclassify.model.ImageCategory

/**
 * On-device, rule-based screenshot categorizer. Pure and deterministic so it
 * can be unit-tested without a device.
 *
 * Supports heading-weighted scoring (# = 3x, ## = 2x, body = 1x), filename weighting (2x),
 * and hierarchical receipts-first conflict resolution when financial transaction markers appear.
 */
object ScreenshotCategorizer {

    private val screenshotTokens = listOf(
        "screenshot", "screen shot", "screencapture", "screen capture",
        "screen-capture", "captura", "bildschirmfoto", "capture d",
    )

    private val keywords: Map<ImageCategory, List<String>> = mapOf(
        ImageCategory.Receipts to listOf(
            "receipt", "invoice", "subtotal", "sub total", "grand total", "total", "tax", "vat",
            "amount due", "amount paid", "paid", "bill", "transaction id", "order id", "txn",
            "statement", "payment", "bank transfer", "credit card", "debit", "bill to", "billed to",
            "cash receipt", "tax invoice", "sales receipt", "total amount", "payment receipt",
            "order total", "balance due", "total due",
        ),
        ImageCategory.QR to listOf(
            "qr code", "qr", "qrcode", "scan qr", "barcode", "scan code", "scan to pay", "scanner",
        ),
        ImageCategory.Learning to listOf(
            "tutorial", "lesson", "course", "class", "lecture", "assignment", "homework",
            "syllabus", "exam", "quiz", "test", "study", "flashcard", "textbook", "book",
            "chapter", "duolingo", "coursera", "udemy", "edx", "quizlet", "definition",
            "explanation", "formula", "theorem", "code", "python", "javascript", "kotlin",
            "java", "html", "css", "function", "algorithm", "git", "github", "stack trace",
            "public class", "exception in thread", "void", "return", "import", "npm",
            "documentation", "cheatsheet", "cheat sheet", "notes",
        ),
        ImageCategory.Travels to listOf(
            "flight", "boarding", "boarding pass", "gate", "seat", "departure", "arrival",
            "airline", "airport", "hotel", "booking", "check-in", "check in", "reservation",
            "itinerary", "pnr", "train", "bus", "uber", "pathao", "indrive", "directions",
            "visa", "ticket", "trip", "travel", "destination", "passport", "terminal",
            "airbnb", "hostel", "route", "navigation", "taxi", "cab",
        ),
        ImageCategory.Foods to listOf(
            "menu", "restaurant", "recipe", "ingredients", "foodmandu", "pizza", "burger",
            "coffee", "momo", "breakfast", "lunch", "dinner", "cafe", "dine", "food",
            "dish", "cuisine", "snack", "drink", "beverage", "bakery", "cooking", "dessert",
            "meal", "appetizer", "entree", "bhojdeals", "swiggy", "zomato",
        ),
        ImageCategory.Health to listOf(
            "doctor", "hospital", "clinic", "prescription", "medicine", "dose", "mg",
            "heart rate", "calories", "workout", "steps", "blood", "vaccine", "pharmacy",
            "symptoms", "sleep", "fitness", "gym", "medical", "patient", "pulse", "bpm",
            "health", "exercise", "diagnosis", "lab report", "test report", "blood pressure",
        ),
        ImageCategory.Others to listOf(
            "whatsapp", "messenger", "telegram", "viber", "signal", "imessage", "wechat",
            "typing", "online", "last seen", "delivered", "seen", "message", "messages",
            "reply", "sent", "instagram", "facebook", "twitter", "tiktok", "reddit",
            "linkedin", "threads", "youtube", "likes", "followers", "following", "retweet",
            "repost", "comments", "upvote", "subscribe", "story", "reels", "meeting",
            "agenda", "deadline", "slack", "jira", "zoom", "teams", "calendar", "sprint",
            "standup", "stand-up", "project", "invite", "schedule", "outlook", "confluence",
        ),
    )

    private val patterns: Map<ImageCategory, Regex> = keywords.mapValues { (_, words) ->
        Regex(words.joinToString("|", prefix = "(?<![a-z0-9])(?:", postfix = ")(?![a-z0-9])") { Regex.escape(it) })
    }

    private val strongReceiptPatterns = listOf(
        Regex("""(?i)\b(?:tax\s*invoice|cash\s*receipt|sales\s*receipt|payment\s*receipt|order\s*receipt|bill\s*to|billed\s*to)\b"""),
        Regex("""(?i)\b(?:amount\s*paid|amount\s*due|grand\s*total|subtotal|sub\s*total|total\s*amount|total\s*due|balance\s*due)\b"""),
        Regex("""(?i)\b(?:transaction\s*successful|payment\s*successful|order\s*total)\b"""),
        Regex("""(?i)\b(?:total|due|paid|balance)\s*[:=-]?\s*([$€£₹¥]|rs\.?|npr|usd|eur|inr)?\s*\d+(?:[.,]\d{1,2})"""),
    )

    fun hasStrongReceiptSignal(text: String): Boolean {
        val lower = text.lowercase()
        val matches = strongReceiptPatterns.count { it.containsMatchIn(lower) }
        val hasReceiptKeyword = lower.contains("total") ||
            lower.contains("invoice") ||
            lower.contains("receipt") ||
            lower.contains("paid") ||
            lower.contains("bill")
        return (matches >= 1 && hasReceiptKeyword) || matches >= 2
    }

    fun isScreenshot(name: String, relativePath: String, folder: String): Boolean {
        val haystack = "$name $relativePath $folder".lowercase()
        return screenshotTokens.any { haystack.contains(it) }
    }

    /**
     * Picks the category using heading-weighted scoring and receipts-first conflict resolution.
     * Heading multipliers: "# " = 3x, "## " = 2x, body = 1x. Filename = 2x.
     */
    fun categorize(
        text: String,
        name: String = "",
        subCategory: String? = null,
        prioritizedText: String = "",
    ): ImageCategory {
        if (subCategory?.contains("qr", ignoreCase = true) == true) {
            return ImageCategory.QR
        }

        val textToScore = prioritizedText.ifBlank { text }
        val lines = textToScore.lines().map { it.trim() }.filter { it.isNotBlank() }

        val scores = mutableMapOf<ImageCategory, Int>()
        for (cat in ImageCategory.entries) {
            scores[cat] = 0
        }

        // Filename score with 2x weight
        if (name.isNotBlank()) {
            val cleanName = name.lowercase().replace('_', ' ').replace('-', ' ')
            for ((category, pattern) in patterns) {
                val matches = pattern.findAll(cleanName).count()
                if (matches > 0) {
                    scores[category] = (scores[category] ?: 0) + (matches * 2)
                }
            }
        }

        // Line scores with heading multipliers
        for (rawLine in lines) {
            val weight = when {
                rawLine.startsWith("# ") -> 3
                rawLine.startsWith("## ") -> 2
                else -> 1
            }
            val cleanLine = rawLine.removePrefix("## ").removePrefix("# ").lowercase()
            for ((category, pattern) in patterns) {
                val matches = pattern.findAll(cleanLine).count()
                if (matches > 0) {
                    scores[category] = (scores[category] ?: 0) + (matches * weight)
                }
            }
        }

        val fullHaystack = "$name $text $prioritizedText".lowercase()
        val hasStrongReceipt = hasStrongReceiptSignal(fullHaystack)
        val receiptsScore = scores[ImageCategory.Receipts] ?: 0

        // Receipts-first conflict resolution:
        // Clear financial total/invoice/payment receipt takes precedence over domain categories.
        if (hasStrongReceipt && receiptsScore > 0) {
            return ImageCategory.Receipts
        }

        var best = ImageCategory.Uncategorized
        var bestScore = 0
        for (category in patterns.keys) {
            val s = scores[category] ?: 0
            if (s > bestScore) {
                best = category
                bestScore = s
            }
        }
        return best
    }
}
