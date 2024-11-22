@file:OptIn(ExperimentalUuidApi::class)

package app.logdate.feature.rewind.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import app.logdate.feature.rewind.ui.overview.RewindOverviewViewModel
import app.logdate.ui.theme.Spacing
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.uuid.ExperimentalUuidApi

@Composable
fun RewindRoute(
    onOpenRewind: RewindOpenCallback,
    onViewPreviousRewinds: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RewindOverviewViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
//    OldRewindScreen(state, onViewPreviousRewinds, onOpenRewind, modifier)
}

@Composable
internal fun OldRewindScreen(
    state: RewindUiState,
    onViewPreviousRewinds: () -> Unit,
    onOpenRewind: RewindOpenCallback,
    modifier: Modifier = Modifier,
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
                        getRewindFlavorText(),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    RewindCardPlaceholder(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )
                }

                is RewindUiState.Loaded -> {
                    val titleText = getRewindFlavorText(state.ready)
                    val textStyle = if (state.ready) {
                        MaterialTheme.typography.headlineMedium
                    } else {
                        MaterialTheme.typography.headlineSmall
                    }
                    Text(
                        titleText,
                        style = textStyle,
                    )
                    RewindCard(
                        id = state.data.id,
                        label = state.data.label, // TODO: Fix this ID
                        title = state.data.title,
                        start = state.data.issueDate.toLocalDateTime(TimeZone.UTC).date,
                        end = state.data.issueDate.toLocalDateTime(TimeZone.UTC).date
                            .plus(1, DateTimeUnit.WEEK),
                        onOpenRewind = onOpenRewind,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
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

//@Preview
//@Composable
//fun RewindScreenPreview() {
//    OldRewindScreen(
//        state = RewindUiState.Loaded(
//            ready = false,
//            data = RewindData(
//                title = "Taking care of business",
//                label = "Rewind 2024#01",
//                media = listOf(Uri.EMPTY, Uri.EMPTY),
//                places = emptyList(),
//                people = emptyList(),
//                issueDate = Clock.System.now(),
//            ),
//        ),
//        onViewPreviousRewinds = { },
//        onOpenRewind = { },
//    )
//}
//
//@Preview
//@Composable
//fun RewindScreenPreview_Loading() {
//    OldRewindScreen(
//        state = RewindUiState.Loading,
//        onViewPreviousRewinds = { },
//        onOpenRewind = { },
//    )
//}

internal fun getRewindFlavorText(ready: Boolean = false): String {
    // TODO: Generate flavor text dynamically depending on the state and content of the rewind
    return if (ready) {
        "Quite the adventurous one, aren't you?"
    } else {
        "Still working on the Rewind. Go touch some grass in the meantime."
    }
}