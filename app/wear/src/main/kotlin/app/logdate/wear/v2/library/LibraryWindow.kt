package app.logdate.wear.v2.library

import app.logdate.client.domain.today.MoodNoteTag
import app.logdate.client.repository.journals.JournalNote
import app.logdate.wear.v2.today.TodayWindow
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlin.time.Instant

/**
 * The moments the watch library shows: voice and text, without mood check-ins, from today back
 * through [WINDOW_DAYS] days. Home lists the last [RECENT_DAYS]; the Archive lists the rest.
 */
object LibraryWindow {
    const val RECENT_DAYS = 7
    const val WINDOW_DAYS = 14

    /** The start of the oldest day Home lists. */
    fun recentStart(today: TodayWindow): Instant = today.date.minus(RECENT_DAYS - 1, DateTimeUnit.DAY).atStartOfDayIn(today.timeZone)

    /** The start of the oldest day in the library. */
    fun windowStart(today: TodayWindow): Instant = today.date.minus(WINDOW_DAYS - 1, DateTimeUnit.DAY).atStartOfDayIn(today.timeZone)

    fun isLibraryMoment(note: JournalNote): Boolean =
        (note is JournalNote.Audio || note is JournalNote.Text) && !MoodNoteTag.isFeeling(note)
}
