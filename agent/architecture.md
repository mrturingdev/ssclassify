# Screenshot Categorizer App - Architecture

## 1. Architecture Goals

Build a privacy-first screenshot categorizer using Kotlin Compose Multiplatform (KMP/Compose). The architecture must:

- Share business logic, data handling, models, and Compose UI across Android and iOS.
- Keep photo-library access, OCR, background scheduling, and secure storage platform-specific.
- Process screenshots locally by default.
- Support offline full-text search and later semantic search.
- Remain testable through clear dependency boundaries.

---

## 2. High-Level Design

Use Clean Architecture with unidirectional data flow (UDF):

```text
Compose UI
    -> ViewModel / State Holder
        -> Use Cases
            -> Repository Interfaces
                -> Platform-aware Repository Implementations
                    -> Local Database / OCR / Photo Library / Classifier
```

The shared KMP module owns UI state, business rules, use cases, domain models, repository contracts, and most data orchestration. Android and iOS provide implementations for native capabilities through interfaces.

---

## 3. Module Layout

```text
image-categorizer/
├── aicore/                         # Android library: Android AICore / Gemini Nano semantic extraction (Gradle :aicore)
│   └── src/
│       ├── androidMain/            # AICore client, prompts, JSON parser, fallback extractor
│       └── androidUnitTest/        # Unit tests
├── shared/                         # KMP module: shared Compose UI + domain + data (Gradle :shared)
│   └── src/
│       ├── commonMain/             # UI, models, classifiers, OcrMeaningProvider expect declaration
│       ├── androidMain/            # MediaStore, ML Kit OCR, TFLite, OcrMeaningProvider actual -> :aicore
│       ├── iosMain/                # PhotoKit actuals, Vision OCR, MainViewController, Shared.framework
│       └── commonTest/             # Pure unit tests
├── androidApp/                     # Android launcher (Gradle :androidApp)
│   └── src/
│       ├── androidMain/            # MainActivity, AndroidManifest.xml (Kotlin 2.4 v2 layout)
│       └── main/assets/            # MobileNet .tflite model + labels
├── iosApp/                         # Xcode host; build phase runs :shared:embedAndSignAppleFrameworkForXcode
└── gradle/libs.versions.toml       # Version catalog
```

`shared` holds all shared layers (there is no separate `composeApp` module). Split domain, data, and feature modules only when build time or team size makes it valuable.

### Current implementation (verified 2026-09-29)

The code features an on-device OCR and semantic inference pipeline backed by SQLite persistence:

```text
App (commonMain/App.kt, manual wiring via remember { })
    -> ScreenshotRepository.cached()   show last results from SQLite at launch
    -> ScreenshotRepository.scan()
        -> MediaScanner.listScreenshots()  metadata only; Android: name/path tokens | iOS: screenshot subtype flag
        -> diff against stored (id, modified_millis): delete missing rows, pick new/changed
        -> MediaScanner.analyze(pending)   OCR: ML Kit (Android) | Vision (iOS)
                                           [Android/KMP] OcrMeaningProvider -> :aicore (Gemini Nano via Android AICore)
                                                         Extracts proper meaning, synthesized message, entities
                                                         Falls back to fast rule-based engine if AICore unsupported/downloading
                                           [Android] TFLite labels -> sub_category fallback
        -> upsert each row as it completes (storing ocr_text, description, sub_category)
        -> load rows; category = ScreenshotCategorizer.categorize(ocr_text, name) on read
    -> ScanOutcome.Success(List<ImageRecord>) held in Compose state
    -> HomeScreen                    category chips, sub-category chips, lazy grid with AI meaning digests
MediaScanner.watchChanges()          ContentObserver | PHPhotoLibraryChangeObserver -> rescan
ThumbnailLoader                      expect/actual thumbnail decode
```

| Target (this doc) | Current code |
|---|---|
| `Screenshot`, `ScreenshotCategory`, `ProcessingStatus` | `ImageRecord`, `ImageCategory` (`model/Models.kt`) |
| `PhotoLibraryGateway` + `OcrEngine` + `ScreenshotClassifier` | `MediaScanner` does all three on Android (`MediaScanner.android.kt`) |
| `OcrMeaningProvider` | `expect`/`actual` in `shared`; Android delegates to `:aicore`, iOS runs local heuristics |
| Semantic Meaning Engine | `:aicore` module (`com.google.ai.edge.aicore:aicore:0.0.1-exp01` + fallback) |
| `ScreenshotRepository` + SQLDelight + FTS | `ScreenshotRepository` + SQLDelight over a `ScreenshotSource` interface; FTS4 search via `search()` |
| ViewModel + `StateFlow<UiState>` | `remember { mutableStateOf }` in `App` |
| Koin modules | manual construction, `AndroidApp.context` set by `initAndroid` |
| Feature packages (`screenshots/domain/...`) | layer packages: `model/`, `classify/`, `data/`, `ui/` |

Known gaps: iOS has no image-label model, `TensorFlowClassifier` is an empty expect/actual stub, and `ImageContentAnalyzer` is tested but has no callers. Moving to the target design starts with build order step 2 (section 16): extract `OcrEngine` / image classification out of `MediaScanner.android.kt`.

---

## 4. Source Sets

### commonMain

Contains platform-independent code:

- Compose screens and reusable components.
- ViewModels/state holders and UI state models.
- Domain entities and use cases.
- Repository interfaces.
- Classification rules.
- Database abstractions and SQLDelight queries.
- Ktor client abstractions if optional cloud features are added.
- Kotlinx Serialization models.

### androidMain

Contains Android-specific code:

- `MediaStore` and Android Photo Picker integration.
- ML Kit OCR and image labeling.
- WorkManager scheduling.
- Android Keystore-backed encryption.
- Android-specific permission handling.

### iosMain

Contains iOS-specific code:

- Photos framework (`PHPhotoLibrary`, `PHAsset`) integration.
- Vision OCR (`VNRecognizeTextRequest`).
- BackgroundTasks integration.
- Keychain-backed encryption keys.
- iOS Photos permission handling.

---

## 5. Feature Structure

Organize shared code by feature rather than only by technical layer:

```text
commonMain/kotlin/
├── core/
│   ├── database/
│   ├── model/
│   ├── navigation/
│   ├── util/
│   └── ui/
├── screenshots/
│   ├── domain/
│   │   ├── model/
│   │   ├── repository/
│   │   └── usecase/
│   ├── data/
│   │   ├── mapper/
│   │   └── repository/
│   └── presentation/
│       ├── list/
│       ├── detail/
│       ├── import/
│       └── search/
└── settings/
    ├── domain/
    └── presentation/
```

---

## 6. Domain Layer

The domain layer contains pure Kotlin code and must not depend on Android, iOS, database, OCR, or UI libraries.

```kotlin
@Serializable
data class Screenshot(
    val id: String,
    val assetId: String,
    val localUri: String,
    val createdAtEpochMillis: Long,
    val width: Int,
    val height: Int,
    val ocrText: String,
    val categories: Set<ScreenshotCategory>,
    val confidence: Float?,
    val tags: List<String>,
    val processingStatus: ProcessingStatus
)

enum class ScreenshotCategory {
    RECEIPT, SHOPPING, TRAVEL, WORK, CODE, DOCUMENT,
    CHAT, SOCIAL, FOOD, FINANCE, HEALTH, MEME, UNCATEGORIZED
}

enum class ProcessingStatus {
    PENDING, PROCESSING, COMPLETE, FAILED
}
```

Repository contracts remain platform-neutral:

```kotlin
interface ScreenshotRepository {
    fun observeScreenshots(query: ScreenshotQuery): Flow<List<Screenshot>>
    suspend fun getScreenshot(id: String): Screenshot?
    suspend fun save(screenshot: Screenshot)
    suspend fun updateCategories(id: String, categories: Set<ScreenshotCategory>)
    suspend fun delete(id: String)
}

interface PhotoLibraryGateway {
    suspend fun listCandidateScreenshots(): List<PhotoAsset>
    suspend fun loadImage(assetId: String, maxDimension: Int): ByteArray
}

interface OcrEngine {
    suspend fun recognize(image: ByteArray): OcrResult
}

interface ScreenshotClassifier {
    suspend fun classify(ocrText: String): ClassificationResult
}
```

---

## 7. Data Layer

### Local database

Use SQLDelight for a shared SQLite schema and generated Kotlin APIs across Android and iOS. Keep original image files in the native photo library; save only asset identifiers, thumbnails when necessary, metadata, OCR text, tags, and categories.

```sql
CREATE TABLE screenshot (
    id TEXT NOT NULL PRIMARY KEY,
    asset_id TEXT NOT NULL UNIQUE,
    local_uri TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    width INTEGER NOT NULL,
    height INTEGER NOT NULL,
    ocr_text TEXT NOT NULL DEFAULT '',
    categories_json TEXT NOT NULL DEFAULT '[]',
    confidence REAL,
    tags_json TEXT NOT NULL DEFAULT '[]',
    processing_status TEXT NOT NULL,
    processed_at INTEGER
);

CREATE VIRTUAL TABLE screenshot_fts USING fts4(
    ocr_text,
    tags,
    categories
);
```

Use FTS4, not FTS5: Android's framework SQLite does not ship FTS5 (verified on API 36), and bundling SQLite is not worth it for this. Key the FTS table by `screenshot.rowid` and keep it in sync with triggers. Replace rows with delete + insert, because `INSERT OR REPLACE` skips delete triggers and `ON CONFLICT DO UPDATE` needs SQLite 3.24 (Android 11+). The implemented schema is `shared/src/commonMain/sqldelight/com/agy/imagecategorizer/db/Screenshot.sq`.

### Classification strategy

Use a layered classifier:

1. Run deterministic rules for strong signals such as receipt totals, booking references, URLs, or code syntax.
2. Use a local model only when rule confidence is low.
3. Store confidence and allow users to override categories.
4. Treat user correction as a local preference rule before using it as future training data.

```kotlin
class HybridScreenshotClassifier(
    private val ruleClassifier: RuleClassifier,
    private val mlClassifier: MlClassifier
) : ScreenshotClassifier {
    override suspend fun classify(ocrText: String): ClassificationResult {
        val ruleResult = ruleClassifier.classify(ocrText)
        return if (ruleResult.confidence >= 0.85f) ruleResult
        else mlClassifier.classify(ocrText, ruleResult)
    }
}
```

---

## 8. Processing Pipeline

```text
Photo scan or manual import
    -> Candidate screenshot detection
    -> Persist PENDING record
    -> Load downsized image bytes
    -> OCR
    -> Rule/ML classification
    -> Extract tags and actions
    -> Save screenshot metadata and FTS index
    -> Update UI through Flow
```

Processing must be idempotent. Use `assetId` as the unique import key so a repeat scan does not create duplicate records.

```kotlin
class ProcessScreenshotUseCase(
    private val photoLibrary: PhotoLibraryGateway,
    private val repository: ScreenshotRepository,
    private val ocrEngine: OcrEngine,
    private val classifier: ScreenshotClassifier
) {
    suspend operator fun invoke(asset: PhotoAsset) {
        val image = photoLibrary.loadImage(asset.id, maxDimension = 1600)
        val ocr = ocrEngine.recognize(image)
        val classification = classifier.classify(ocr.text)

        repository.save(
            Screenshot(
                id = asset.id,
                assetId = asset.id,
                localUri = asset.uri,
                createdAtEpochMillis = asset.createdAtEpochMillis,
                width = asset.width,
                height = asset.height,
                ocrText = ocr.text,
                categories = classification.categories,
                confidence = classification.confidence,
                tags = classification.tags,
                processingStatus = ProcessingStatus.COMPLETE
            )
        )
    }
}
```

---

## 9. Presentation Layer

Use a state holder or ViewModel per screen. It exposes immutable state and handles UI events; composables only render state and dispatch events.

```kotlin
data class ScreenshotListUiState(
    val query: String = "",
    val selectedCategory: ScreenshotCategory? = null,
    val screenshots: List<Screenshot> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

class ScreenshotListViewModel(
    private val observeScreenshots: ObserveScreenshotsUseCase
) {
    private val _uiState = MutableStateFlow(ScreenshotListUiState())
    val uiState: StateFlow<ScreenshotListUiState> = _uiState.asStateFlow()

    fun onQueryChanged(query: String) {
        _uiState.update { it.copy(query = query) }
    }
}
```

Recommended screens:

- Home: recent screenshots, categories, scan status.
- Search: full-text search, filters, saved queries.
- Detail: image preview, OCR text, category editing, tags, actions.
- Import/Scan: import progress, skipped items, error retry.
- Settings: privacy, storage, scan behavior, language, backup opt-in.

---

## 10. Dependency Injection

Use Koin for straightforward KMP dependency injection, or manual dependency assembly for a small app. Keep common bindings in `commonMain`; bind native implementations in each platform source set.

```kotlin
val commonModule = module {
    single<ScreenshotRepository> { SqlDelightScreenshotRepository(get()) }
    single<ScreenshotClassifier> { HybridScreenshotClassifier(get(), get()) }
    factory { ProcessScreenshotUseCase(get(), get(), get(), get()) }
    factory { ScreenshotListViewModel(get()) }
}
```

Android and iOS modules supply `PhotoLibraryGateway`, `OcrEngine`, secure key storage, and background scheduler bindings.

---

## 11. Platform Boundaries

Use interfaces for native capabilities. Avoid leaking Android `Uri`, `Context`, iOS `UIImage`, or `PHAsset` into `commonMain`.

| Capability | Shared contract | Android implementation | iOS implementation |
|---|---|---|---|
| Photo access | `PhotoLibraryGateway` | MediaStore / Photo Picker | Photos framework |
| OCR | `OcrEngine` | ML Kit | Apple Vision |
| Semantic Meaning | `OcrMeaningProvider` | `:aicore` (Android AICore / Gemini Nano) | Local heuristic summarizer |
| Background processing | `ProcessingScheduler` | WorkManager | BackgroundTasks |
| Secure key storage | `SecureKeyProvider` | Android Keystore | Keychain |
| Permissions | `PermissionGateway` | Runtime permissions | Photos authorization |
| Image bytes | `ImageLoader` | ContentResolver | PHImageManager |

---

## 11.5. On-Device Semantic Extraction with Android AICore

The `:aicore` Android module (`com.agy.imagecategorizer.aicore`) provides on-device generative semantic intelligence using Google Play Services Android AICore and Gemini Nano.

### Architecture:
- **`AiCoreClient`**: Abstraction wrapping `com.google.ai.edge.aicore.GenerativeModel`. Manages lifecycle, preparation, streaming, and maps `GenerativeAIException.ErrorCode` (busy, updates, storage limits) to observable `AiCoreStatus`.
- **`OcrPromptBuilder`**: Strips mobile status bar noise (clocks, battery, radios) and crafts structured prompts requesting strict JSON outputs within Gemini Nano's context window.
- **`OcrMeaningParser`**: Parses LLM markdown fences and raw JSON into strongly-typed `OcrMeaningResult` (headline, message, category, entities, action items, sensitivity flag).
- **`RuleBasedOcrMeaningExtractor`**: High-performance local deterministic fallback engine used when AICore is unsupported (API < 31, non-Pixel/Galaxy flagship), downloading, or busy.
- **`OcrMeaningProvider` (KMP Bridge)**: An `expect`/`actual` bridge in `:shared` that connects `MediaScanner` to `:aicore` on Android, populating `ImageRecord.description` and `subCategory` with synthesized AI summaries.

---

## 12. Background Work

Background execution differs substantially by platform, so treat it as best-effort rather than the only way to index screenshots.

- Android: enqueue unique WorkManager work after import or app launch; constrain heavy processing to charging/idle where appropriate.
- iOS: request `BGProcessingTask` opportunistically, but always support foreground scans because the system controls actual execution time.
- Both: store checkpoints and resume pending work on the next application launch.

```kotlin
interface ProcessingScheduler {
    fun schedulePendingProcessing()
    fun cancelPendingProcessing()
}
```

---

## 13. Security and Privacy

- Keep screenshot bytes in the system photo library unless the user explicitly requests an in-app copy.
- Store OCR text locally and encrypt the database where product requirements justify it.
- Store encryption keys only in Android Keystore or iOS Keychain.
- Exclude sensitive local app data from device backups unless the user opts in.
- Never send screenshot content to a network service without explicit, per-feature consent.
- Redact OCR text from production logs and crash reports.
- On-device Gemini Nano inference is 100% offline and private via Android AICore system service.

---

## 14. Testing Strategy

### Shared tests

- Unit-test classifiers, use cases, mappers, search behavior, and state holders in `commonTest`.
- Test repository behavior against a temporary SQLDelight database.
- Test idempotency: importing the same `assetId` twice must not create duplicates.
- Unit-test `OcrMeaningProvider` contract in `commonTest`.

### Platform & AICore tests

- Android: test MediaStore query behavior, permission outcomes, and WorkManager jobs.
- `:aicore`: test prompt formatting, JSON parser resiliency, rule-based fallback, and mock client orchestration in `androidUnitTest`.
- iOS: test Photos authorization behavior, Vision OCR adapter mapping, and background task registration.

### UI tests

- Test Compose screen states: loading, empty, results, error, and processing progress.
- Use fake repositories and fake OCR/photo gateways for deterministic tests.

---

## 15. Dependency Recommendations

Accessor names match `gradle/libs.versions.toml`.

```kotlin
// aicore/build.gradle.kts
androidMain {
    implementation(libs.google.ai.edge.aicore)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
}

// shared/build.gradle.kts
commonMain {
    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.sqldelight.runtime)
}

androidMain {
    implementation(project(":aicore"))
    implementation(libs.mlkit.text.recognition)
    implementation(libs.tensorflow.lite.task.vision)
    implementation(libs.sqldelight.android.driver)
}
```

iOS dependencies for Photos, Vision, BackgroundTasks, and Keychain are available through Apple frameworks. Add SQLDelight's native driver in `iosMain`.

---

## 16. Build Order

Status as of 2026-09-29 in brackets.

1. Create Compose Multiplatform project and source-set layout. [done]
2. Add shared domain models, repository interfaces, and SQLDelight schema. [done]
3. Implement screenshot list/search UI with real scan and SQLite persistence. [done]
4. Implement Android photo access, ML Kit OCR, and repository wiring. [done]
5. Implement on-device semantic meaning extraction via `:aicore` module (Gemini Nano) with multiplatform bridge in `:shared`. [done]
6. Implement iOS Photos access, Vision OCR, and repository wiring. [done, not run on a device yet]
7. Add background scheduling and robust retry/checkpoint handling per platform.
8. Add category correction, full-text search, encryption, and automated tests. [correction, FTS4 search and tests done; no encryption]
9. Add optional on-device semantic-search embeddings after the MVP is stable.
