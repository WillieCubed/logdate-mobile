package app.logdate.wear.presentation.rewind

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import app.logdate.wear.R
import kotlin.uuid.Uuid

@Composable
fun WearRewindListScreen(
    viewModel: WearRewindViewModel,
    onSelectRewind: (Uuid) -> Unit,
) {
    val state by viewModel.rewindListState.collectAsState()
    WearRewindListContent(
        state = state,
        onSelectRewind = onSelectRewind,
    )
}

@Composable
internal fun WearRewindListContent(
    state: WearRewindListUiState,
    onSelectRewind: (Uuid) -> Unit = {},
) {
    val listState = rememberScalingLazyListState()

    ScreenScaffold(
        timeText = { TimeText() },
        scrollState = listState,
    ) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
        ) {
            item(key = "header") {
                Text(
                    text = stringResource(R.string.wear_rewind_title),
                    style = MaterialTheme.typography.titleSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
                )
            }

            if (state.rewinds.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = stringResource(R.string.wear_rewind_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                    )
                }
            } else {
                items(
                    items = state.rewinds,
                    key = { it.uid.toString() },
                ) { rewind ->
                    Card(
                        onClick = { onSelectRewind(rewind.uid) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = rewind.title,
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = rewind.label,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
