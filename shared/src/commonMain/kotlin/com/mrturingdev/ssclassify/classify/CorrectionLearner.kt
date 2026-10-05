package com.mrturingdev.ssclassify.classify

import com.mrturingdev.ssclassify.model.ImageCategory
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Generalizes user category corrections to similar screenshots.
 *
 * Each corrected screenshot becomes a labeled example. A screenshot whose
 * text is close enough to one or more examples (cosine similarity of binary
 * TF-IDF token vectors, IDF taken over the whole library) gets the category
 * with the highest summed similarity. This catches the common case: the same
 * app screen or receipt layout, corrected once, showing up again. Anything
 * below [threshold] is left to the keyword rules.
 *
 * Pure and deterministic; build a new instance whenever the library or the
 * corrections change.
 */
class CorrectionLearner(
    library: List<Doc>,
    corrections: List<Correction>,
    private val threshold: Double = DEFAULT_THRESHOLD,
) {
    data class Doc(val id: String, val text: String, val name: String)
    data class Correction(val doc: Doc, val category: ImageCategory)

    private class Example(val id: String, val vector: Map<String, Double>, val norm: Double, val category: ImageCategory)

    private val idf: Map<String, Double>
    private val docCount: Int
    private val examples: List<Example>

    init {
        val df = HashMap<String, Int>()
        for (doc in library) for (token in tokens(doc)) df[token] = (df[token] ?: 0) + 1
        docCount = library.size
        idf = df.mapValues { (_, count) -> idf(count) }
        examples = corrections.mapNotNull { c ->
            val vector = vectorOf(tokens(c.doc))
            val norm = norm(vector)
            if (norm == 0.0) null else Example(c.doc.id, vector, norm, c.category)
        }
    }

    /**
     * The category learned from corrections of *other* screenshots, or null
     * when nothing is similar enough. A screenshot never learns from itself,
     * so resetting its own correction falls back to what it would be without it.
     */
    fun categorize(doc: Doc): ImageCategory? {
        if (examples.isEmpty()) return null
        val vector = vectorOf(tokens(doc))
        val norm = norm(vector)
        if (norm == 0.0) return null

        // ponytail: O(library x corrections) per load; index examples by token if corrections reach the thousands
        val scores = HashMap<ImageCategory, Double>()
        for (example in examples) {
            if (example.id == doc.id) continue
            val similarity = dot(vector, example.vector) / (norm * example.norm)
            if (similarity >= threshold) {
                scores[example.category] = (scores[example.category] ?: 0.0) + similarity
            }
        }
        return scores.maxByOrNull { it.value }?.key
    }

    /** Cosine similarity of two docs under this library's IDF; exposed for threshold calibration tests. */
    internal fun similarity(a: Doc, b: Doc): Double {
        val va = vectorOf(tokens(a))
        val vb = vectorOf(tokens(b))
        val denominator = norm(va) * norm(vb)
        return if (denominator == 0.0) 0.0 else dot(va, vb) / denominator
    }

    private fun vectorOf(tokens: Set<String>): Map<String, Double> =
        tokens.associateWith { idf[it] ?: idf(0) }

    // Smoothed IDF: rarer tokens (store names, app titles) dominate, words on every screenshot barely count.
    private fun idf(df: Int): Double = ln((docCount + 1.0) / (df + 1.0)) + 1.0

    companion object {
        /** Calibration knob; CorrectionLearnerTest pins same-template vs unrelated similarities around it. */
        const val DEFAULT_THRESHOLD = 0.5

        /** Lowercased word tokens of OCR text + file name; pure numbers (amounts, dates) are noise. */
        internal fun tokens(doc: Doc): Set<String> {
            val result = HashSet<String>()
            val word = StringBuilder()
            fun flush() {
                if (word.length >= 2 && !word.all(Char::isDigit)) result += word.toString()
                word.clear()
            }
            for (c in "${doc.name} ${doc.text}".lowercase()) {
                if (c.isWordChar()) word.append(c) else flush()
            }
            flush()
            return result
        }

        // Combining marks keep Devanagari words (vowel signs, virama) in one piece.
        private fun Char.isWordChar(): Boolean =
            isLetterOrDigit() ||
                category == CharCategory.NON_SPACING_MARK ||
                category == CharCategory.COMBINING_SPACING_MARK

        private fun norm(vector: Map<String, Double>): Double = sqrt(vector.values.sumOf { it * it })

        private fun dot(a: Map<String, Double>, b: Map<String, Double>): Double {
            val (small, large) = if (a.size <= b.size) a to b else b to a
            return small.entries.sumOf { (token, weight) -> weight * (large[token] ?: 0.0) }
        }
    }
}
