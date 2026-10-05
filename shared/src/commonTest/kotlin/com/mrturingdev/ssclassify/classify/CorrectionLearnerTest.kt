package com.mrturingdev.ssclassify.classify

import com.mrturingdev.ssclassify.classify.CorrectionLearner.Correction
import com.mrturingdev.ssclassify.classify.CorrectionLearner.Doc
import com.mrturingdev.ssclassify.model.ImageCategory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CorrectionLearnerTest {

    // Real names differ only in the timestamp, which tokenization drops as a number.
    private fun doc(id: String, text: String) = Doc(id, text, "Screenshot_20260901-100000.png")

    // Two screens from the same gym app, plus a varied library so IDF is realistic.
    private val gymYoga = doc("gym1", "Pulse Fitness Club membership renewal Yoga class Tuesday 6pm Trainer Anita Studio 2 Book now")
    private val gymZumba = doc("gym2", "Pulse Fitness Club membership renewal Zumba class Friday 7pm Trainer Bikash Studio 1 Book now")
    private val flight = doc("flt", "Boarding pass Flight YT 123 Gate 4 Seat 12A Departure 10:30 Airline Yeti")
    private val receipt = doc("rcp", "Receipt Bhatbhateni Subtotal 450.00 Tax 58.50 Total 508.50 Paid by card")
    private val chat = doc("cht", "WhatsApp Ram typing last seen today Book now reply sent delivered")
    private val code = doc("cod", "fun main val x null return Exception in thread main Kotlin")
    private val library = listOf(gymYoga, gymZumba, flight, receipt, chat, code)

    @Test
    fun sameTemplateScoresAboveThresholdAndUnrelatedBelow() {
        val learner = CorrectionLearner(library, emptyList())
        val threshold = CorrectionLearner.DEFAULT_THRESHOLD
        val sameTemplate = learner.similarity(gymYoga, gymZumba)
        assertTrue(sameTemplate >= threshold + 0.1, "same template: $sameTemplate")
        for (other in listOf(flight, receipt, chat, code)) {
            val unrelated = learner.similarity(gymYoga, other)
            assertTrue(unrelated <= threshold - 0.3, "unrelated ${other.id}: $unrelated")
        }
    }

    @Test
    fun correctionGeneralizesToSameTemplateOnly() {
        val learner = CorrectionLearner(library, listOf(Correction(gymYoga, ImageCategory.Health)))
        assertEquals(ImageCategory.Health, learner.categorize(gymZumba))
        assertNull(learner.categorize(flight))
        assertNull(learner.categorize(chat), "sharing 'book now' is not enough")
    }

    @Test
    fun neverLearnsFromItself() {
        val learner = CorrectionLearner(library, listOf(Correction(gymYoga, ImageCategory.Health)))
        assertNull(learner.categorize(gymYoga))
    }

    @Test
    fun closerCorrectionWinsWhenCorrectionsDisagree() {
        val gymYogaAgain = doc("gym3", "Pulse Fitness Club membership renewal Yoga class Tuesday 6pm Trainer Anita Studio 2")
        val learner = CorrectionLearner(
            library + gymYogaAgain,
            listOf(Correction(gymYoga, ImageCategory.Health), Correction(gymZumba, ImageCategory.Work)),
        )
        assertEquals(ImageCategory.Health, learner.categorize(gymYogaAgain))
    }

    @Test
    fun emptyTextAndNoCorrectionsLearnNothing() {
        assertNull(CorrectionLearner(library, emptyList()).categorize(gymZumba))
        val learner = CorrectionLearner(library, listOf(Correction(gymYoga, ImageCategory.Health)))
        assertNull(learner.categorize(Doc("empty", "", "")))
    }

    @Test
    fun tokensKeepDevanagariWordsWholeAndDropNumbers() {
        assertEquals(
            setOf("नमस्ते", "संसार", "total", "12a"),
            CorrectionLearner.tokens(Doc("x", "नमस्ते संसार, Total 508.50 12A 2026", "")),
        )
    }
}
