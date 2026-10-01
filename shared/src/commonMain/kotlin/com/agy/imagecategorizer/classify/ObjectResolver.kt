package com.agy.imagecategorizer.classify

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
        "boarding pass", "invoice", "receipt", "ticket", "menu",
        "balance", "payment", "order", "retweet", "reply",
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
