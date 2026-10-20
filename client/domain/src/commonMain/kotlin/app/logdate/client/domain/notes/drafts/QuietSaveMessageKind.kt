package app.logdate.client.domain.notes.drafts

import app.logdate.client.domain.reminders.WakingHours
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

enum class QuietSaveMessageKind { Neutral, Bedtime, Morning, Dated }

fun quietSaveMessageKind(
    savedAt: Instant,
    now: Instant,
    captureZone: TimeZone,
    viewingZone: TimeZone,
    hours: WakingHours?,
): QuietSaveMessageKind {
    val savedDate = savedAt.toLocalDateTime(captureZone).date
    val viewingDate = now.toLocalDateTime(viewingZone).date
    if (captureZone != viewingZone) return QuietSaveMessageKind.Dated
    if (hours != null && now >= savedAt) {
        val bedtime =
            (-1..1)
                .map { offset ->
                    LocalDateTime(savedDate.plus(offset, DateTimeUnit.DAY), hours.sleep).toInstant(captureZone)
                }.firstOrNull { savedAt >= it - 90.minutes && savedAt <= it + 2.hours }
        if (bedtime != null) {
            if (now <= bedtime + 2.hours) return QuietSaveMessageKind.Bedtime
            val bedDate = bedtime.toLocalDateTime(captureZone).date
            val wakeDate = if (hours.wake <= hours.sleep) bedDate.plus(1, DateTimeUnit.DAY) else bedDate
            val wakeAt = LocalDateTime(wakeDate, hours.wake).toInstant(captureZone)
            if (now >= wakeAt && viewingDate == wakeDate) return QuietSaveMessageKind.Morning
        }
    }
    return if (savedDate == viewingDate) QuietSaveMessageKind.Neutral else QuietSaveMessageKind.Dated
}
