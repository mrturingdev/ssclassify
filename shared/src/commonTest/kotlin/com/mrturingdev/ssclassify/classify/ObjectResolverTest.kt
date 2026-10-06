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

    @Test
    fun resolvesReceiptDomainSubcategories() {
        assertEquals("Food", ObjectResolver.resolveSubCategory("Himalayan Java Cafe coffee pizza total $15", com.mrturingdev.ssclassify.model.ImageCategory.Receipts))
        assertEquals("Travel", ObjectResolver.resolveSubCategory("Yeti Airlines flight booking total $120", com.mrturingdev.ssclassify.model.ImageCategory.Receipts))
        assertEquals("Health", ObjectResolver.resolveSubCategory("City Pharmacy medicine dose total $25", com.mrturingdev.ssclassify.model.ImageCategory.Receipts))
        assertEquals("Grocery", ObjectResolver.resolveSubCategory("Bhatbhateni Supermarket provisions total $50", com.mrturingdev.ssclassify.model.ImageCategory.Receipts))
        assertEquals("Utility", ObjectResolver.resolveSubCategory("Electricity bill payment total $30", com.mrturingdev.ssclassify.model.ImageCategory.Receipts))
    }

    @Test
    fun resolvesDomainSubcategoriesAcrossTaxonomy() {
        assertEquals("Code", ObjectResolver.resolveSubCategory("fun testCode() {\n  return 42\n}", com.mrturingdev.ssclassify.model.ImageCategory.Learning))
        assertEquals("Quiz", ObjectResolver.resolveSubCategory("Physics Chapter 2 quiz questions", com.mrturingdev.ssclassify.model.ImageCategory.Learning))
        assertEquals("Boarding Pass", ObjectResolver.resolveSubCategory("Flight YT 123 Gate 4 Seat 12A", com.mrturingdev.ssclassify.model.ImageCategory.Travels))
        assertEquals("Menu", ObjectResolver.resolveSubCategory("Italian Restaurant Menu with Appetizers and Desserts", com.mrturingdev.ssclassify.model.ImageCategory.Foods))
        assertEquals("Recipe", ObjectResolver.resolveSubCategory("Chocolate Cake Recipe: 2 cups flour, 1 tablespoon sugar, bake at 350", com.mrturingdev.ssclassify.model.ImageCategory.Foods))
        assertEquals("Prescription", ObjectResolver.resolveSubCategory("Clinic OPD Prescription: Rx Paracetamol 500mg dose", com.mrturingdev.ssclassify.model.ImageCategory.Health))
        assertEquals("Fitness", ObjectResolver.resolveSubCategory("Daily Workout: 10,000 steps, 450 calories, heart rate 120 bpm", com.mrturingdev.ssclassify.model.ImageCategory.Health))
    }
}
