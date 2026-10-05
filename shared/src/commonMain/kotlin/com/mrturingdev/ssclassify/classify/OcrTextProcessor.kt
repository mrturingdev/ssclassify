package com.mrturingdev.ssclassify.classify

/**
 * One OCR text line. The box is normalized to 0..1 of the image with the
 * origin at the top-left, so both engines (ML Kit pixels, Vision's
 * bottom-left normalized rects) map onto the same space.
 */
data class OcrLine(
    val text: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
    val height: Float get() = bottom - top
}

/**
 * Raw OCR output next to its cleaned, reading-order version, plus [prioritized]:
 * the filtered rows ordered middle of the screen first, widening outward, with
 * font-size importance marked as Markdown headings ("# " title-size, "## "
 * emphasized). That is what the meaning summarizer reads.
 */
data class OcrText(val raw: String, val filtered: String, val prioritized: String = filtered)

/**
 * Turns one full-image OCR pass into:
 *  - raw: every line, in the engine's order, untouched;
 *  - filtered: only lines in the central focus region (status bar, nav bar and
 *    edge chrome sit in the outer margin), arranged into reading-order rows so
 *    split columns rejoin ("Total" + "508.50" -> "Total 508.50"), with
 *    meaningless tokens (symbol and garbage fragments) and pure status-bar lines removed.
 *
 * One OCR call serves both, instead of OCR-ing a center crop and the whole image separately.
 */
object OcrTextProcessor {

    /** Margin on each side excluded from the focus region: 0.05 keeps the central 90%. Calibration knob. */
    const val FOCUS_MARGIN = 0.05f

    /** Row text height over the median row height at which a row reads as a title / emphasized. Calibration knobs. */
    const val TITLE_RATIO = 1.6f
    const val EMPHASIS_RATIO = 1.2f

    /**
     * Focus rings as distances from the vertical middle: the middle 60% of the
     * screen, then 80%, then the rest of the focus region. Vertical only, since
     * left-aligned content starts near the side edges. Calibration knobs.
     */
    val FOCUS_RINGS = listOf(0.3f, 0.4f)

    private class Row(val text: String, val size: Float, val centerY: Float)

    /** Clock, battery and signal tokens that status bars put into OCR. */
    internal val statusNoise = Regex(
        "^([0-2]?[0-9]:[0-5][0-9](\\s*[ap]m)?|\\d{1,3}%|lte\\+?|5g\\+?|4g|3g|wifi|volte|battery|am|pm)$",
        RegexOption.IGNORE_CASE,
    )

    fun process(lines: List<OcrLine>, focusMargin: Float = FOCUS_MARGIN): OcrText {
        val raw = lines.joinToString("\n") { it.text.trim() }.trim()
        val focused = lines.filter {
            it.centerX in focusMargin..(1f - focusMargin) && it.centerY in focusMargin..(1f - focusMargin)
        }
        // Line box height is the font size: the row's tallest line sets its size.
        val rows = readingOrderRows(focused)
            .map { row -> Row(cleanLine(row.joinToString(" ") { it.text }), row.maxOf { it.height }, row.first().centerY) }
            .filter { it.text.isNotEmpty() }
        val filtered = rows.joinToString("\n") { it.text }
        return OcrText(raw, filtered, prioritize(rows))
    }

    /**
     * Orders rows by focus ring, middle first (reading order within a ring), and
     * marks rows whose font is clearly larger than the screen's body text (the
     * median row height) as "# " or "## " headings. Nothing is dropped.
     */
    private fun prioritize(rows: List<Row>): String {
        if (rows.isEmpty()) return ""
        // Lower median: body text is the baseline, so headings never pull it up.
        val median = rows.map { it.size }.sorted()[(rows.size - 1) / 2]
        return rows.sortedBy(::ring).joinToString("\n") { row ->
            when {
                median <= 0f -> row.text
                row.size >= median * TITLE_RATIO -> "# ${row.text}"
                row.size >= median * EMPHASIS_RATIO -> "## ${row.text}"
                else -> row.text
            }
        }
    }

    /** 0 for the middle ring, widening outward; rows past every ring share the last index. */
    private fun ring(row: Row): Int {
        val offset = kotlin.math.abs(row.centerY - 0.5f)
        return FOCUS_RINGS.indexOfFirst { offset <= it }.takeIf { it >= 0 } ?: FOCUS_RINGS.size
    }

    /**
     * The tallest horizontal band of the focus region with no OCR text in it,
     * as (top, bottom) fractions of the image height, or null when every gap is
     * shorter than [minHeight]. On an image-first screenshot this is where the
     * photo sits, so an image classifier can look at it instead of the whole screen.
     */
    fun textFreeBand(
        lines: List<OcrLine>,
        focusMargin: Float = FOCUS_MARGIN,
        minHeight: Float = 0.2f,
    ): Pair<Float, Float>? {
        val top = focusMargin
        val bottom = 1f - focusMargin
        var best: Pair<Float, Float>? = null
        var cursor = top
        val occupied = lines
            .filter { it.bottom > top && it.top < bottom }
            .sortedBy { it.top }
        for (line in occupied + OcrLine("", 0f, bottom, 1f, bottom)) {
            val gapEnd = line.top.coerceAtMost(bottom)
            if (gapEnd - cursor > (best?.let { it.second - it.first } ?: 0f)) best = cursor to gapEnd
            cursor = maxOf(cursor, line.bottom)
        }
        return best?.takeIf { it.second - it.first >= minHeight }
    }

    /**
     * Text-only cleanup for OCR stored before line geometry was recorded:
     * no focus region or row rebuilding, just token filtering per line.
     */
    fun cleanText(text: String): String =
        text.lines().map(::cleanLine).filter { it.isNotEmpty() }.joinToString("\n")

    /**
     * Groups lines whose vertical centers are within half a line height of the
     * row's first line, top to bottom; each row is ordered left to right.
     */
    private fun readingOrderRows(lines: List<OcrLine>): List<List<OcrLine>> {
        val rows = mutableListOf<MutableList<OcrLine>>()
        for (line in lines.sortedBy { it.centerY }) {
            val row = rows.lastOrNull()
            val anchor = row?.first()
            val tolerance = maxOf(anchor?.height ?: 0f, line.height) / 2f
            if (anchor != null && kotlin.math.abs(line.centerY - anchor.centerY) <= tolerance) {
                row.add(line)
            } else {
                rows.add(mutableListOf(line))
            }
        }
        return rows.map { row -> row.sortedBy { it.left } }
    }

    /**
     * Drops meaningless tokens, then the whole line if only status-bar tokens
     * remain. Times and percentages inside real content ("Departure 10:30",
     * "20% off") are kept: only a line made purely of them is chrome.
     */
    private fun cleanLine(line: String): String {
        val tokens = line.split(' ', '\t').filter(::isMeaningful)
        return if (tokens.all { statusNoise.matches(it) }) "" else tokens.joinToString(" ")
    }

    private fun isMeaningful(token: String): Boolean {
        if (token.isBlank()) return false
        val wordChars = token.count { it.isLetterOrDigit() }
        if (wordChars == 0) return false // "|", "•", "—", "..." and icon glyphs read as symbols
        // Lone letters are almost always icon fragments; digits ("Gate 4") and the words a / I are kept.
        if (token.length == 1 && token[0].isLetter() && token.lowercase() !in setOf("a", "i")) return false
        // Garbage like "ﬁ@#%" or "~_~": mostly symbols with a stray letter.
        return wordChars * 2 >= token.length
    }
}
