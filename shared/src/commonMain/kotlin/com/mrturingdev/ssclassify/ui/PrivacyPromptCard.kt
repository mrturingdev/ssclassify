package com.mrturingdev.ssclassify.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mrturingdev.ssclassify.telemetry.PrivacyPrompt

/** The one-time telemetry card on Home; [onAnswer] gets whether to keep sharing quality stats. */
@Composable
internal fun PrivacyPromptCard(prompt: PrivacyPrompt, onAnswer: (shareQualityStats: Boolean) -> Unit) {
    val (title, body, decline, accept) = when (prompt) {
        PrivacyPrompt.NewInstall -> listOf(
            "Anonymous stats help improve categorization",
            "The app shares counts like scan time and category corrections, and crash reports. " +
                "Never your screenshots or their text. Change anytime in Settings.",
            "Turn off stats",
            "OK",
        )
        PrivacyPrompt.UpdateNotice -> listOf(
            "This update adds anonymous crash reports and stats",
            "Counts like scan time and category corrections help improve categorization. " +
                "They never include your screenshots or their text. Change anytime in Settings.",
            "Turn off stats",
            "OK",
        )
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.Rounded.Shield,
                contentDescription = null,
                modifier = Modifier.size(22.dp).padding(top = 2.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { onAnswer(false) }) { Text(decline) }
            FilledTonalButton(onClick = { onAnswer(true) }) { Text(accept) }
        }
    }
}
