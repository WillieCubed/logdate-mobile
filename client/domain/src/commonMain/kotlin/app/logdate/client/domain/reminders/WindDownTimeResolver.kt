package app.logdate.client.domain.reminders

import app.logdate.client.health.SleepRepository
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/** Resolves the waking window from completed sleep sessions without requesting health access. */
class WindDownTimeResolver(
    private val sleep: SleepRepository,
) {
    suspend fun resolve(
        now: Instant,
        zone: TimeZone,
    ): WakingHours =
        try {
            if (!sleep.hasSleepPermissions()) {
                WakingHours.Fallback
            } else {
                val start = now - 30.days
                val sessions =
                    sleep
                        .getSleepSessions(start, now)
                        .filter { it.startTime >= start && it.endTime <= now && it.endTime > it.startTime }
                        .distinctBy { it.startTime to it.endTime }
                if (!sleep.hasSleepPermissions()) {
                    WakingHours.Fallback
                } else {
                    WakingHours.from(
                        averageWake = average(sessions.map { it.endTime.toLocalDateTime(zone).time }),
                        averageSleep = average(sessions.map { it.startTime.toLocalDateTime(zone).time }),
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.w("Could not resolve reminder waking hours from sleep", e)
            WakingHours.Fallback
        }

    /** Circular means keep 23:30 and 00:30 near midnight; opposing times have no useful mean. */
    private fun average(times: List<LocalTime>): LocalTime? {
        if (times.isEmpty()) return null
        val angles = times.map { it.toSecondOfDay() * 2 * PI / SECONDS_PER_DAY }
        val x = angles.sumOf(::cos) / times.size
        val y = angles.sumOf(::sin) / times.size
        if (hypot(x, y) < 0.000001) return null
        val angle = atan2(y, x)
        val seconds = ((angle * SECONDS_PER_DAY / (2 * PI)).roundToInt() + SECONDS_PER_DAY) % SECONDS_PER_DAY
        return LocalTime.fromSecondOfDay(seconds)
    }

    private companion object {
        const val SECONDS_PER_DAY = 24 * 60 * 60
    }
}
