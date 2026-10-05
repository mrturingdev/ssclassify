package com.mrturingdev.ssclassify.classify

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ObjectResolverTest {

    @Test
    fun textNamesTheObjectMostSpecificFirst() {
        assertEquals(
            DetectedObject("Boarding Pass", ObjectSource.Ocr),
            ObjectResolver.fromText("Your ticket\nBoarding pass YT 123"),
        )
        assertEquals(DetectedObject("Invoice", ObjectSource.Ocr), ObjectResolver.fromText("INVOICE #42 Total 12.00"))
        assertEquals(DetectedObject("Groceries", ObjectSource.Ocr), ObjectResolver.fromText("Category: groceries"))
    }

    @Test
    fun keywordsMustBeWholeWords() {
        assertNull(ObjectResolver.fromText("border reordered menus"))
    }

    @Test
    fun imageModelOnlyForTextLightScreenshots() {
        assertTrue(ObjectResolver.needsImageModel(""))
        assertTrue(ObjectResolver.needsImageModel("Golden retriever for sale"))
        assertFalse(ObjectResolver.needsImageModel("Pulse Fitness Club membership renewal Yoga class Tuesday 6pm Trainer Anita"))
    }

    @Test
    fun screenArtifactsAndLowConfidenceLabelsAreIgnored() {
        // What MobileNet actually returned for text screenshots on the emulator.
        assertNull(ObjectResolver.fromImageLabels(listOf(ClassificationResult("envelope", 0.9f))))
        assertNull(ObjectResolver.fromImageLabels(listOf(ClassificationResult("web site", 0.7f))))
        assertNull(ObjectResolver.fromImageLabels(listOf(ClassificationResult("golden retriever", 0.3f))))
        // Measured on a photo crop of beach grass: confidently wrong, so under the bar.
        assertNull(ObjectResolver.fromImageLabels(listOf(ClassificationResult("ear", 0.57f))))
        assertEquals(
            DetectedObject("Golden Retriever", ObjectSource.TensorFlowLite),
            ObjectResolver.fromImageLabels(
                listOf(ClassificationResult("web site", 0.95f), ClassificationResult("golden retriever", 0.8f)),
            ),
        )
    }
}
