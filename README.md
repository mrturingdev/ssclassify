# S.S. Classify

A Compose Multiplatform (KMP) mobile app that finds the screenshots in your
photo library, reads them with on-device OCR and groups them by content.
Other images are ignored. Everything runs on-device, no network.

| Platform | Photo library API | Status |
|----------|--------------------|--------|
| Android  | MediaStore          | Builds (assembleDebug) |
| iOS      | PhotoKit (PHPhotoLibrary) | Builds (xcodebuild + shared framework) |

## Features

- Scans the device photo library for screenshots only and classifies each one.
- Categories: Receipts, Finance, Shopping, Travel, Food, Health, Work, Code,
  Chats, Social, Documents, Uncategorized (empty categories are hidden).
- Category chips with per-count badges; tapping a chip filters the grid.
- Results are stored in SQLite (SQLDelight); rescans only OCR new or changed
  screenshots and drop deleted ones. Last results show instantly at launch.
- Correct a screenshot's category from its detail dialog, or reset it to
  automatic. Corrections are stored separately and survive re-analysis.
- Corrections teach the classifier: screenshots whose text closely matches a
  corrected one (same app screen or receipt layout) get the same category
  (`classify/CorrectionLearner.kt`, TF-IDF cosine similarity >= 0.5).
- Full-text search over OCR text and file names (SQLite FTS4, prefix match,
  all words must match); combines with the category chips.
- Thumbnails load lazily (async, cached, downscaled to ~320px).
- Auto-refreshes when the photo library changes (ContentObserver on Android,
  PHPhotoLibraryChangeObserver on iOS).
- Requests photo access on first scan and shows a permission-required state.

## How categorization works

`shared/src/commonMain/.../classify/ScreenshotCategorizer.kt` is pure and unit-tested:

1. **Screenshot detection** - Android: file name / folder contains a screenshot
   token (`screenshot`, `screen shot`, `captura`, `bildschirmfoto`, ...).
   iOS: `PHAssetMediaSubtypePhotoScreenshot`. Everything else is skipped.
2. **OCR** - ML Kit Text Recognition (Android, Latin script, near full
   resolution) or Apple Vision (iOS) reads the whole screenshot once, with a
   box per line. `classify/OcrTextProcessor.kt` turns that into:
   - **raw text**: every line as the engine returned it;
   - **filtered text**: only lines in the central 90% (status and nav bars
     live in the outer 5%), rebuilt into reading-order rows so split columns
     rejoin ("Total" + "508.50" -> "Total 508.50"), symbol/garbage tokens and
     pure status-bar lines removed.
   The detail screen shows both.
3. **Keyword rules** - whole-word keyword hits over the *filtered* text + file
   name are counted per category; the most hits wins, ties go to the order
   above, no hits -> Uncategorized. Search indexes the raw text.
4. **Object** (`classify/ObjectResolver.kt`) - named from the filtered text
   when it contains one ("Boarding Pass", "Invoice"). Only text-light,
   photo-first screenshots run MobileNet (Android), on the tallest text-free
   band found from the OCR boxes; labels that describe screens ("web site",
   "envelope") and anything under 0.6 confidence are ignored. The detail
   screen says which engine named it.

## Project structure

```
image-categorizer/
├── shared/                          # Kotlin Multiplatform module
│   ├── src/commonMain/             # Shared Compose UI + domain + categorize logic
│   │   └── kotlin/com/agy/imagecategorizer/
│   │       ├── App.kt             # Root composable, scan flow, PERMISSION_DENIED
│   │       ├── model/             # ImageCategory, ImageRecord
│   │       ├── classify/          # ScreenshotCategorizer (pure keyword rules)
│   │       ├── data/              # expect MediaScanner / ThumbnailLoader
│   │       └── ui/                # HomeScreen (chips + lazy grid)
│   ├── src/androidMain/           # MediaStore actuals (+ AndroidApp.init / hasPhotoAccess)
│   ├── src/iosMain/               # PhotoKit actuals (PHPhotoLibrary, PHImageManager)
│   └── src/commonTest/            # ScreenshotCategorizerTest, ImageContentAnalyzerTest
├── androidApp/                    # Android launcher (MainActivity, manifest, permissions)
├── iosApp/                        # Xcode shell project
│   ├── iosApp/                    # iOSApp.swift, ContentView.swift, Info.plist
│   └── Configuration/             # Build settings (xcconfig)
└── gradle/libs.versions.toml      # Version catalog
```

## Prerequisites

- JDK 21 (`org.gradle.java.home` in `gradle.properties`). Android Studio's arm64
  JDK 25 also builds the project, and is needed on Apple Silicon to run iOS
  simulator tests:
  ```bash
  arch -arm64 ./gradlew "-Dorg.gradle.java.home=/Applications/Android Studio.app/Contents/jbr/Contents/Home" :shared:iosSimulatorArm64Test
  ```
  ```bash
  export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home
  ```
- Android: Android SDK (minSdk 24, target/compileSdk 35), AGP 8.6.1, Gradle 8.8 (wrapper included)
- iOS: Xcode + Xcode Command Line Tools (Kotlin/Native 2.4.0)

## Build & run

### Android
```bash
./gradlew :androidApp:assembleDebug
# APK at androidApp/build/outputs/apk/debug/androidApp-debug.apk
./gradlew :shared:testDebugUnitTest   # unit tests
```

### iOS
Open `iosApp/iosApp.xcodeproj` in Xcode and run the `ImageCategorizer` scheme,
or build from the CLI:
```bash
xcodebuild -project iosApp/iosApp.xcodeproj -scheme ImageCategorizer \
  -configuration Debug -sdk iphonesimulator CODE_SIGNING_ALLOWED=NO build
```
The Xcode build phase invokes `./gradlew :shared:embedAndSignAppleFrameworkForXcode`,
which compiles the shared framework for the active SDK (`Shared.framework`).

## Version notes

- Kotlin 2.4.0, Compose Multiplatform 1.11.1. iOS targets are `iosArm64` and
  `iosSimulatorArm64` only (CMP 1.11 has no `iosX64`); Intel Macs are not supported.
- The only JDK 21 on the dev machine is x86_64, so Gradle runs under Rosetta and
  Kotlin/Native skips iOS simulator tests. Run them with an arm64 JDK (below).
- The Xcode app links `-lsqlite3` itself: `Shared` is a static framework, so
  SQLDelight's SQLite dependency does not propagate to the app on its own.
- `Info.plist` must keep `CADisableMinimumFrameDurationOnPhone = true`; Compose
  crashes on launch in debug builds without it.
- AGP 8.6.1 and compileSdk 35 are required by androidx.compose 1.10.x/1.11.x.
- Kotlin 2.4 uses the v2 Android source layout: Android sources live in
  `src/androidMain/kotlin` and `src/androidMain/AndroidManifest.xml`.
- Kotlin/Native 2.4 renames interop surfaces - Platform enums resolve as
  top-level constants (e.g. `PHAssetMediaSubtypePhotoScreenshot`) and
  `requestAuthorizationForAccessLevel...` is `requestAuthorizationForAccessLevel`.

## Permissions

- Android: `READ_MEDIA_IMAGES` (API 33+), `READ_EXTERNAL_STORAGE` (≤ API 32),
  requested from `MainActivity` before the UI loads.
- iOS: `NSPhotoLibraryUsageDescription` in `Info.plist`; authorization requested
  via `PHPhotoLibrary.requestAuthorizationForAccessLevel(PHAccessLevelReadWrite)`.

## License

Private/internal project scaffold.