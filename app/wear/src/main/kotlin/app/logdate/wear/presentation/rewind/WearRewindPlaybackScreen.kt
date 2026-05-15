package app.logdate.wear.presentation.rewind

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import app.logdate.shared.model.RewindContent

@Composable
fun WearRewindPlaybackScreen(
    viewModel: WearRewindViewModel,
    onExit: () -> Unit,
) {
    val playback by viewModel.playbackState.collectAsState()
    val state = playback

    if (state == null) {
        onExit()
        return
    }

    WearRewindPlaybackContent(
        state = state,
        onAdvance = { viewModel.advance() },
        onPrevious = { viewModel.previous() },
        onExit = {
            viewModel.exitPlayback()
            onExit()
        },
    )
}

@Composable
internal fun WearRewindPlaybackContent(
    state: WearRewindPlaybackState,
    onAdvance: () -> Unit = {},
    onPrevious: () -> Unit = {},
    onExit: () -> Unit = {},
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
    ) {
        // Progress bar at top
        ProgressSegments(
            current = state.currentIndex,
            total = state.totalPanels,
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 24.dp, start = 16.dp, end = 16.dp),
        )

        // Panel content
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 40.dp),
            contentAlignment = Alignment.Center,
        ) {
            val panel = state.currentPanel
            if (panel != null) {
                PanelContent(panel = panel)
            }
        }

        // Touch zones: left half = previous, right half = advance
        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clickable { onPrevious() },
            )
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clickable {
                            if (state.isLastPanel) onExit() else onAdvance()
                        },
            )
        }
    }
}

@Composable
private fun ProgressSegments(
    current: Int,
    total: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        repeat(total) { index ->
            val color =
                if (index <= current) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceContainer
                }
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .height(2.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(color),
            )
        }
    }
}

@Composable
private fun PanelContent(panel: RewindContent) {
    when (panel) {
        is RewindContent.NarrativeContext -> {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = panel.contextText,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }

        is RewindContent.TextNote -> {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = panel.content.take(120),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        is RewindContent.Transition -> {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = panel.transitionText,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Visual-heavy panels that don't translate to the watch's screen size are
        // filtered out — Image/Video and the structural map/weather/personality/top-list
        // cards live in the phone-side Rewind UI.
        is RewindContent.Image,
        is RewindContent.Video,
        is RewindContent.MapPanel,
        is RewindContent.WeatherPanel,
        is RewindContent.PersonalityCard,
        is RewindContent.TopList,
        -> Unit
    }
}
