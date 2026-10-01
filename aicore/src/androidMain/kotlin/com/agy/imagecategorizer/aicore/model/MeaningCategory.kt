package com.agy.imagecategorizer.aicore.model

/**
 * High-level semantic category representing the contextual meaning of the text extracted from OCR.
 */
enum class MeaningCategory(val displayName: String) {
    /** Receipts, bills, invoices, order totals, bank transaction slips, payment gateways. */
    RECEIPT_EXPENSE("Receipt & Expense"),

    /** Boarding passes, flight confirmations, train/bus tickets, hotel reservations, rides. */
    TRAVEL_TICKET("Travel & Ticket"),

    /** Chats, messaging apps, SMS, DMs, conversations with sender/receiver dialogue. */
    CONVERSATION_CHAT("Conversation & Chat"),

    /** Social media feeds, tweets, Reddit threads, Instagram/LinkedIn posts or comments. */
    SOCIAL_MEDIA("Social Media"),

    /** Code snippets, logs, terminal sessions, stack traces, compile errors, API payloads. */
    TECHNICAL_CODE("Code & Technical"),

    /** Official letters, agreements, government IDs, forms, medical reports, resumes. */
    DOCUMENT_FORM("Document & Form"),

    /** Calendar invitations, meeting notifications, deadlines, webinar schedules. */
    EVENT_SCHEDULE("Event & Schedule"),

    /** Personal notes, shopping lists, to-do checklists, reminders, brainstorming memos. */
    MEMO_NOTE("Memo & Note"),

    /** Articles, news clips, recipes, educational guides, web summaries. */
    ARTICLE_INFO("Article & Information"),

    /** Uncategorized or ambiguous content. */
    UNCATEGORIZED("Uncategorized");

    companion object {
        fun fromString(value: String?): MeaningCategory {
            if (value.isNullOrBlank()) return UNCATEGORIZED
            val normalized = value.trim().uppercase()
            return entries.firstOrNull { it.name == normalized }
                ?: when {
                    "RECEIPT" in normalized || "EXPENSE" in normalized || "BILL" in normalized || "INVOICE" in normalized -> RECEIPT_EXPENSE
                    "TRAVEL" in normalized || "TICKET" in normalized || "FLIGHT" in normalized || "BOARDING" in normalized -> TRAVEL_TICKET
                    "CHAT" in normalized || "CONVERSATION" in normalized || "MESSAGE" in normalized || "SMS" in normalized -> CONVERSATION_CHAT
                    "SOCIAL" in normalized || "TWEET" in normalized || "POST" in normalized -> SOCIAL_MEDIA
                    "CODE" in normalized || "LOG" in normalized || "TECH" in normalized || "ERROR" in normalized || "STACK" in normalized -> TECHNICAL_CODE
                    "DOC" in normalized || "FORM" in normalized || "LETTER" in normalized || "CONTRACT" in normalized -> DOCUMENT_FORM
                    "EVENT" in normalized || "SCHEDULE" in normalized || "CALENDAR" in normalized || "MEETING" in normalized -> EVENT_SCHEDULE
                    "NOTE" in normalized || "TODO" in normalized || "LIST" in normalized || "MEMO" in normalized -> MEMO_NOTE
                    "ARTICLE" in normalized || "NEWS" in normalized || "INFO" in normalized -> ARTICLE_INFO
                    else -> UNCATEGORIZED
                }
        }
    }
}
