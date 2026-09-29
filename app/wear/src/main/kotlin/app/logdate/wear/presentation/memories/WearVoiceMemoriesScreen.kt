package app.logdate.wear.presentation.memories

import android.text.format.DateFormat
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import app.logdate.wear.R
import app.logdate.wear.presentation.recording.formatDuration
import app.logdate.wear.presentation.timeline.formatDayLabel
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.viewmodel.koinViewModel
import java.util.Date
import kotlin.uuid.Uuid

@Composable
fun WearVoiceMemoriesScreen(
    onOpenMemory: (Uuid) -> Unit,
    viewModel: WearVoiceMemoriesViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    WearVoiceMemoriesContent(state = state, onOpenMemory = onOpenMemory, onLoadMore = viewModel::loadMore)
}

@Composable
internal fun WearVoiceMemoriesContent(
    state: VoiceMemoriesUiState,
    onOpenMemory: (Uuid) -> Unit = {},
    onLoadMore: () -> Unit = {},
) {
    val listState = rememberScalingLazyListState()
    ScreenScaffold(timeText = { TimeText() }, scrollState = listState) {
        ScalingLazyColumn(state = listState, modifier = Modifier.fillMaxWidth()) {
            item(key = "title") {
                Text(
                    text = stringResource(R.string.wear_memories_title),
                    style = MaterialTheme.typography.titleSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                )
            }
            when {
                !state.isLoaded -> Unit
                state.memories.isEmpty() -> item(key = "empty") { EmptyMemories() }
                else -> {
                    items(items = state.memories, key = { it.noteId.toString() }) { memory ->
                        MemoryRow(memory = memory, onClick = { onOpenMemory(memory.noteId) })
                    }
                    if (state.hasMore) {
                        item(key = "older") {
                            Button(
                                onClick = onLoadMore,
                                enabled = !state.isLoadingMore,
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(stringResource(R.string.wear_memories_older)) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyMemories() {
    androidx.compose.foundation.layout.Column(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.wear_memories_empty),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.wear_memories_empty_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun MemoryRow(
    memory: VoiceMemoryItem,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val day = memory.createdAt.toLocalDateTime(TimeZone.currentSystemDefault()).date
    val time = DateFormat.getTimeFormat(context).format(Date(memory.createdAt.toEpochMilliseconds()))
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        icon = { Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null) },
        label = { Text(formatDayLabel(day)) },
        secondaryLabel = { Text(stringResource(R.string.wear_memories_row_detail, time, formatDuration(memory.durationMs))) },
    )
}
