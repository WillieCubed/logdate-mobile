package app.logdate.wear.presentation.health

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText

@Composable
fun HealthDashboardScreen(viewModel: HealthDashboardViewModel) {
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
            item(key = "title") {
                Text(
                    text = "Health Today",
                    style = MaterialTheme.typography.titleSmall,
                    textAlign = TextAlign.Center,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                )
            }

            // Heart rate card
            item(key = "heartRate") {
                Card(
                    onClick = {},
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = Icons.Default.FavoriteBorder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                    val hrText = uiState.currentHeartRate?.let { "$it bpm" } ?: "No reading"
                    Text(
                        text = hrText,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "Heart rate",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Step count card
            item(key = "steps") {
                Card(
                    onClick = {},
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = Icons.Default.DirectionsWalk,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    val stepsText =
                        uiState.currentStepCount?.let {
                            "%,d steps".format(it)
                        } ?: "No reading"
                    Text(
                        text = stepsText,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "Today",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Correlation insight
            if (uiState.correlationInsight.isNotEmpty()) {
                item(key = "insight") {
                    Card(
                        onClick = {},
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = uiState.correlationInsight,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}
