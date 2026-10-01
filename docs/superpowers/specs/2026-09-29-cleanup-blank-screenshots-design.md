# Feature Specification: Possible Clean Up Section & Permanent Deletion

## 1. Overview & Goals

Users accumulate junk screenshots on their devices, notably:
1. Accidental screenshots consisting of just a solid black screen (e.g. phone waking up, locked screen) or plain white screen with nothing on it (e.g. blank browser page, blank document).
2. Uncategorized screenshots that did not match any content categories.

This feature adds:
- Deterministic on-device detection of blank screens (solid black / plain white with no text).
- A dedicated **"Clean Up"** section in the main UI category filter bar with sub-filters:
  - `All Clean Up`
  - `Blank Screens`
  - `Uncategorized`
- Batch multi-select deletion with "Select All" and confirmation warning.
- Single-item permanent deletion from both `ImageDetailDialog` and `ScreenshotDetailScreen`.
- Actual permanent deletion from the device's photo library via `MediaStore` on Android and `PHPhotoLibrary` on iOS, combined with SQLDelight cache synchronization.

## 2. Architecture & Components

### 2.1 Pure Kotlin Blank Screen Detector (`BlankScreenDetector.kt`)
Located in `shared/src/commonMain/kotlin/com/agy/imagecategorizer/classify/BlankScreenDetector.kt`.
- Pure Kotlin, unit-testable in `commonTest`.
- Works with `ImageContentAnalyzer.PixelSource`.
- Evaluation criteria:
  - **Text check**: Must have no OCR text (`filteredText.isBlank()` and `rawText.isBlank()`).
  - **Luminance & Uniformity check**:
    - **Black Screen**: sampled pixels have average luminance < 15 (brightness < 0.06), low variance, and edge density < 0.02.
    - **White Screen**: sampled pixels have average luminance > 240 (brightness > 0.94), white ratio > 0.95, and edge density < 0.02.
- Returns `BlankScreenType`: `BlackScreen`, `WhiteScreen`, or `None`.

### 2.2 MediaScanner & Analysis Integration
- Android: `MediaScanner.android.kt` checks whether OCR text is empty; if so, runs `BlankScreenDetector` on the sampled bitmap. When detected, sets `subCategory = "Blank Screen"`, `description = "Solid black screen"` or `"Plain white screen"`, and `objectSource = ObjectSource.Ocr`.
- iOS: `MediaScanner.ios.kt` runs the same check using `BlankScreenDetector`.
- `MediaScanner.deleteScreenshots(ids: List<String>): List<String>`:
  - Android: calls `contentResolver.delete(uri, null, null)`.
  - iOS: calls `PHPhotoLibrary.sharedPhotoLibrary().performChanges` to delete the `PHAsset`s.

### 2.3 Repository Layer (`ScreenshotRepository.kt`)
- `deleteScreenshots(ids: List<String>): List<String>`:
  - Executes deletion on device via `scanner.deleteScreenshots(ids)`.
  - In SQLite transaction, removes deleted IDs via `queries.deleteById(id)` and `queries.deleteOverride(id)`.
  - Reloads / updates cached screenshots.

### 2.4 Presentation Layer (`HomeScreen.kt`, `ScreenshotDetailScreen.kt`)
- `HomeScreen`:
  - Adds `CLEANUP_KEY = "cleanup"` to category navigation.
  - When in Clean Up mode:
    - Renders sub-category filter chips: `All Clean Up`, `Blank Screens`, `Uncategorized`.
    - Renders batch selection bar: "Select All" / "Deselect All", selected count indicator, "Delete Selected" button.
    - Displays checkable cards in the grid.
    - Displays permanent delete confirmation dialog before deleting.
- `ImageDetailDialog` & `ScreenshotDetailScreen`:
  - Adds a red "Delete Screenshot" button with confirmation dialog.

## 3. Testing Plan
- `BlankScreenDetectorTest` in `commonTest`:
  - Solid black grid -> `BlackScreen`
  - Solid white grid -> `WhiteScreen`
  - Grid with text / shapes -> `None`
  - High saturation or gradient -> `None`
- `ScreenshotRepositoryTest`:
  - `deleteScreenshots` drops records from repository cache and database queries.
- UI compile and assemble verification.
