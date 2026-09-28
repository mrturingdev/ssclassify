# Screenshot Categorizer App - Plan

## 1. Product Vision

Build a privacy-first, on-device screenshot organizer that:

- Automatically detects and imports screenshots.
- Extracts text using OCR.
- Classifies screenshots into meaningful categories.
- Makes them instantly searchable by text and semantics.
- Works offline by default; cloud features are optional and explicit.

Target users: power users, developers, students, and professionals who accumulate hundreds of screenshots and need to find them later.

---

## 2. Core Features (MVP)

### 2.1 Screenshot Import & Detection

- Scan device gallery for screenshots.
- Detect likely screenshots using:
  - Filename patterns (`Screenshot_`, `Screen Shot`, localized variants).
  - Aspect ratio and resolution heuristics.
  - Optional metadata hints.
- Allow manual "Add to app" selection.

### 2.2 OCR & Text Extraction

- Extract visible text from each screenshot.
- Support multiple languages (English + Nepali at minimum).
- Store extracted text in local database.
- Index text for full-text search (FTS).

### 2.3 Classification

- Assign one or more categories:
  - Receipt, Shopping, Travel, Work, Code, Document, Chat, Social, Food, Finance, Health, Meme, Uncategorized.
- Use hybrid approach:
  - Rule-based keyword detection.
  - Lightweight ML model for ambiguous cases.
- Allow user to correct category; use as training signal.

### 2.4 Search & Browse

- Keyword search over OCR text.
- Filter by category.
- Sort by date, category, or recency.
- Future: semantic search ("show database migration screenshots").

### 2.5 Privacy & Security

- All processing on-device by default.
- Encrypted local database.
- No cloud upload unless user explicitly enables backup/AI features.
- Optional "private folder" for sensitive screenshots.

---

## 3. Technical Stack

Versions are the source of truth in `gradle/libs.versions.toml`; this table was verified against it on 2026-09-27.

| Concern | Target | Current (in repo) |
|---|---|---|
| Language / platform | Kotlin Multiplatform (Android + iOS) | Kotlin 2.4.0, targets `androidTarget`, `iosX64`, `iosArm64`, `iosSimulatorArm64` |
| UI | Compose Multiplatform + Material 3 | CMP 1.10.3 (pinned: 1.11 dropped `iosX64`, needed on Intel Macs), Material3 1.9.0, icons-extended 1.7.3 |
| Build | Gradle + AGP | Gradle 9.0 wrapper, AGP 8.6.1, JDK 21, compileSdk/targetSdk 35, minSdk 24, iOS 15.0 |
| Concurrency | Kotlinx Coroutines / Flow | Coroutines 1.10.2 |
| Local DB | SQLDelight + SQLite (FTS4) | SQLDelight 2.4.0 (`screenshots.db`, `commonMain/sqldelight/.../Screenshot.sq`). Incremental scans. Full-text search uses **FTS4**, not FTS5: Android's framework SQLite has no FTS5 (confirmed on API 36), and FTS4 ships on both platforms without bundling SQLite |
| OCR (Android) | ML Kit Text Recognition | ML Kit `text-recognition` 16.0.1 (Latin script only; Nepali needs `text-recognition-devanagari`) |
| OCR (iOS) | Apple Vision `VNRecognizeTextRequest` | Vision, accurate level (`iosMain/.../classify/OcrHelper.kt`) |
| Image ML (Android) | Optional on-device model | TFLite Task Vision 0.4.4 + MobileNet v1 quant (`androidApp/src/main/assets`) for object labels on screenshots |
| Image ML (iOS) | - | **Not yet** (`TensorFlowClassifier` is an empty expect/actual stub) |
| Classification | Hybrid rules + optional ML | Screenshots only. `ScreenshotCategorizer`: keyword rules over OCR text + file name -> content category (section 2.3, minus Meme). Android adds MobileNet labels / OCR keywords as a free-form `subCategory`. `ImageContentAnalyzer` (pixel heuristics) exists and is tested but is not wired in |
| Background work | WorkManager / BGProcessingTask | **Not yet** - foreground scan, auto-rescan via `ContentObserver` / `PHPhotoLibraryChangeObserver` |
| DI | Koin or manual assembly | Manual (`remember { MediaScanner() }`, Android `Context` via `initAndroid`) |
| Serialization | Kotlinx Serialization (JSON) | **Not yet** (not needed until persistence lands) |

---

## 4. Architecture Overview

See `architecture.md` for full details. High-level summary:

- **Domain layer (commonMain):**
  - `Screenshot`, `ScreenshotCategory`, `ProcessingStatus`
  - `ScreenshotRepository`, `PhotoLibraryGateway`, `OcrEngine`, `ScreenshotClassifier`
  - Use cases: `ProcessScreenshotUseCase`, `ObserveScreenshotsUseCase`, `SearchScreenshotsUseCase`
- **Data layer (commonMain + platform):**
  - SQLDelight schema and queries
  - Repository implementations using platform photo/OCR/DB
- **Presentation layer (commonMain):**
  - Compose screens: Home, Search, Detail, Import/Scan, Settings
  - State holders / ViewModels exposing `StateFlow<UiState>`
- **Platform implementations:**
  - `androidMain`: MediaStore/Photo Picker, ML Kit, WorkManager, Keystore
  - `iosMain`: Photos framework, Vision OCR, BackgroundTasks, Keychain

---

### Current status (verified 2026-09-27)

- `:androidApp:assembleDebug` builds; `:shared:testDebugUnitTest` passes (21 tests: `ScreenshotCategorizerTest`, `ImageContentAnalyzerTest`, `ScreenshotRepositoryTest`). `:shared:compileKotlinIosX64` builds; iOS tests cannot run on this Intel Mac (the iOS 26 simulator rejects x86_64).
- Phase 1 (Android) is partially done: gallery scan, categorization, ML Kit OCR and grid UI with category and sub-category chips work. Persistence with incremental rescans, full-text search, a detail dialog and category correction are done. Phase 1 acceptance criteria are met.
- Phase 2 (iOS) is partially done: PhotoKit scan filtered by the screenshot flag, Vision OCR, thumbnails and shared UI work. Missing: everything missing on Android.
- Only screenshots are scanned (Android: name/path tokens such as `screenshot`, `captura`, `bildschirmfoto`; iOS: `PHAssetMediaSubtypePhotoScreenshot`). Other gallery images are ignored. Categories are the content categories from section 2.3 (Meme is omitted since text rules cannot detect it); no match -> Uncategorized.
- Scans are incremental: OCR (and TFLite on Android) only runs for screenshots that are new or whose modified timestamp changed; deleted screenshots are dropped. Cached results show at launch while a background rescan catches up. Each result is saved as soon as it is analyzed, so an interrupted scan keeps its progress.

---

## 5. Implementation Phases

### Phase 0 - Setup & Research (1–2 weeks)

- Finalize feature list and UX flows.
- Research:
  - Android photo permissions and scoped storage.
  - iOS Photos authorization and background processing limits.
  - ML Kit vs Vision accuracy for mixed English/Nepali text.
- Decide:
  - On-device vs cloud classification strategy.
  - Minimum supported OS versions.

Deliverables:
- Technical spec doc.
- Basic UI wireframes.
- Risk register (privacy, performance, OS restrictions).

---

### Phase 1 - Core Pipeline (Android) (3–4 weeks)

**Goals:** Import → OCR → Classify → Store → Search.

Tasks:

- Set up Compose Multiplatform project and source sets.
- Implement photo picker and screenshot scanning on Android.
- Integrate ML Kit Text Recognition.
- Implement rule-based classifier.
- Set up SQLDelight schema and repository.
- Build basic list + detail screens in Compose.
- Implement keyword search.

Acceptance criteria:

- App can import screenshots from gallery.
- OCR text is extracted and stored.
- Basic categories are assigned.
- User can search by keyword and filter by category.

---

### Phase 2 - Core Pipeline (iOS) (3–4 weeks)

Parallel or sequential to Phase 1.

Tasks:

- Implement Photos access and screenshot filtering on iOS.
- Integrate Vision OCR.
- Implement same category logic as Android.
- Set up SQLDelight native driver and repository.
- Build list + detail + search screens in Compose.

Acceptance criteria:

- Same core flow as Android MVP.
- Consistent categories and behavior.

---

### Phase 3 - UX Polish & Performance (2–3 weeks)

- Optimize OCR and classification performance.
- Add progress indicators and error states.
- Improve category accuracy with better rules / small ML model.
- Add:
  - Multi-select actions.
  - Bulk delete / archive.
  - "Mark as not a screenshot".
- Add basic analytics (privacy-respecting, opt-in).

---

### Phase 4 - Advanced Features (optional, 4+ weeks)

- Semantic search with on-device embeddings.
- Action extraction:
  - Detect URLs, phone numbers, flight numbers, coupon codes.
- Duplicate detection & clustering.
- Expiry reminders for tickets, bookings, coupons.
- Sensitive-content detector (OTP, cards, passwords).
- Nepali-specific optimizations:
  - Better Devanagari OCR tuning.
  - Localized category names.

---

## 6. Privacy & Security Plan

- Default: all processing on-device.
- Encrypt local database (SQLCipher or platform mechanisms).
- Do not log OCR text or screenshot paths in analytics.
- If cloud features are added later:
  - Explicit opt-in.
  - Clear description of what is uploaded and why.
  - Option to delete all server data.

---

## 7. Risks & Mitigations

| Risk                                    | Impact | Mitigation                                           |
|-----------------------------------------|--------|------------------------------------------------------|
| OS restricts background photo scanning  | High   | Use on-demand scan + user-triggered refresh          |
| OCR accuracy poor for mixed scripts     | Medium | Tune language settings; allow manual corrections     |
| Performance/battery drain               | Medium | Throttle processing; use WorkManager / BG tasks well |
| Users distrust "screenshot reader" app  | High   | Strong privacy messaging; offline-first design       |
| App Store / Play policy issues          | Medium | Careful permission usage; transparent privacy policy |

---

## 8. Success Metrics

- % of screenshots successfully categorized.
- Search success rate (user finds desired screenshot).
- Time-to-first-result for search.
- Retention (7-day, 30-day).
- Opt-in rate for any optional cloud features.

---

## 9. Open Questions

- Should we support desktop (macOS/Windows) in future?
- Do we offer cloud backup/sync as a paid feature?
- How aggressive should automatic scanning be vs manual trigger?
- Do we support user-defined custom categories in v1 or later?

---

## 10. Next Steps

1. Measure category accuracy on a real screenshot set and tune `ScreenshotCategorizer` keywords.
2. Extract `OcrEngine` / image classifier out of `MediaScanner.android.kt` behind shared interfaces.
3. Use corrections as a signal (plan 2.3): e.g. suggest keywords from corrected screenshots' OCR text. Today they only pin that one screenshot.
4. Run the iOS build on a device or Apple Silicon Mac (Vision OCR, PhotoKit, SQLite are compiled but never executed).
5. Set up version control and CI for Android and iOS (the project is not a git repository yet).
