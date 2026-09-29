package app.logdate.wear.presentation.memories

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import kotlinx.datetime.TimeZone
import java.util.Date

/** The recording's clock time in the user's 12 or 24 hour style, in [timeZone]. */
@Composable
internal fun formatMemoryTime(
    memory: VoiceMemoryItem,
    timeZone: TimeZone,
): String {
    val format = DateFormat.getTimeFormat(LocalContext.current)
    format.timeZone = java.util.TimeZone.getTimeZone(timeZone.id)
    return format.format(Date(memory.createdAt.toEpochMilliseconds()))
}
