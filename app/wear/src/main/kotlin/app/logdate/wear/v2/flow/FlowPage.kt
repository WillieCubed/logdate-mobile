package app.logdate.wear.v2.flow

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnScope
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnState
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeSource
import androidx.wear.compose.material3.TimeText
import androidx.wear.compose.material3.TimeTextDefaults
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import app.logdate.client.repository.journals.JournalNote
import app.logdate.wear.R
import app.logdate.wear.v2.moments.MomentRow

object FlowTags {
    const val LIST = "flow_list"
}

/** What the moment list lets the person do. Each control appears only when its handler is set. */
data class FlowActions(
    val onOpenMoment: ((JournalNote) -> Unit)? = null,
)

/** Home's second page: the moments of the last 7 days, newest first. */
@Composable
fun FlowPage(
    state: FlowUiState,
    modifier: Modifier = Modifier,
    listState: TransformingLazyColumnState = rememberTransformingLazyColumnState(),
    timeSource: TimeSource = TimeTextDefaults.rememberTimeSource(TimeTextDefaults.timeFormat()),
    actions: FlowActions = FlowActions(),
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
                is FlowUiState.Ready -> momentItems(state, actions)
            }
        }
    }
}

private fun TransformingLazyColumnScope.momentItems(
    state: FlowUiState.Ready,
    actions: FlowActions,
) {
    if (state.moments.isEmpty()) {
        item { FlowMessage(stringResource(R.string.wear_flow_empty)) }
        return
    }
    items(state.moments.size, key = { index -> state.moments[index].uid.toString() }) { index ->
        val note = state.moments[index]
        MomentRow(note, state.timeZone, onOpen = actions.onOpenMoment?.let { open -> { open(note) } })
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
