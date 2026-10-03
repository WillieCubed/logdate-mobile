package app.logdate.wear.v2.today

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.transformLatest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * The calendar day Today covers: [date] in [timeZone], from [start] up to, but not including, [end].
 * A day around a daylight saving change is 23 or 25 hours long.
 */
data class TodayWindow(
    val date: LocalDate,
    val timeZone: TimeZone,
    val start: Instant,
    val end: Instant,
) {
    operator fun contains(instant: Instant): Boolean = instant >= start && instant < end
}

/**
 * Reports the day Today covers, at once and again whenever it changes: at local midnight, and as
 * soon as [timeChanges] says the clock, date or time zone was changed.
 */
class DayClock(
    private val timeChanges: Flow<Unit>,
    private val clock: Clock = Clock.System,
    private val timeZone: () -> TimeZone = { TimeZone.currentSystemDefault() },
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(): Flow<TodayWindow> =
        merge(flowOf(Unit), timeChanges)
            .transformLatest {
                while (true) {
                    val today = current()
                    emit(today)
                    delay(today.end - clock.now())
                }
            }.distinctUntilChanged()

    fun current(): TodayWindow {
        val zone = timeZone()
        val date = clock.now().toLocalDateTime(zone).date
        return TodayWindow(
            date = date,
            timeZone = zone,
            start = date.atStartOfDayIn(zone),
            end = date.plus(1, DateTimeUnit.DAY).atStartOfDayIn(zone),
        )
    }
}
