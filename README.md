# Image Categorizer

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
2. **OCR** - ML Kit Text Recognition (Android, Latin script) or Apple Vision
   (iOS) reads the screenshot text.
3. **Keyword rules** - whole-word keyword hits over OCR text + file name are
   counted per category; the most hits wins, ties go to the order above, no
   hits -> Uncategorized.

On Android, a MobileNet TFLite model and OCR keywords also add a free-form
sub-category shown as a second chip row.

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

- JDK 21 (must set `JAVA_HOME`; the default JDK 25 breaks the Kotlin toolchain)
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

- Kotlin 2.4.0, Compose plugin 1.11.0, but Compose runtime/foundation/ui **pinned
  to 1.10.3**: CMP 1.11.0 dropped `iosX64` artifacts, and this machine is an
  Intel Mac (`x86_64`), so iOS simulator development needs the `iosX64` target.
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