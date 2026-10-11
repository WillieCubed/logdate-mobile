package app.logdate.wear.v2.flow

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnScope
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnState
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeSource
import androidx.wear.compose.material3.TimeText
import androidx.wear.compose.material3.TimeTextDefaults
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import app.logdate.client.repository.journals.JournalNote
import app.logdate.wear.R
import app.logdate.wear.v2.moments.MomentRow
import app.logdate.wear.v2.moments.rowPlayback
import app.logdate.wear.v2.playback.PlaybackState

object FlowTags {
    const val LIST = "flow_list"
    const val EARLIER = "flow_earlier"
}

/** What the moment list lets the person do. Each control appears only when its handler is set. */
data class FlowActions(
    val onOpenMoment: ((JournalNote) -> Unit)? = null,
    val onPlayPause: ((JournalNote.Audio) -> Unit)? = null,
    val onOpenArchive: (() -> Unit)? = null,
)

/**
 * Home's second page: the voice and text moments of the last 7 days, newest first, each playable
 * from its row, then the memory [cards], then Earlier when the library window has older moments, and [footer] at the very end.
 */
@Composable
fun FlowPage(
    state: FlowUiState,
    modifier: Modifier = Modifier,
    listState: TransformingLazyColumnState = rememberTransformingLazyColumnState(),
    timeSource: TimeSource = TimeTextDefaults.rememberTimeSource(TimeTextDefaults.timeFormat()),
    actions: FlowActions = FlowActions(),
    playback: PlaybackState = PlaybackState.Idle,
    cards: TransformingLazyColumnScope.(TransformationSpec) -> Unit = {},
    footer: TransformingLazyColumnScope.(TransformationSpec) -> Unit = {},
) {
    val spec = rememberTransformationSpec()
    ScreenScaffold(scrollState = listState, timeText = { TimeText(timeSource = timeSource) }) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding, modifier = modifier.testTag(FlowTags.LIST)) {
            item {
                ListHeader(modifier = Modifier.transformedHeight(this, spec), transformation = SurfaceTransformation(spec)) {
                    Text(stringResource(R.string.wear_flow_title))
                }
            }
            when (state) {
                FlowUiState.Loading -> Unit
                FlowUiState.Failed -> item { FlowMessage(stringResource(R.string.wear_flow_failed)) }
                is FlowUiState.Ready -> {
                    momentItems(state, actions, playback)
                    cards(spec)
                    earlierItem(state, actions.onOpenArchive, spec)
                }
            }
            footer(spec)
        }
    }
}

private fun TransformingLazyColumnScope.momentItems(
    state: FlowUiState.Ready,
    actions: FlowActions,
    playback: PlaybackState,
) {
    if (state.moments.isEmpty()) {
        item { FlowMessage(stringResource(R.string.wear_flow_empty)) }
        return
    }
    items(state.moments.size, key = { index -> state.moments[index].uid.toString() }) { index ->
        val note = state.moments[index]
        MomentRow(
            note = note,
            timeZone = state.timeZone,
            onOpen = actions.onOpenMoment?.let { open -> { open(note) } },
            playback = rowPlayback(note, playback, actions.onPlayPause),
        )
    }
}

private fun TransformingLazyColumnScope.earlierItem(
    state: FlowUiState.Ready,
    onOpenArchive: (() -> Unit)?,
    spec: TransformationSpec,
) {
    if (state.earlierCount == 0 || onOpenArchive == null) return
    item {
        Button(
            onClick = onOpenArchive,
            modifier = Modifier.fillMaxWidth().transformedHeight(this, spec).testTag(FlowTags.EARLIER),
            transformation = SurfaceTransformation(spec),
            secondaryLabel = { Text(pluralStringResource(R.plurals.wear_flow_earlier_count, state.earlierCount, state.earlierCount)) },
            label = { Text(stringResource(R.string.wear_flow_earlier)) },
        )
    }
}

@Composable
private fun FlowMessage(text: String) {
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
    )
}
