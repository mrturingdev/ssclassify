package com.agy.imagecategorizer.aicore.model

/**
 * Types of structured entities that can be parsed out from OCR text.
 */
enum class EntityType {
    /** Monetary amounts, prices, totals, balances (e.g. "$45.20", "€12.50", "Rs. 1,500"). */
    AMOUNT,

    /** Dates, timestamps, flight departure times, due dates (e.g. "Oct 14, 2026", "14:30"). */
    DATE_TIME,

    /** Merchants, vendors, companies, senders, or platforms (e.g. "Starbucks", "Delta Air", "John Doe"). */
    ORGANIZATION_SENDER,

    /** Reference numbers, order IDs, PNRs, tracking numbers, flight numbers (e.g. "AA-4921", "ORD-98421"). */
    IDENTIFIER,

    /** Physical addresses, airport codes, terminals, venues (e.g. "Gate B12", "JFK Airport"). */
    LOCATION,

    /** Contact details such as emails, phone numbers, website links. */
    CONTACT_INFO,

    /** Explicit action items, reminders, follow-ups, or verification steps. */
    ACTION_ITEM,

    /** General key-value attributes. */
    OTHER;

    companion object {
        fun fromString(value: String?): EntityType {
            if (value.isNullOrBlank()) return OTHER
            val normalized = value.trim().uppercase()
            return entries.firstOrNull { it.name == normalized }
                ?: when {
                    "AMOUNT" in normalized || "PRICE" in normalized || "TOTAL" in normalized || "MONEY" in normalized -> AMOUNT
                    "DATE" in normalized || "TIME" in normalized -> DATE_TIME
                    "ORG" in normalized || "MERCHANT" in normalized || "SENDER" in normalized || "VENDOR" in normalized -> ORGANIZATION_SENDER
                    "ID" in normalized || "REF" in normalized || "ORDER" in normalized || "PNR" in normalized || "CODE" in normalized -> IDENTIFIER
                    "LOC" in normalized || "ADDRESS" in normalized || "GATE" in normalized -> LOCATION
                    "CONTACT" in normalized || "EMAIL" in normalized || "PHONE" in normalized || "URL" in normalized -> CONTACT_INFO
                    "ACTION" in normalized || "TASK" in normalized -> ACTION_ITEM
                    else -> OTHER
                }
        }
    }
}

/**
 * A concrete extracted key-value entity from the text.
 */
data class ExtractedEntity(
    val type: EntityType,
    val label: String,
    val value: String,
)
