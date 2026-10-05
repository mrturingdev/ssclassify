# Enhanced Screenshot Details Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enhance screenshot details by generating preview content summaries, a dedicated full details screen navigated from the quick detail dialog, and an interactive zoomable full-screen image viewer.

**Architecture:** Extend `ImageRecord` to carry preserved `ocrText`; introduce a pure `ScreenshotContentSummarizer` for concise 1–2 line summaries and key entity highlights; improve `ThumbnailLoader` cache keys for high-res rendering; build a gesture-driven `FullscreenImageViewer` (1x–5x zoom, pan, double-tap); build a dedicated `ScreenshotDetailScreen` with copyable OCR text, metadata, and category controls; and wire navigation through `HomeScreen`.

**Tech Stack:** Kotlin Multiplatform (KMP), Compose Multiplatform, SQLDelight, Android SDK MediaStore / Photos, Jetpack Compose Material 3.

## Global Constraints
- Target platform: Kotlin Multiplatform (Android & iOS).
- UI framework: Compose Multiplatform with Material 3.
- All business logic and summarization must reside in `commonMain` and be fully unit-testable without Android/iOS dependencies.
- No external heavy dependencies; utilize Kotlin stdlib and Compose built-in gesture detectors.
- Maintain existing database schema and tests intact.

---

### Task 1: Expose Preserved `ocrText` in `ImageRecord` and Update Repository Mapping

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/mrturingdev/ssclassify/model/Models.kt:20-50`
- Modify: `shared/src/commonMain/kotlin/com/mrturingdev/ssclassify/data/ScreenshotRepository.kt:115-135`
- Test: `shared/src/androidUnitTest/kotlin/com/mrturingdev/ssclassify/data/ScreenshotRepositoryTest.kt`

**Interfaces:**
- Consumes: `ocr_text` column from SQLDelight `screenshot` table.
- Produces: `ImageRecord.ocrText: String`.

- [ ] **Step 1: Write the failing test**

Add test to `shared/src/androidUnitTest/kotlin/com/mrturingdev/ssclassify/data/ScreenshotRepositoryTest.kt`:
```kotlin
    @Test
    fun cachedRecordsIncludeOcrText() = runBlocking {
        val source = FakeSource(
            assets = listOf(asset("a", date = 1)),
            text = mapOf("a" to "Total $50.00 Paid"),
        )
        val repo = repository(source)
        repo.scan()
        val record = repo.cached().single()
        assertEquals("Total $50.00 Paid", record.ocrText)
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :shared:testDebugUnitTest --tests "com.mrturingdev.ssclassify.data.ScreenshotRepositoryTest.cachedRecordsIncludeOcrText"`
Expected: Compilation failure or assertion failure because `ocrText` does not exist on `ImageRecord`.

- [ ] **Step 3: Implement minimal code**

In `shared/src/commonMain/kotlin/com/mrturingdev/ssclassify/model/Models.kt`:
Add `val ocrText: String = ""` to `ImageRecord`:
```kotlin
data class ImageRecord(
    val id: String,
    val name: String,
    val folder: String,
    val relativePath: String,
    val width: Long,
    val height: Long,
    val dateMillis: Long,
    val category: ImageCategory,
    val subCategory: String? = null,
    val description: String? = null,
    val autoCategory: ImageCategory = category,
    val source: CategorySource = CategorySource.Rules,
    val ocrText: String = "",
) {
    val isCategoryCorrected: Boolean
        get() = source == CategorySource.User

    val aspectRatio: Float
        get() = if (height > 0) width.toFloat() / height.toFloat() else 0f
}
```

In `shared/src/commonMain/kotlin/com/mrturingdev/ssclassify/data/ScreenshotRepository.kt`:
Update `Row.toRecord(learner: CorrectionLearner)`:
```kotlin
    private fun Row.toRecord(learner: CorrectionLearner): ImageRecord {
        val learned = learner.categorize(doc)
        val auto = learned ?: ScreenshotCategorizer.categorize(ocr_text, name)
        return ImageRecord(
            id = id,
            name = name,
            folder = folder,
            relativePath = relative_path,
            width = width,
            height = height,
            dateMillis = date_millis,
            category = override ?: learned ?: auto,
            subCategory = sub_category,
            description = description,
            autoCategory = auto,
            source = when {
                override != null -> CategorySource.User
                learned != null -> CategorySource.Learned
                else -> CategorySource.Rules
            },
            ocrText = ocr_text,
        )
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :shared:testDebugUnitTest --tests "com.mrturingdev.ssclassify.data.ScreenshotRepositoryTest.cachedRecordsIncludeOcrText"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/mrturingdev/ssclassify/model/Models.kt shared/src/commonMain/kotlin/com/mrturingdev/ssclassify/data/ScreenshotRepository.kt shared/src/androidUnitTest/kotlin/com/mrturingdev/ssclassify/data/ScreenshotRepositoryTest.kt
git commit -m "feat(shared): expose preserved ocrText in ImageRecord"
```

---

### Task 2: Implement `ScreenshotContentSummarizer` with Unit Tests

**Files:**
- Create: `shared/src/commonMain/kotlin/com/mrturingdev/ssclassify/classify/ScreenshotContentSummarizer.kt`
- Create: `shared/src/commonTest/kotlin/com/mrturingdev/ssclassify/classify/ScreenshotContentSummarizerTest.kt`

**Interfaces:**
- Produces:
  - `ScreenshotContentSummarizer.summarizeDigest(ocrText: String, fallbackDescription: String? = null): String`
  - `ScreenshotContentSummarizer.extractHighlights(ocrText: String): List<Pair<String, String>>`
  - `ScreenshotContentSummarizer.formatCleanOcrText(ocrText: String): String`

- [ ] **Step 1: Write the failing tests**

Create `shared/src/commonTest/kotlin/com/mrturingdev/ssclassify/classify/ScreenshotContentSummarizerTest.kt`:
```kotlin
package com.mrturingdev.ssclassify.classify

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScreenshotContentSummarizerTest {

    @Test
    fun extractsCleanDigestFromReceiptOcr() {
        val ocr = """
            10:45 AM  100%
            Bhatbhateni Supermarket
            Tax Invoice / Cash Receipt
            Date: 2026-09-15
            Item 1   $10.00
            Item 2   $40.00
            Subtotal: $50.00
            Total: $50.00
            Thank you for shopping
        """.trimIndent()
        val digest = ScreenshotContentSummarizer.summarizeDigest(ocr)
        assertTrue(digest.contains("Bhatbhateni Supermarket") || digest.contains("Total: $50.00"))
        assertTrue(!digest.startsWith("10:45 AM"))
    }

    @Test
    fun extractsDetectedHighlights() {
        val ocr = """
            Airline Yeti Flight YT 123
            Date: 2026-09-20
            Total: $120.50
            PNR: 98ABC
        """.trimIndent()
        val highlights = ScreenshotContentSummarizer.extractHighlights(ocr)
        val keys = highlights.map { it.first }
        assertTrue(keys.contains("Total") || keys.contains("Amount"))
    }

    @Test
    fun fallbacksGracefullyWhenOcrEmpty() {
        val fallback = "Objects: phone, screen."
        val digest = ScreenshotContentSummarizer.summarizeDigest("", fallback)
        assertEquals(fallback, digest)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :shared:testDebugUnitTest --tests "com.mrturingdev.ssclassify.classify.ScreenshotContentSummarizerTest"`
Expected: FAIL with compilation error (unresolved reference `ScreenshotContentSummarizer`).

- [ ] **Step 3: Implement `ScreenshotContentSummarizer`**

Create `shared/src/commonMain/kotlin/com/mrturingdev/ssclassify/classify/ScreenshotContentSummarizer.kt`:
```kotlin
package com.mrturingdev.ssclassify.classify

object ScreenshotContentSummarizer {

    private val statusNoiseRegex = Regex(
        "^([0-2]?[0-9]:[0-5][0-9](\\s*[ap]m)?|\\d+%$|lte|5g|4g|wifi|volte|battery|am|pm)$",
        RegexOption.IGNORE_CASE,
    )

    private val amountRegex = Regex(
        """(?i)(?:total|grand\s*total|subtotal|amount|due|paid|price|balance|rs\.?|npr|\$)\s*[:=-]?\s*([$€£₹]?\s*\d+(?:[.,]\d{1,2})?)"""
    )

    private val dateRegex = Regex(
        """(?i)(?:date|dated)?\s*[:=-]?\s*(\b\d{1,4}[/-]\d{1,2}[/-]\d{1,4}\b|\b(?:jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\s+\d{1,2},?\s+\d{2,4}\b)"""
    )

    private val idRegex = Regex(
        """(?i)(?:order|invoice|txn|transaction|ticket|pnr|id|ref|bill)\s*(?:no\.?|id|#)?\s*[:=-]?\s*([a-z0-9\-_]{4,20})"""
    )

    /** Generates a concise 1-2 line summary for preview cards and dialog headers. */
    fun summarizeDigest(ocrText: String, fallbackDescription: String? = null): String {
        val lines = ocrText.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() && !isNoiseLine(it) }

        if (lines.isEmpty()) {
            return fallbackDescription?.takeIf { it.isNotBlank() } ?: "No text detected"
        }

        // Check if there is an amount or headline line
        val amountLine = lines.firstOrNull { amountRegex.containsMatchIn(it) }
        val titleLine = lines.firstOrNull { it.length in 4..60 && !amountRegex.containsMatchIn(it) } ?: lines.first()

        val digest = if (amountLine != null && amountLine != titleLine) {
            "$titleLine • $amountLine"
        } else {
            lines.take(2).joinToString(" • ")
        }

        return if (digest.length > 90) digest.take(87).trimEnd() + "..." else digest
    }

    /** Extracts structured key-value highlights for the detail screen. */
    fun extractHighlights(ocrText: String): List<Pair<String, String>> {
        val highlights = mutableListOf<Pair<String, String>>()

        amountRegex.find(ocrText)?.let { match ->
            highlights += "Amount" to match.value.trim()
        }

        dateRegex.find(ocrText)?.let { match ->
            highlights += "Date" to match.groupValues.getOrElse(1) { match.value }.trim()
        }

        idRegex.find(ocrText)?.let { match ->
            highlights += "Reference" to match.value.trim()
        }

        return highlights
    }

    /** Cleans up raw OCR text into readable paragraphs for copy and full inspection. */
    fun formatCleanOcrText(ocrText: String): String {
        return ocrText.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n")
    }

    private fun isNoiseLine(line: String): Boolean {
        val clean = line.trim().lowercase()
        return clean.length <= 2 || statusNoiseRegex.matches(clean)
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :shared:testDebugUnitTest --tests "com.mrturingdev.ssclassify.classify.ScreenshotContentSummarizerTest"`
Expected: PASS (all tests green).

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/mrturingdev/ssclassify/classify/ScreenshotContentSummarizer.kt shared/src/commonTest/kotlin/com/mrturingdev/ssclassify/classify/ScreenshotContentSummarizerTest.kt
git commit -m "feat(shared): add ScreenshotContentSummarizer for digest and highlights"
```

---

### Task 3: Enhance `ThumbnailLoader` with Resolution-Aware Caching & High-Res Support

**Files:**
- Modify: `shared/src/androidMain/kotlin/com/mrturingdev/ssclassify/data/ThumbnailLoader.android.kt`
- Modify: `shared/src/iosMain/kotlin/com/mrturingdev/ssclassify/data/ThumbnailLoader.ios.kt`

**Interfaces:**
- Consumes: `id: String`, `sizePx: Int`.
- Produces: `ThumbnailLoader.load(id: String, sizePx: Int): ImageBitmap?` with cache key `"$id@$sizePx"`.

- [ ] **Step 1: Inspect and update cache key logic in `ThumbnailLoader.android.kt`**

In `shared/src/androidMain/kotlin/com/mrturingdev/ssclassify/data/ThumbnailLoader.android.kt`:
Update `cache` key to include `sizePx`:
```kotlin
    actual suspend fun load(id: String, sizePx: Int): ImageBitmap? = withContext(Dispatchers.IO) {
        val key = "$id@$sizePx"
        cache.get(key) ?: decode(id, sizePx)?.also { cache.put(key, it) }
    }
```
And in `computeSampleSize`:
```kotlin
    private fun computeSampleSize(width: Int, height: Int, sizePx: Int): Int {
        if (sizePx <= 0) return 1
        var sample = 1
        while (width / (sample * 2) >= sizePx || height / (sample * 2) >= sizePx) {
            sample *= 2
        }
        return sample
    }
```

- [ ] **Step 2: Update `ThumbnailLoader.ios.kt` similarly**

In `shared/src/iosMain/kotlin/com/mrturingdev/ssclassify/data/ThumbnailLoader.ios.kt`:
Update cache lookup:
```kotlin
    actual suspend fun load(id: String, sizePx: Int): ImageBitmap? = mutex.withLock {
        val key = "$id@$sizePx"
        cache[key] ?: fetch(id, sizePx).also { bitmap ->
            if (bitmap != null) cache[key] = bitmap
        }
    }
```

- [ ] **Step 3: Run existing tests to ensure no regressions**

Run: `./gradlew :shared:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add shared/src/androidMain/kotlin/com/mrturingdev/ssclassify/data/ThumbnailLoader.android.kt shared/src/iosMain/kotlin/com/mrturingdev/ssclassify/data/ThumbnailLoader.ios.kt
git commit -m "fix(shared): make ThumbnailLoader cache resolution-aware for high-res viewing"
```

---

### Task 4: Build `FullscreenImageViewer` Component

**Files:**
- Create: `shared/src/commonMain/kotlin/com/mrturingdev/ssclassify/ui/FullscreenImageViewer.kt`

**Interfaces:**
- Produces:
  - `@Composable fun FullscreenImageViewer(image: ImageRecord, thumbnailLoader: ThumbnailLoader, onDismiss: () -> Unit)`

- [ ] **Step 1: Write `FullscreenImageViewer.kt`**

Create `shared/src/commonMain/kotlin/com/mrturingdev/ssclassify/ui/FullscreenImageViewer.kt`:
Implement zoomable viewer using Compose `Modifier.pointerInput` with `detectTransformGestures` and `detectTapGestures` (for double tap to toggle zoom 1x to 2.5x), smooth scale clamping (1f to 5f), pan clamping, and top/bottom HUD overlay with Close button.
Also loads high-res image (size 2048), falling back immediately to the 320/720 cached bitmap while loading.

- [ ] **Step 2: Compile to verify syntax and API compatibility**

Run: `./gradlew :shared:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add shared/src/commonMain/kotlin/com/mrturingdev/ssclassify/ui/FullscreenImageViewer.kt
git commit -m "feat(ui): create FullscreenImageViewer with pinch, pan, and double-tap zoom"
```

---

### Task 5: Build `ScreenshotDetailScreen` (Dedicated Full Details Page)

**Files:**
- Create: `shared/src/commonMain/kotlin/com/mrturingdev/ssclassify/ui/ScreenshotDetailScreen.kt`

**Interfaces:**
- Produces:
  - `@Composable fun ScreenshotDetailScreen(image: ImageRecord, thumbnailLoader: ThumbnailLoader, onBack: () -> Unit, onOpenFullscreen: () -> Unit, onCategoryChange: (ImageCategory?) -> Unit)`

- [ ] **Step 1: Write `ScreenshotDetailScreen.kt`**

Create `shared/src/commonMain/kotlin/com/mrturingdev/ssclassify/ui/ScreenshotDetailScreen.kt`:
- `Scaffold` with `TopAppBar`: Back navigation icon, title "Screenshot Details", and category icon.
- `LazyColumn` / `Column` scrollable content:
  1. Hero image card (aspect ratio preserved or height 280.dp), clickable with tap-to-fullscreen hint and fullscreen icon button.
  2. Category selector row with `CategoryPicker` and source attribution note.
  3. Formatted Metadata section: dimensions (`${image.width} × ${image.height} px`), formatted date (`dateMillis`), and folder path.
  4. Extracted Highlights Card: Displays `ScreenshotContentSummarizer.extractHighlights(image.ocrText)`.
  5. Full OCR Text Card: Displays `ScreenshotContentSummarizer.formatCleanOcrText(image.ocrText)`, with a "Copy Text" button using `LocalClipboardManager.current.setText(AnnotatedString(...))` and a temporary snackbar/badge saying "Copied to clipboard".

- [ ] **Step 2: Compile to verify syntax and API compatibility**

Run: `./gradlew :shared:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add shared/src/commonMain/kotlin/com/mrturingdev/ssclassify/ui/ScreenshotDetailScreen.kt
git commit -m "feat(ui): create ScreenshotDetailScreen with full OCR text and metadata"
```

---

### Task 6: Wire Preview Summaries, Detail Dialog Actions, and Fullscreen in `HomeScreen.kt`

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/mrturingdev/ssclassify/ui/HomeScreen.kt`

**Interfaces:**
- Integrates:
  - `ImageCard`: uses `ScreenshotContentSummarizer.summarizeDigest(image.ocrText, image.description)`.
  - `ImageDetailDialog`: adds Fullscreen button on preview image, displays summary digest + highlights, and adds "View Full Details" button.
  - `HomeScreen`: manages `detailDialogImage: ImageRecord?`, `fullscreenImage: ImageRecord?`, and `activeDetailScreen: ImageRecord?`.

- [ ] **Step 1: Update `ImageCard` in `HomeScreen.kt`**

Update lines ~513-550 to display `ScreenshotContentSummarizer.summarizeDigest(image.ocrText, image.description)` instead of raw `image.description`.

- [ ] **Step 2: Update `ImageDetailDialog` in `HomeScreen.kt`**

- Add fullscreen expand icon overlay on the image preview; clicking it triggers `onOpenFullscreen()`.
- Display a clean "Summary" card below the title.
- In `AlertDialog` buttons:
  - Keep "Done" (dismiss).
  - Add primary button "View Full Details" with `Icons.AutoMirrored.Rounded.ArrowForward` which dismisses the dialog and calls `onNavigateToDetails(image)`.

- [ ] **Step 3: Wire screen state in `HomeScreen` and render `ScreenshotDetailScreen` / `FullscreenImageViewer`**

- Add state `activeDetailRecord: ImageRecord?`.
- When `activeDetailRecord != null`, render `ScreenshotDetailScreen` with `onBack = { activeDetailRecord = null }`.
- When `fullscreenRecord != null`, render `FullscreenImageViewer` with `onDismiss = { fullscreenRecord = null }`.

- [ ] **Step 4: Compile and test shared module**

Run: `./gradlew :shared:testDebugUnitTest`
Expected: BUILD SUCCESSFUL with 100% tests passing.

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/mrturingdev/ssclassify/ui/HomeScreen.kt
git commit -m "feat(ui): wire preview summary, full details navigation, and fullscreen viewer in HomeScreen"
```

---

### Task 7: Full App Build & Verification

**Files:**
- None (verification across whole project).

- [ ] **Step 1: Run all unit tests**

Run: `./gradlew :shared:testDebugUnitTest`
Expected: 100% tests passing.

- [ ] **Step 2: Build the Android application**

Run: `./gradlew :androidApp:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit any final integration adjustments**

```bash
git status
```
Verify working tree is clean.
