package app.logdate.wear.presentation.home

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicNone
import androidx.compose.material.icons.filled.Mood
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.ViewTimeline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.OutlinedButton
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun WearHomeScreen(
    onNavigateToWalkieTalkie: () -> Unit,
    onNavigateToVoiceNote: () -> Unit,
    onNavigateToMoodCheckIn: () -> Unit,
    onNavigateToQuickText: () -> Unit,
    onNavigateToTimeline: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: WearHomeViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberScalingLazyListState()

    ScreenScaffold(
        timeText = { TimeText() },
        scrollState = listState,
    ) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
        ) {
            // Greeting card
            item {
                Text(
                    text = uiState.greeting,
                    style = MaterialTheme.typography.titleSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
                )
            }
            item {
                Text(
                    text = uiState.entryCountLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                )
            }

            // Walkie-Talkie hero chip
            item {
                Button(
                    onClick = onNavigateToWalkieTalkie,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Walkie-Talkie") },
                    icon = { Icon(Icons.Default.Mic, contentDescription = null) },
                )
            }

            // Voice Note
            item {
                FilledTonalButton(
                    onClick = onNavigateToVoiceNote,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Voice Note") },
                    icon = { Icon(Icons.Default.MicNone, contentDescription = null) },
                )
            }

            // Mood Check-in
            item {
                FilledTonalButton(
                    onClick = onNavigateToMoodCheckIn,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Mood Check-in") },
                    icon = { Icon(Icons.Default.Mood, contentDescription = null) },
                )
            }

            // Quick Text
            item {
                OutlinedButton(
                    onClick = onNavigateToQuickText,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Quick Text") },
                    icon = { Icon(Icons.Default.TextFields, contentDescription = null) },
                )
            }

            // Timeline
            item {
                OutlinedButton(
                    onClick = onNavigateToTimeline,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Timeline") },
                    icon = { Icon(Icons.Default.ViewTimeline, contentDescription = null) },
                )
            }

            // Settings
            item {
                OutlinedButton(
                    onClick = onNavigateToSettings,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Settings") },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    colors = ButtonDefaults.outlinedButtonColors(),
                )
            }
        }
    }
}
