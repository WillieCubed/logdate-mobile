package app.logdate.client.domain.streak

import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil

/**
 * Turns the days a user logged something into a [CampfireState].
 *
 * Logged days belong to the same fire when they are at most two calendar days apart, so a single
 * empty day between them is forgiven. The phase comes from how long ago the last logged day was:
 * today or yesterday is burning, two days ago is embers, and anything older has gone out.
 */
object CampfireCalculator {
    private const val MAX_GAP_WITHIN_FIRE = 2
    private const val EMBERS_AGE_DAYS = 2

    fun calculate(
        loggedDays: Set<LocalDate>,
        today: LocalDate,
    ): CampfireState {
        val days = loggedDays.filter { it <= today }.sorted()
        if (days.isEmpty()) {
            return CampfireState(
                phase = FirePhase.UNLIT,
                loggedToday = false,
                runDays = 0,
                size = null,
                longestRunDays = 0,
                totalDaysKept = 0,
                isRekindled = false,
            )
        }

        val fireLengths = fireLengths(days)
        val daysSinceLastLog = days.last().daysUntil(today)
        val phase =
            when {
                daysSinceLastLog < EMBERS_AGE_DAYS -> FirePhase.BURNING
                daysSinceLastLog == EMBERS_AGE_DAYS -> FirePhase.EMBERS
                else -> FirePhase.OUT
            }
        val runDays = if (phase == FirePhase.OUT) 0 else fireLengths.last()

        return CampfireState(
            phase = phase,
            loggedToday = daysSinceLastLog == 0,
            runDays = runDays,
            size = FireSize.forRunDays(runDays),
            longestRunDays = fireLengths.max(),
            totalDaysKept = days.size,
            isRekindled = phase != FirePhase.OUT && fireLengths.size > 1,
        )
    }

    /** The number of logged days in each fire, oldest first, for ascending [days]. */
    private fun fireLengths(days: List<LocalDate>): List<Int> {
        val lengths = mutableListOf(1)
        days.zipWithNext { previous, next ->
            if (previous.daysUntil(next) <= MAX_GAP_WITHIN_FIRE) {
                lengths[lengths.lastIndex]++
            } else {
                lengths += 1
            }
        }
        return lengths
    }
}
