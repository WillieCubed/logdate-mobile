package app.logdate.wear.presentation.timeline

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import app.logdate.client.repository.journals.JournalNote
import app.logdate.wear.R
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun WearDayDetailScreen(
    viewModel: WearTimelineViewModel,
) {
    val detail by viewModel.selectedDayState.collectAsState()
    val dayDetail = detail ?: return

    WearDayDetailContent(
        detail = dayDetail,
    )
}

@Composable
internal fun WearDayDetailContent(
    detail: WearDayDetailUiState,
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
                    text = formatDetailHeader(detail.date),
                    style = MaterialTheme.typography.titleSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
                )
            }

            if (detail.entries.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = stringResource(R.string.wear_day_detail_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                items(
                    items = detail.entries,
                    key = { it.uid.toString() },
                ) { note ->
                    NoteEntryCard(note = note)
                }
            }
        }
    }
}

@Composable
private fun NoteEntryCard(note: JournalNote) {
    Card(
        onClick = { },
        modifier = Modifier.fillMaxWidth(),
    ) {
        val timezone = TimeZone.currentSystemDefault()
        val time = note.creationTimestamp.toLocalDateTime(timezone)
        val timeLabel = "%d:%02d %s".format(
            if (time.hour % 12 == 0) 12 else time.hour % 12,
            time.minute,
            if (time.hour < 12) "am" else "pm",
        )

        when (note) {
            is JournalNote.Text -> {
                Icon(
                    Icons.Default.TextFields,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = note.content.take(60),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            is JournalNote.Audio -> {
                Icon(
                    Icons.Default.Mic,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                val seconds = note.durationMs / 1000
                val durationFormatted = "%d:%02d".format(seconds / 60, seconds % 60)
                Text(
                    text = stringResource(R.string.wear_day_detail_voice_note, durationFormatted),
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            is JournalNote.Image -> {
                Text(
                    text = stringResource(R.string.wear_day_detail_photo),
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            is JournalNote.Video -> {
                Text(
                    text = stringResource(R.string.wear_day_detail_video),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Text(
            text = timeLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun formatDetailHeader(date: LocalDate): String {
    val monthName = date.month.name.lowercase().replaceFirstChar { it.uppercase() }
    return stringResource(R.string.wear_day_detail_header, monthName, date.day, date.year)
}
