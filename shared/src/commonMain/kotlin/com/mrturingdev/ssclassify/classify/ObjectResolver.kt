package com.mrturingdev.ssclassify.classify

import com.mrturingdev.ssclassify.model.ImageCategory

/** Which engine named a screenshot's main object. */
enum class ObjectSource { Ocr, TensorFlowLite }

data class DetectedObject(val label: String, val source: ObjectSource)

/**
 * Decides whether a screenshot's main object comes from its OCR text or from
 * the TensorFlow Lite image classifier.
 *
 * Text wins: a keyword in the filtered OCR text ("Boarding Pass", "Invoice")
 * names the object precisely. MobileNet (ImageNet classes) is only asked when
 * the screenshot carries little text, i.e. it is mostly a photo, and only a
 * confident label that is not an artifact of screenshots themselves counts.
 * On text screenshots MobileNet reliably answers "envelope" or "web site",
 * which describes the screen, not its content.
 */
object ObjectResolver {

    /** Below this many filtered words a screenshot is image-first and worth classifying. */
    const val TEXT_LIGHT_WORDS = 8

    /**
     * Calibration knob for MobileNet v1 quant top-1 scores. Measured on the
     * emulator with photo-region crops: a waterfall scored valley/cliff 0.49
     * (right), beach grass scored "ear" 0.57 (wrong). A wrong object is worse
     * than none, so the bar sits above both.
     */
    const val MIN_IMAGE_CONFIDENCE = 0.6f

    // Most specific first, so "boarding pass" beats "ticket".
    private val textObjects = listOf(
        "boarding pass", "invoice", "prescription", "receipt", "ticket", "menu",
        "recipe", "balance", "payment", "order", "tutorial", "course",
        "workout", "lab report", "retweet", "reply",
    )
    private val textObjectPattern = Regex(
        textObjects.joinToString("|", prefix = "(?<![a-z0-9])(", postfix = ")(?![a-z0-9])") { Regex.escape(it) },
    )
    private val explicitCategory = Regex("category:\\s*([a-zA-Z]+)", RegexOption.IGNORE_CASE)

    /** ImageNet classes MobileNet gives screens themselves (UI chrome, white pages, text blocks). */
    private val screenArtifacts = setOf(
        "web site", "envelope", "menu", "crossword puzzle", "comic book", "book jacket",
        "monitor", "screen", "television", "desktop computer", "laptop", "notebook",
        "hand-held computer", "cellular telephone", "ipod", "digital clock", "digital watch",
        "scoreboard", "rule", "slide rule", "packet", "carton", "binder", "street sign",
        "oscilloscope", "remote control", "paper towel", "toilet tissue",
    )

    private val foodReceiptPattern = Regex("""(?i)\b(?:restaurant|cafe|coffee|burger|pizza|momo|bakery|foodmandu|dine|dining|breakfast|lunch|dinner|meal|beverage|drinks?|bar|kitchen)\b""")
    private val travelReceiptPattern = Regex("""(?i)\b(?:flight|airline|airport|hotel|booking|stay|uber|pathao|indrive|cab|taxi|train|bus|ticket|airfare|travel)\b""")
    private val healthReceiptPattern = Regex("""(?i)\b(?:pharmacy|clinic|hospital|medicine|medical|doctor|drug|dose|prescription|lab)\b""")
    private val groceryReceiptPattern = Regex("""(?i)\b(?:supermarket|mart|grocery|groceries|bhatbhateni|store|provisions|retail)\b""")
    private val utilityReceiptPattern = Regex("""(?i)\b(?:electricity|water|internet|wifi|recharge|telco|utility|bill\s*payment|ntc|ncell|telecom)\b""")

    private val codePattern = Regex("""(?i)(?:\b(?:fun\s+[a-z]|class\s+[a-z]|def\s+[a-z]|public\s+class|return\b|import\s+[a-z]|val\s+[a-z]|var\s+[a-z]|git\b|github|algorithm|stack\s*trace|exception\s+in)\b|\{|\})""")
    private val quizPattern = Regex("""(?i)\b(?:quiz|exam|test\s*question|assessment|multiple\s*choice)\b""")
    private val tutorialPattern = Regex("""(?i)\b(?:tutorial|how\s*to|step\s*\d|walkthrough|guide)\b""")
    private val coursePattern = Regex("""(?i)\b(?:course|lecture|syllabus|coursera|udemy|edx|duolingo|curriculum)\b""")
    private val notesPattern = Regex("""(?i)\b(?:chapter|notes|textbook|definition|formula|theorem|summary)\b""")

    private val recipePattern = Regex("""(?i)\b(?:recipe|ingredients?|tablespoon|teaspoon|tbsp|tsp|cook\b|bake\b|boil\b|prep\s*time)\b""")
    private val menuPattern = Regex("""(?i)\b(?:menu|appetizer|entree|dessert|beverages?|combos?|starters?)\b""")
    private val diningPattern = Regex("""(?i)\b(?:cafe|restaurant|dine|dining|foodmandu|bakery|kitchen)\b""")

    private val prescriptionPattern = Regex("""(?i)\b(?:prescription|rx\b|dosage|dose|mg\b|tablet|capsule|syrup|take\s*\d)\b""")
    private val labPattern = Regex("""(?i)\b(?:lab|test\s*result|blood|pathology|urine|hemoglobin|reference\s*range)\b""")
    private val fitnessPattern = Regex("""(?i)\b(?:workout|fitness|gym|calories|steps|heart\s*rate|bpm|pulse|exercise)\b""")

    private val chatPattern = Regex("""(?i)\b(?:whatsapp|messenger|telegram|viber|signal|imessage|chat|typing|last\s*seen|online)\b""")
    private val socialPattern = Regex("""(?i)\b(?:instagram|facebook|twitter|tiktok|reddit|linkedin|threads|followers|likes|retweet|repost|reels?)\b""")
    private val meetingPattern = Regex("""(?i)\b(?:meeting|zoom|teams|agenda|standup|stand-up|calendar|jira|slack|sprint|confluence)\b""")
    private val documentPattern = Regex("""(?i)\b(?:document|agreement|contract|policy|terms|clause|pdf)\b""")

    /** The object named by the text, or null when the text does not name one. */
    fun fromText(filteredText: String): DetectedObject? {
        explicitCategory.find(filteredText)?.let {
            return DetectedObject(it.groupValues[1].titleCase(), ObjectSource.Ocr)
        }
        val keyword = textObjectPattern.findAll(filteredText.lowercase())
            .map { it.value }
            .minByOrNull { textObjects.indexOf(it) }
            ?: return null
        return DetectedObject(keyword.titleCase(), ObjectSource.Ocr)
    }

    /** Resolves the standard subcategory under [category] from OCR text. */
    fun resolveSubCategory(text: String, category: ImageCategory): String? {
        val lower = text.lowercase()
        return when (category) {
            ImageCategory.Receipts -> when {
                foodReceiptPattern.containsMatchIn(lower) -> "Food"
                travelReceiptPattern.containsMatchIn(lower) -> "Travel"
                healthReceiptPattern.containsMatchIn(lower) -> "Health"
                groceryReceiptPattern.containsMatchIn(lower) -> "Grocery"
                utilityReceiptPattern.containsMatchIn(lower) -> "Utility"
                lower.contains("invoice") -> "Invoice"
                else -> "Receipt"
            }
            ImageCategory.QR -> when {
                lower.contains("wifi") || lower.contains("ssid") -> "Wi-Fi"
                lower.contains("upi") || lower.contains("pa=") || lower.contains("fonepay") || lower.contains("pay") -> "Payment"
                lower.contains("http") || lower.contains(".com") || lower.contains(".org") || lower.contains("www.") -> "Website"
                lower.contains("contact") || lower.contains("email") || lower.contains("phone") -> "Contact"
                else -> "QR Code"
            }
            ImageCategory.Learning -> when {
                codePattern.containsMatchIn(lower) -> "Code"
                quizPattern.containsMatchIn(lower) -> "Quiz"
                tutorialPattern.containsMatchIn(lower) -> "Tutorial"
                coursePattern.containsMatchIn(lower) -> "Course"
                notesPattern.containsMatchIn(lower) -> "Notes"
                else -> "Study"
            }
            ImageCategory.Travels -> when {
                lower.contains("boarding pass") || (lower.contains("gate") && lower.contains("seat")) -> "Boarding Pass"
                lower.contains("flight") || lower.contains("airline") || lower.contains("airport") -> "Flight"
                lower.contains("hotel") || lower.contains("reservation") || lower.contains("check-in") || lower.contains("check in") -> "Hotel Booking"
                lower.contains("direction") || lower.contains("route") || lower.contains("map") -> "Directions"
                lower.contains("ticket") || lower.contains("pnr") || lower.contains("train") || lower.contains("bus") -> "Ticket"
                else -> "Travel"
            }
            ImageCategory.Foods -> when {
                recipePattern.containsMatchIn(lower) -> "Recipe"
                menuPattern.containsMatchIn(lower) -> "Menu"
                diningPattern.containsMatchIn(lower) -> "Dining"
                else -> "Food"
            }
            ImageCategory.Health -> when {
                prescriptionPattern.containsMatchIn(lower) -> "Prescription"
                labPattern.containsMatchIn(lower) -> "Lab Result"
                fitnessPattern.containsMatchIn(lower) -> "Fitness"
                else -> "Medical"
            }
            ImageCategory.Others -> when {
                chatPattern.containsMatchIn(lower) -> "Chat"
                socialPattern.containsMatchIn(lower) -> "Social"
                meetingPattern.containsMatchIn(lower) -> "Meeting"
                documentPattern.containsMatchIn(lower) -> "Document"
                else -> null
            }
            ImageCategory.Uncategorized -> null
        }
    }

    /** Whether to spend an image-classifier run: only when the text is too thin to say anything. */
    fun needsImageModel(filteredText: String): Boolean =
        filteredText.split(' ', '\n').count { it.isNotBlank() } < TEXT_LIGHT_WORDS

    /** The best image label that is confident and not a screenshot artifact. */
    fun fromImageLabels(labels: List<ClassificationResult>): DetectedObject? =
        labels.filter { it.confidence >= MIN_IMAGE_CONFIDENCE }
            .map { it.label.substringBefore(',').trim().lowercase() to it.confidence }
            .filter { (label, _) -> label.isNotEmpty() && label !in screenArtifacts }
            .maxByOrNull { it.second }
            ?.let { (label, _) -> DetectedObject(label.titleCase(), ObjectSource.TensorFlowLite) }

    private fun String.titleCase(): String =
        split(' ').joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
}
