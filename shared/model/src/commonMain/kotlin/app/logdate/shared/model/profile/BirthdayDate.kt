package app.logdate.shared.model.profile

import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** Birthdays are calendar dates; UTC midnight is their compatibility representation as an Instant. */
fun Instant.asBirthdayDateInstant(): Instant = toLocalDateTime(TimeZone.UTC).date.atStartOfDayIn(TimeZone.UTC)
