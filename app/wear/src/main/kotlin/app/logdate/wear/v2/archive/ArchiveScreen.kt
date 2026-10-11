package app.logdate.wear.v2.archive

import android.text.format.DateFormat
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
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
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import app.logdate.client.repository.journals.JournalNote
import app.logdate.wear.R
import app.logdate.wear.v2.moments.MomentRow
import app.logdate.wear.v2.moments.rowPlayback
import app.logdate.wear.v2.playback.PlaybackState
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate
import java.time.format.DateTimeFormatter

object ArchiveTags {
    const val LIST = "archive_list"
}

/** Days 8 to 14 of the library window, a day at a time, each moment playable and openable. */
@Composable
fun ArchiveScreen(
    state: ArchiveUiState,
    modifier: Modifier = Modifier,
    listState: TransformingLazyColumnState = rememberTransformingLazyColumnState(),
    timeSource: TimeSource = TimeTextDefaults.rememberTimeSource(TimeTextDefaults.timeFormat()),
    playback: PlaybackState = PlaybackState.Idle,
    onOpenMoment: ((JournalNote) -> Unit)? = null,
    onPlayPause: ((JournalNote.Audio) -> Unit)? = null,
) {
    val spec = rememberTransformationSpec()
    ScreenScaffold(scrollState = listState, timeText = { TimeText(timeSource = timeSource) }) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding, modifier = modifier.testTag(ArchiveTags.LIST)) {
            item {
                ListHeader(modifier = Modifier.transformedHeight(this, spec), transformation = SurfaceTransformation(spec)) {
                    Text(stringResource(R.string.wear_archive_title))
                }
            }
            when (state) {
                ArchiveUiState.Loading -> Unit
                ArchiveUiState.Failed -> item { ArchiveMessage(stringResource(R.string.wear_archive_failed)) }
                is ArchiveUiState.Ready -> dayItems(state, spec, playback, onOpenMoment, onPlayPause)
            }
        }
    }
}

private fun TransformingLazyColumnScope.dayItems(
    state: ArchiveUiState.Ready,
    spec: TransformationSpec,
    playback: PlaybackState,
    onOpenMoment: ((JournalNote) -> Unit)?,
    onPlayPause: ((JournalNote.Audio) -> Unit)?,
) {
    if (state.days.isEmpty()) {
        item { ArchiveMessage(stringResource(R.string.wear_archive_empty)) }
        return
    }
    state.days.forEach { day ->
        item(key = day.date.toString()) {
            ListHeader(modifier = Modifier.transformedHeight(this, spec), transformation = SurfaceTransformation(spec)) {
                Text(archiveDayText(day.date), style = MaterialTheme.typography.labelMedium)
            }
        }
        items(day.moments.size, key = { index -> day.moments[index].uid.toString() }) { index ->
            val note = day.moments[index]
            MomentRow(
                note = note,
                timeZone = state.timeZone,
                onOpen = onOpenMoment?.let { open -> { open(note) } },
                playback = rowPlayback(note, playback, onPlayPause),
            )
        }
    }
}

@Composable
private fun ArchiveMessage(text: String) {
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
    )
}

/** [date] as a short weekday, month and day in the user's locale, such as "Mon, Sep 21". */
@Composable
private fun archiveDayText(date: LocalDate): String {
    val locale = LocalConfiguration.current.locales[0]
    return remember(locale, date) {
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, DAY_SKELETON), locale).format(date.toJavaLocalDate())
    }
}

private const val DAY_SKELETON = "EEEMMMd"
