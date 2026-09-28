package com.agy.imagecategorizer.classify

import com.agy.imagecategorizer.model.ImageCategory

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
        ),
        ImageCategory.Finance to listOf(
            "bank", "balance", "account", "transfer", "deposit", "withdraw", "credit card",
            "debit", "statement", "esewa", "khalti", "upi", "paypal", "loan", "interest",
            "stock", "stocks", "portfolio", "crypto", "bitcoin", "npr", "usd",
        ),
        ImageCategory.Shopping to listOf(
            "cart", "add to cart", "buy now", "checkout", "price", "sale", "discount", "coupon",
            "shipping", "free delivery", "amazon", "daraz", "ebay", "aliexpress", "wishlist",
            "in stock", "out of stock",
        ),
        ImageCategory.Travel to listOf(
            "flight", "boarding", "boarding pass", "gate", "seat", "departure", "arrival",
            "airline", "airport", "hotel", "booking", "check-in", "check in", "reservation",
            "itinerary", "pnr", "train", "bus", "uber", "pathao", "indrive", "directions", "visa",
        ),
        ImageCategory.Food to listOf(
            "menu", "restaurant", "recipe", "ingredients", "foodmandu", "pizza", "burger",
            "coffee", "momo", "breakfast", "lunch", "dinner", "cafe", "dine",
        ),
        ImageCategory.Health to listOf(
            "doctor", "hospital", "clinic", "prescription", "medicine", "dose", "mg",
            "heart rate", "calories", "workout", "steps", "blood", "vaccine", "pharmacy",
            "symptoms", "sleep",
        ),
        ImageCategory.Work to listOf(
            "meeting", "agenda", "deadline", "slack", "jira", "zoom", "teams", "calendar",
            "sprint", "standup", "stand-up", "project", "invite", "schedule", "outlook",
            "confluence", "manager", "task",
        ),
        ImageCategory.Code to listOf(
            "function", "class", "return", "import", "def", "const", "val", "var", "public",
            "void", "exception", "stack trace", "traceback", "npm", "git", "console",
            "null", "undefined", "localhost", "select", "kotlin", "python", "java",
            "gradle", "compile", "error:",
        ),
        ImageCategory.Chat to listOf(
            "whatsapp", "messenger", "telegram", "viber", "signal", "imessage", "wechat",
            "typing", "online", "last seen", "delivered", "seen", "message", "messages",
            "reply", "sent",
        ),
        ImageCategory.Social to listOf(
            "instagram", "facebook", "twitter", "tiktok", "reddit", "linkedin", "threads",
            "youtube", "likes", "followers", "following", "retweet", "repost", "comments",
            "upvote", "subscribe", "story", "reels",
        ),
        ImageCategory.Documents to listOf(
            "pdf", "page", "document", "section", "chapter", "article", "terms",
            "policy", "form", "certificate", "signature", "notice", "application",
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
    fun categorize(text: String, name: String = ""): ImageCategory {
        val haystack = "$name $text".lowercase().replace('_', ' ')
        var best = ImageCategory.Other
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
