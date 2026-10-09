package app.logdate.wear.v2.flow

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import app.logdate.wear.v2.moments.momentDayAndTime
import app.logdate.wear.v2.moments.momentLabel
import kotlinx.datetime.TimeZone

object FlowTags {
    const val LIST = "flow_list"
}

/** Home's second page: the moments of the last 7 days, newest first. */
@Composable
fun FlowPage(
    state: FlowUiState,
    modifier: Modifier = Modifier,
    listState: TransformingLazyColumnState = rememberTransformingLazyColumnState(),
    timeSource: TimeSource = TimeTextDefaults.rememberTimeSource(TimeTextDefaults.timeFormat()),
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
                is FlowUiState.Ready -> momentItems(state)
            }
        }
    }
}

private fun TransformingLazyColumnScope.momentItems(state: FlowUiState.Ready) {
    if (state.moments.isEmpty()) {
        item { FlowMessage(stringResource(R.string.wear_flow_empty)) }
        return
    }
    items(state.moments.size, key = { index -> state.moments[index].uid.toString() }) { index ->
        MomentRow(state.moments[index], state.timeZone)
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

@Composable
private fun MomentRow(
    note: JournalNote,
    timeZone: TimeZone,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = momentDayAndTime(note.creationTimestamp, timeZone),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = momentLabel(note),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}
