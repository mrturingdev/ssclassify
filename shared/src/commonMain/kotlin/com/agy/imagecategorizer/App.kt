package com.agy.imagecategorizer

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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.agy.imagecategorizer.data.MediaScanner
import com.agy.imagecategorizer.data.ScanOutcome
import com.agy.imagecategorizer.data.ScreenshotRepository
import com.agy.imagecategorizer.data.ThumbnailLoader
import com.agy.imagecategorizer.data.createSqlDriver
import com.agy.imagecategorizer.model.ImageRecord
import com.agy.imagecategorizer.ui.HomeScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Tokens from .design/DESIGN.md (brunopetrovic/apple)
private val ActionBlue = Color(0xFF0066CC)
private val LightCanvas = Color(0xFFFFFFFF)
private val LightParchment = Color(0xFFF5F5F7)
private val LightInk = Color(0xFF1D1D1F)

private val DarkCanvas = Color(0xFF121212)
private val DarkTile = Color(0xFF1E1E20)
private val DarkInk = Color(0xFFF5F5F7)

@Composable
fun App(isPermissionGranted: Boolean = true) {
    val darkTheme = isSystemInDarkTheme()
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = ActionBlue,
            background = DarkCanvas,
            surface = DarkCanvas,
            surfaceVariant = DarkTile,
            onSurface = DarkInk,
            onBackground = DarkInk,
        )
    } else {
        lightColorScheme(
            primary = ActionBlue,
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
                    outcome = if (isPermissionGranted) repository.scan() else ScanOutcome.Failure(PERMISSION_DENIED)
                } while (rescanRequested)
                scanning = false
            }
        }

        // Show what was analyzed last time right away, then catch up incrementally.
        LaunchedEffect(Unit) {
            if (!isPermissionGranted) return@LaunchedEffect
            val cached = repository.cached()
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

        HomeScreen(
            modifier = Modifier.fillMaxSize(),
            scanning = scanning,
            outcome = outcome,
            scanner = scanner,
            thumbnailLoader = loader,
            onScan = ::triggerScan,
            query = query,
            onQueryChange = { query = it },
            searchResults = searchResults,
            onCategoryChange = { id, category ->
                scope.launch {
                    repository.setCategory(id, category)
                    // Reload so grid, chip counts and (via the search effect) results reflect it.
                    outcome = ScanOutcome.Success(repository.cached())
                }
            },
        )
    }
}

internal const val PERMISSION_DENIED = "permission_denied"
