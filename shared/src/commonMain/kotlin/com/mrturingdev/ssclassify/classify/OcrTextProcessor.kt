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
 * Four views of one OCR pass, each for a different reader:
 *  - [raw]: every line, in the engine's order, untouched;
 *  - [filtered]: the detected text, focus region only, cleaned, in reading order;
 *  - [summary]: for finding the title. Rows ordered middle of the screen first,
 *    widening outward, with headings marked as "# " (title) and "## "
 *    (emphasized) by font size and ALL CAPS;
 *  - [detail]: for reading. The filtered rows, but when the middle of the screen
 *    has a solid title the outer rows are secondary and move to the bottom;
 *  - [title]: the one row that best names the screen, or null when no row
 *    stands out (see [OcrTextProcessor.pickTitle]).
 */
data class OcrText(
    val raw: String,
    val filtered: String,
    val summary: String = filtered,
    val detail: String = filtered,
    val title: String? = null,
)

/**
 * Turns one full-image OCR pass into [OcrText]. The filtered text keeps only
 * lines in the central focus region (status bar, nav bar and edge chrome sit in
 * the outer margin), arranged into reading-order rows so split columns rejoin
 * ("Total" + "508.50" -> "Total 508.50"), with meaningless tokens (symbol and
 * garbage fragments) and pure status-bar lines removed. Summary and detail
 * reorder and mark those same rows; nothing is dropped.
 *
 * One OCR call serves all views, instead of OCR-ing a center crop and the whole image separately.
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

    /** Fewest letters for an ALL CAPS row to count as a heading, so "OK" or "PM" don't. Calibration knob. */
    const val MIN_CAPS_LETTERS = 3

    /** A solid title says something: at least this many words or characters, so big "DONE" or "PAY" buttons don't count. Calibration knobs. */
    const val MIN_TITLE_WORDS = 2
    const val MIN_TITLE_CHARS = 8

    /** One reading-order row: [level] 1 = title, 2 = emphasized, 3 = body; [ring] 0 = middle of the screen. */
    private class Row(val text: String, val size: Float, val ring: Int, var level: Int = 3)

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
            .map { row -> Row(cleanLine(row.joinToString(" ") { it.text }), row.maxOf { it.height }, ring(row.first().centerY)) }
            .filter { it.text.isNotEmpty() }
        val filtered = rows.joinToString("\n") { it.text }
        if (rows.isEmpty()) return OcrText(raw, filtered)
        assignLevels(rows)

        // Summary: middle ring first, widening outward; reading order within a ring (stable sort).
        val summary = rows.sortedBy { it.ring }.joinToString("\n") { row ->
            when (row.level) {
                1 -> "# ${row.text}"
                2 -> "## ${row.text}"
                else -> row.text
            }
        }
        // Detail: a solid title in the middle makes the outer rows secondary, so they go to the bottom.
        val hasMiddleTitle = rows.any { it.ring == 0 && it.level == 1 && isSolidTitle(it.text) }
        val detail = if (hasMiddleTitle) {
            val (middle, outer) = rows.partition { it.ring == 0 }
            (middle + outer).joinToString("\n") { it.text }
        } else {
            filtered
        }
        return OcrText(raw, filtered, summary, detail, pickTitle(rows))
    }

    /**
     * Solid title rows first, then solid emphasized rows; within a level the
     * middle ring wins, then the larger font, then reading order. Null when no
     * row is both prominent and solid, so callers fall back to their own digest.
     */
    private fun pickTitle(rows: List<Row>): String? = (1..2).firstNotNullOfOrNull { level ->
        // minWithOrNull keeps the first of equal rows, which is reading order.
        rows.filter { it.level == level && isSolidTitle(it.text) }
            .minWithOrNull(compareBy<Row> { it.ring }.thenByDescending { it.size })
    }?.text

    /**
     * Font size first: rows clearly larger than the screen's body text (the
     * median row height) become titles or emphasized. ALL CAPS then promotes a
     * row one level, since caps is how screens shout without a bigger font.
     */
    private fun assignLevels(rows: List<Row>) {
        // Lower median: body text is the baseline, so headings never pull it up.
        val median = rows.map { it.size }.sorted()[(rows.size - 1) / 2]
        for (row in rows) {
            val bySize = when {
                median <= 0f -> 3
                row.size >= median * TITLE_RATIO -> 1
                row.size >= median * EMPHASIS_RATIO -> 2
                else -> 3
            }
            row.level = if (isAllCaps(row.text)) maxOf(1, bySize - 1) else bySize
        }
    }

    private fun isSolidTitle(text: String): Boolean =
        text.split(' ').count { it.isNotBlank() } >= MIN_TITLE_WORDS || text.length >= MIN_TITLE_CHARS

    /** Scripts without case (Devanagari, CJK) never count as caps. */
    private fun isAllCaps(text: String): Boolean {
        val letters = text.filter { it.isLetter() }
        return letters.length >= MIN_CAPS_LETTERS && letters.all { it.isUpperCase() }
    }

    /** 0 for the middle ring, widening outward; rows past every ring share the last index. */
    private fun ring(centerY: Float): Int {
        val offset = kotlin.math.abs(centerY - 0.5f)
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
