package com.mrturingdev.ssclassify.classify

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Calibration check against real (scrubbed) OCR line dumps exported from the
 * debug "Export OCR lines" action. Each fixture's `# title:` header lists the
 * acceptable titles separated by " | ", or `*` when the screen has no clear one.
 * Title picking is heuristic, so this guards a minimum hit count rather than
 * every fixture: raise [MIN_TITLE_HITS] when a change gets more right.
 */
class OcrFixturesTest {

    private class Fixture(val name: String, val expected: List<String>?, val lines: List<OcrLine>)

    private fun fixtures(): List<Fixture> {
        val dir = File(javaClass.classLoader!!.getResource("ocr-fixtures")!!.toURI())
        return dir.listFiles()!!.sortedBy { it.name }.map { file ->
            val text = file.readLines()
            val expected = text.first { it.startsWith("# title:") }.removePrefix("# title:").trim()
            val lines = text.filter { it.isNotBlank() && !it.startsWith("#") }.map {
                val p = it.split(' ', limit = 5)
                OcrLine(p.getOrElse(4) { "" }, p[0].toFloat(), p[1].toFloat(), p[2].toFloat(), p[3].toFloat())
            }
            Fixture(file.nameWithoutExtension, expected.takeUnless { it == "*" }?.split(" | "), lines)
        }
    }

    @Test
    fun titlesMatchWhatAPersonWouldCallTheScreen() {
        val scored = fixtures().filter { it.expected != null }
        val report = scored.map { f ->
            val title = OcrTextProcessor.process(f.lines).title
            val hit = title in f.expected!!
            println("${if (hit) "PASS" else "MISS"} ${f.name}: got \"$title\", want ${f.expected}")
            hit
        }
        val hits = report.count { it }
        println("title hits: $hits/${scored.size}")
        assertTrue(hits >= MIN_TITLE_HITS, "title hits dropped to $hits/${scored.size}, minimum $MIN_TITLE_HITS")
    }

    @Test
    fun qrPreviewPrefersTheTitleOverAGuessedCompanyName() {
        // A QR voucher: no merchant label, suffix or business word, so any company name would be a guess.
        val text = OcrTextProcessor.process(fixtures().single { it.name == "voucher-redemption" }.lines)
        val preview = ScreenshotContentSummarizer.previewText(text.filtered, isQr = true, title = text.title)
        assertEquals("Redemption successful • Rs. 220", preview)
    }

    @Test
    fun previewAppendsOnlyTheAmountNotItsMergedRow() {
        // "< Other payment methods" and "fone pay Rs. 699.00" share a screen row.
        val text = OcrTextProcessor.process(fixtures().single { it.name == "qr-checkout-fonepay" }.lines)
        val preview = ScreenshotContentSummarizer.previewText(text.filtered, isQr = true, title = text.title)
        assertEquals("Scan to pay with Fonepay • Rs. 699.00", preview)
    }

    private companion object {
        const val MIN_TITLE_HITS = 13
    }
}
