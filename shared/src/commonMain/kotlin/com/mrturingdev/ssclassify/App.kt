package com.mrturingdev.ssclassify

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.graphics.Color
import com.mrturingdev.ssclassify.data.MediaScanner
import com.mrturingdev.ssclassify.data.ScanOutcome
import com.mrturingdev.ssclassify.data.ScreenshotRepository
import com.mrturingdev.ssclassify.data.ThumbnailLoader
import com.mrturingdev.ssclassify.data.createSqlDriver
import com.mrturingdev.ssclassify.model.ImageRecord
import com.mrturingdev.ssclassify.settings.ApplySystemTheme
import com.mrturingdev.ssclassify.settings.ThemeMode
import com.mrturingdev.ssclassify.settings.loadThemeMode
import com.mrturingdev.ssclassify.settings.saveThemeMode
import com.mrturingdev.ssclassify.telemetry.CountBucket
import com.mrturingdev.ssclassify.telemetry.DurationBucket
import com.mrturingdev.ssclassify.telemetry.NoopTelemetrySink
import com.mrturingdev.ssclassify.telemetry.PrivacyPrompt
import com.mrturingdev.ssclassify.telemetry.QualityEvent
import com.mrturingdev.ssclassify.telemetry.SettingsTelemetryStore
import com.mrturingdev.ssclassify.telemetry.ShareBucket
import com.mrturingdev.ssclassify.telemetry.Telemetry
import com.mrturingdev.ssclassify.ui.HomeScreen
import com.mrturingdev.ssclassify.ui.SettingsScreen
import com.mrturingdev.ssclassify.widget.DeepLinks
import com.mrturingdev.ssclassify.widget.publishWidgetSnapshot
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.TimeSource

// Tokens from .design/DESIGN.md (brunopetrovic/apple)
private val ActionBlue = Color(0xFF0066CC)
private val LightCanvas = Color(0xFFFFFFFF)
private val LightParchment = Color(0xFFF5F5F7)
private val LightInk = Color(0xFF1D1D1F)

private val DarkCanvas = Color(0xFF121212)
private val DarkTile = Color(0xFF1E1E20)
private val DarkInk = Color(0xFFF5F5F7)

// Selection tints of Action Blue, so chips and toggles never fall back to Material's lavender.
private val LightSelection = Color(0xFFE3EEFB)
private val LightOnSelection = Color(0xFF003A75)
private val DarkSelection = Color(0xFF1B3A5E)
private val DarkOnSelection = Color(0xFFD6E6FF)

/**
 * @param pinWidget asks the launcher to pin the home-screen widget; null where the
 * app cannot (iOS, older Android launchers), so Settings shows manual steps instead.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun App(
    isPermissionGranted: Boolean = true,
    pinWidget: (() -> Unit)? = null,
    /** Debug builds only: shares a screenshot's OCR lines as calibration fixture JSON; null hides the action. */
    exportOcrLines: ((id: String) -> Unit)? = null,
    /**
     * Started by the platform entry point, before any UI, so early crashes are
     * reported; null (previews, tests) sends nothing.
     */
    telemetry: Telemetry? = null,
) {
    var themeMode by remember { mutableStateOf(loadThemeMode()) }
    val darkTheme = when (themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    ApplySystemTheme(themeMode, darkTheme)
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = ActionBlue,
            onPrimary = Color.White,
            secondaryContainer = DarkSelection,
            onSecondaryContainer = DarkOnSelection,
            background = DarkCanvas,
            surface = DarkCanvas,
            surfaceVariant = DarkTile,
            onSurface = DarkInk,
            onBackground = DarkInk,
        )
    } else {
        lightColorScheme(
            primary = ActionBlue,
            onPrimary = Color.White,
            secondaryContainer = LightSelection,
            onSecondaryContainer = LightOnSelection,
            background = LightCanvas,
            surface = LightCanvas,
            surfaceVariant = LightParchment,
            onSurface = LightInk,
            onBackground = LightInk,
        )
    }

    MaterialTheme(colorScheme = colorScheme) {
        val scanner = remember { MediaScanner() }
        val repository = remember { ScreenshotRepository(scanner, createSqlDriver()) }
        val loader = remember { ThumbnailLoader() }
        val scope = rememberCoroutineScope()

        val telemetry = telemetry ?: remember { Telemetry(NoopTelemetrySink, SettingsTelemetryStore()) }
        var telemetryConsent by remember { mutableStateOf(telemetry.consent) }
        var privacyPrompt by remember { mutableStateOf<PrivacyPrompt?>(null) }

        var scanning by remember { mutableStateOf(false) }
        var rescanRequested by remember { mutableStateOf(false) }
        var outcome by remember { mutableStateOf<ScanOutcome?>(null) }
        var query by rememberSaveable { mutableStateOf("") }
        var searchResults by remember { mutableStateOf<List<ImageRecord>?>(null) }

        fun triggerScan() {
            if (scanning) {
                rescanRequested = true
                return
            }
            scanning = true
            scope.launch {
                do {
                    rescanRequested = false
                    val started = TimeSource.Monotonic.markNow()
                    val result = if (isPermissionGranted) repository.scan() else ScanOutcome.Failure(PERMISSION_DENIED)
                    outcome = result
                    // Only scans that analyzed something, so this is never a per-launch ping.
                    (result as? ScanOutcome.Success)?.stats?.let { stats ->
                        telemetry.record(
                            QualityEvent.ScanFinished(
                                screenshots = CountBucket.of(stats.analyzed),
                                duration = DurationBucket.of(started.elapsedNow().inWholeMilliseconds),
                                ocrFailures = CountBucket.of(stats.ocrFailures),
                                aiCore = scanner.aiCoreState,
                                untitled = ShareBucket.of(stats.untitled, stats.analyzed),
                            ),
                        )
                    }
                } while (rescanRequested)
                scanning = false
            }
        }

        // Show what was analyzed last time right away, then catch up incrementally.
        LaunchedEffect(Unit) {
            if (!isPermissionGranted) return@LaunchedEffect
            val cached = repository.cached()
            privacyPrompt = telemetry.pendingPrompt(libraryIsEmpty = cached.isEmpty())
            if (cached.isNotEmpty()) {
                outcome = ScanOutcome.Success(cached)
                triggerScan()
            }
        }

        // Re-runs when the query changes or a scan lands; a newer keystroke cancels the pending search.
        LaunchedEffect(query, outcome) {
            if (query.isBlank()) {
                searchResults = null
                return@LaunchedEffect
            }
            delay(150)
            searchResults = repository.search(query)
        }

        // Widgets read the same analyzed library: refresh them whenever it changes.
        LaunchedEffect(outcome) {
            (outcome as? ScanOutcome.Success)?.let { publishWidgetSnapshot(it.images) }
        }

        var showSettings by rememberSaveable { mutableStateOf(false) }
        val deepLinkId = DeepLinks.pendingScreenshotId
        LaunchedEffect(deepLinkId) {
            if (deepLinkId != null) showSettings = false
        }
        // Keeps the home screen's saved state (category, scroll) while Settings is open.
        val screens = rememberSaveableStateHolder()
        if (showSettings) {
            // Deprecated for NavigationEventHandler, which needs activity 1.12+ on Android;
            // this one still routes through the OnBackPressedDispatcher of activity 1.10.
            @Suppress("DEPRECATION")
            BackHandler { showSettings = false }
            SettingsScreen(
                themeMode = themeMode,
                onThemeModeChange = {
                    themeMode = it
                    saveThemeMode(it)
                },
                pinWidget = pinWidget,
                libraryCount = (outcome as? ScanOutcome.Success)?.images?.size ?: 0,
                consent = telemetryConsent,
                onConsentChange = {
                    telemetry.updateConsent(it)
                    telemetryConsent = it
                    privacyPrompt = null
                },
                onReanalyzeAll = {
                    telemetry.record(QualityEvent.ReanalyzeAllUsed)
                    scope.launch {
                        repository.markAllForReanalysis()
                        // Back home, where the usual scan progress shows.
                        showSettings = false
                        triggerScan()
                    }
                },
                onBack = { showSettings = false },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            screens.SaveableStateProvider("home") {
                HomeScreen(
                    modifier = Modifier.fillMaxSize(),
                    onOpenSettings = { showSettings = true },
                    openDetailId = deepLinkId,
                    onDetailOpened = { DeepLinks.pendingScreenshotId = null },
                    scanning = scanning,
                    outcome = outcome,
                    scanner = scanner,
                    thumbnailLoader = loader,
                    onScan = ::triggerScan,
                    onExportOcrLines = exportOcrLines,
                    // New installs see the card once their first scan has found screenshots.
                    privacyPrompt = privacyPrompt.takeIf {
                        it == PrivacyPrompt.UpdateNotice || (outcome as? ScanOutcome.Success)?.images?.isNotEmpty() == true
                    },
                    onPrivacyAnswer = { share ->
                        telemetry.answerPrompt(share)
                        telemetryConsent = telemetry.consent
                        privacyPrompt = null
                    },
                    query = query,
                    onQueryChange = { query = it },
                    searchResults = searchResults,
                    onCategoryChange = { id, category ->
                        scope.launch {
                            val before = (outcome as? ScanOutcome.Success)?.images?.firstOrNull { it.id == id }?.category
                            // A reset (null) isn't a correction.
                            if (category != null && before != null && before != category) {
                                telemetry.record(QualityEvent.CategoryCorrected(from = before, to = category))
                            }
                            repository.setCategory(id, category)
                            // Reload so grid, chip counts and (via the search effect) results reflect it.
                            outcome = ScanOutcome.Success(repository.cached())
                        }
                    },
                    onDeleteScreenshots = { ids ->
                        scope.launch {
                            repository.deleteScreenshots(ids)
                            outcome = ScanOutcome.Success(repository.cached())
                        }
                    },
                )
            }
        }
    }
}

internal const val PERMISSION_DENIED = "permission_denied"
