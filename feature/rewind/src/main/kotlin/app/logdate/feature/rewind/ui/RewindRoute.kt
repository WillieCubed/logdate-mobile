package app.logdate.feature.rewind.ui

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import app.logdate.ui.theme.Spacing
import kotlinx.datetime.Clock

@Composable
fun RewindRoute(
    onOpenRewind: RewindOpenCallback,
    onViewPreviousRewinds: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RewindOverviewViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    RewindScreen(state, onViewPreviousRewinds, onOpenRewind, modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RewindScreen(
    state: RewindUiState,
    onViewPreviousRewinds: () -> Unit,
    onOpenRewind: RewindOpenCallback,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Column(
            Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            when (state) {
                is RewindUiState.Loading -> {
                    Text(
                        "Still working on the rewind",
                        style = MaterialTheme.typography.headlineLarge,
                        modifier = Modifier.padding(horizontal = Spacing.lg)
                    )
                    RewindCardPlaceholder(
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                is RewindUiState.Loaded -> {

                    val titleText = getRewindFlavorText(state.ready)
                    val textStyle = if (state.ready) {
                        MaterialTheme.typography.headlineLarge
                    } else {
                        MaterialTheme.typography.headlineMedium
                    }
                    Text(
                        titleText,
                        style = textStyle,
                    )
                    RewindCard(
                        state.data.label, // TODO: Fix this ID
                        state.data.label,
                        state.data.title,
                        onOpenRewind,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onViewPreviousRewinds,
            ) {
                Text("View previous rewinds")

            }
        }
    }
}

@Preview
@Composable
fun RewindScreenPreview() {
    RewindScreen(
        state = RewindUiState.Loaded(
            ready = false,
            data = RewindData(
                title = "Taking care of business",
                label = "Rewind 2024#01",
                media = listOf(Uri.EMPTY, Uri.EMPTY),
                places = emptyList(),
                people = emptyList(),
                issueDate = Clock.System.now(),
            ),
        ),
        onViewPreviousRewinds = { },
        onOpenRewind = { },
    )
}

internal fun getRewindFlavorText(ready: Boolean = false): String {
    // TODO: Generate flavor text dynamically depending on the state and content of the rewind
    return if (ready) {
        "Quite the adventurous one, aren't you?"
    } else {
        "Still working on the Rewind. Go touch some grass in the meantime."
    }
}