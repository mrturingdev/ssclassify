package com.mrturingdev.ssclassify.classify

import com.mrturingdev.ssclassify.model.ImageCategory

/**
 * On-device, rule-based screenshot categorizer. Pure and deterministic so it
 * can be unit-tested without a device.
 *
 * [isScreenshot] decides which gallery images are screenshots at all (from
 * file name / folder), and [categorize] assigns a content category from the
 * OCR text plus the file name (e.g. Samsung's `Screenshot_..._WhatsApp.jpg`).
 */
object ScreenshotCategorizer {

    private val screenshotTokens = listOf(
        "screenshot", "screen shot", "screencapture", "screen capture",
        "screen-capture", "captura", "bildschirmfoto", "capture d",
    )

    // ponytail: keyword hit counts, swap in an on-device text model if accuracy plateaus
    private val keywords: Map<ImageCategory, List<String>> = mapOf(
        ImageCategory.Receipts to listOf(
            "receipt", "invoice", "subtotal", "sub total", "grand total", "total", "tax", "vat",
            "amount due", "amount paid", "paid", "bill", "transaction id", "order id", "txn",
            "statement", "payment", "bank transfer", "credit card", "debit",
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
        ),
        ImageCategory.Travels to listOf(
            "flight", "boarding", "boarding pass", "gate", "seat", "departure", "arrival",
            "airline", "airport", "hotel", "booking", "check-in", "check in", "reservation",
            "itinerary", "pnr", "train", "bus", "uber", "pathao", "indrive", "directions",
            "visa", "ticket", "trip", "travel", "destination", "passport", "terminal",
        ),
        ImageCategory.Foods to listOf(
            "menu", "restaurant", "recipe", "ingredients", "foodmandu", "pizza", "burger",
            "coffee", "momo", "breakfast", "lunch", "dinner", "cafe", "dine", "food",
            "dish", "cuisine", "snack", "drink", "beverage", "bakery", "cooking", "dessert",
            "meal",
        ),
        ImageCategory.Health to listOf(
            "doctor", "hospital", "clinic", "prescription", "medicine", "dose", "mg",
            "heart rate", "calories", "workout", "steps", "blood", "vaccine", "pharmacy",
            "symptoms", "sleep", "fitness", "gym", "medical", "patient", "pulse", "bpm",
            "health", "exercise",
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

    fun isScreenshot(name: String, relativePath: String, folder: String): Boolean {
        val haystack = "$name $relativePath $folder".lowercase()
        return screenshotTokens.any { haystack.contains(it) }
    }

    /** Picks the category with the most keyword hits; ties go to declaration order. */
    fun categorize(text: String, name: String = "", subCategory: String? = null): ImageCategory {
        if (subCategory?.contains("qr", ignoreCase = true) == true) {
            return ImageCategory.QR
        }
        val haystack = "$name $text".lowercase().replace('_', ' ')
        var best = ImageCategory.Uncategorized
        var bestHits = 0
        for ((category, pattern) in patterns) {
            val hits = pattern.findAll(haystack).count()
            if (hits > bestHits) {
                best = category
                bestHits = hits
            }
        }
        return best
    }
}
