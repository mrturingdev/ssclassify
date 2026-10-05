package com.mrturingdev.ssclassify.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mrturingdev.ssclassify.settings.appVersion
import com.mrturingdev.ssclassify.settings.openStoreReview
import com.mrturingdev.ssclassify.settings.storeName
import com.mrturingdev.ssclassify.settings.widgetSteps
import com.mrturingdev.ssclassify.settings.ThemeMode

/** Ratings at or above this go straight to the store; lower ones ask for a written review. */
private const val HAPPY_RATING = 4

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    /** Asks the launcher to pin the widget; null when it cannot, so manual steps show instead. */
    pinWidget: (() -> Unit)?,
    /** How many screenshots are analyzed; the re-analyze button is off when there are none. */
    libraryCount: Int,
    onReanalyzeAll: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text("Settings", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                AppWordmark(
                    modifier = Modifier.alignByBaseline(),
                    fontSize = MaterialTheme.typography.bodySmall.fontSize,
                    accentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    " · Version ${appVersion()}",
                    modifier = Modifier.alignByBaseline(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ThemeSection(themeMode, onThemeModeChange)
            WidgetSection(pinWidget)
            LibrarySection(libraryCount, onReanalyzeAll)
            RatingSection()
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeSection(themeMode: ThemeMode, onThemeModeChange: (ThemeMode) -> Unit) {
    SettingsCard("Theme") {
        val modes = ThemeMode.entries
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            modes.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = mode == themeMode,
                    onClick = { onThemeModeChange(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                ) {
                    Text(mode.name)
                }
            }
        }
    }
}

@Composable
private fun WidgetSection(pinWidget: (() -> Unit)?) {
    SettingsCard("Home screen widget") {
        Text(
            "See your most recent screenshots right on your home screen.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (pinWidget != null) {
            Button(onClick = pinWidget, shape = RoundedCornerShape(20.dp)) {
                Text("Add widget")
            }
        } else {
            widgetSteps.forEachIndexed { index, step ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${index + 1}.", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                    Text(step, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun LibrarySection(libraryCount: Int, onReanalyzeAll: () -> Unit) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    SettingsCard("Library") {
        Text(
            "Read every screenshot again with the latest text recognition, titles and categories.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = { confirming = true },
            enabled = libraryCount > 0,
            shape = RoundedCornerShape(20.dp),
        ) {
            Text("Re-analyze all")
        }
    }
    if (confirming) {
        val noun = if (libraryCount == 1) "screenshot" else "screenshots"
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("Re-analyze $libraryCount $noun?") },
            text = {
                Text(
                    "This re-runs text recognition on every screenshot and may take a few minutes. " +
                        "Your category corrections are kept.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirming = false
                    onReanalyzeAll()
                }) { Text("Re-analyze") }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun RatingSection() {
    var stars by rememberSaveable { mutableIntStateOf(0) }
    SettingsCard("Rate S.S. Classify") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            for (value in 1..5) {
                IconButton(onClick = { stars = value }) {
                    Icon(
                        if (value <= stars) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                        contentDescription = "$value star${if (value == 1) "" else "s"}",
                        modifier = Modifier.size(32.dp),
                        tint = if (value <= stars) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (stars > 0) {
            val happy = stars >= HAPPY_RATING
            Text(
                if (happy) {
                    "Thank you! A rating on ${storeName} helps others find the app."
                } else {
                    "Sorry it is not there yet. A review on ${storeName} tells us what to fix."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = { openStoreReview(writeReview = !happy) },
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text(if (happy) "Rate on ${storeName}" else "Write a review")
            }
        }
    }
}
