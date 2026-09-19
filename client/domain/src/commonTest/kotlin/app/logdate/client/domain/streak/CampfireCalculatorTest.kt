package app.logdate.client.domain.streak

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CampfireCalculatorTest {
    private val today = LocalDate(2026, 3, 15)

    private fun daysAgo(vararg offsets: Int): Set<LocalDate> = offsets.map { today.minus(it, DateTimeUnit.DAY) }.toSet()

    private fun consecutiveDaysEndingAt(
        end: LocalDate,
        count: Int,
    ): Set<LocalDate> = (0 until count).map { end.minus(it, DateTimeUnit.DAY) }.toSet()

    private fun calculate(days: Set<LocalDate>) = CampfireCalculator.calculate(loggedDays = days, today = today)

    @Test
    fun `no entries leaves the fire unlit`() {
        val state = calculate(emptySet())

        assertEquals(FirePhase.UNLIT, state.phase)
        assertFalse(state.loggedToday)
        assertEquals(0, state.runDays)
        assertNull(state.size)
        assertEquals(0, state.longestRunDays)
        assertEquals(0, state.totalDaysJournaled)
        assertFalse(state.isRekindled)
    }

    @Test
    fun `an entry today lights a burning fire`() {
        val state = calculate(daysAgo(0))

        assertEquals(FirePhase.BURNING, state.phase)
        assertTrue(state.loggedToday)
        assertEquals(1, state.runDays)
        assertEquals(FireSize.SPARK, state.size)
    }

    @Test
    fun `an entry yesterday leaves the fire burning while today is open`() {
        val state = calculate(daysAgo(1, 2, 3))

        assertEquals(FirePhase.BURNING, state.phase)
        assertFalse(state.loggedToday)
        assertEquals(3, state.runDays)
    }

    @Test
    fun `missing yesterday burns the fire down to embers`() {
        val state = calculate(daysAgo(2, 3, 4, 5))

        assertEquals(FirePhase.EMBERS, state.phase)
        assertFalse(state.loggedToday)
        assertEquals(4, state.runDays)
    }

    @Test
    fun `embers have the size of the fire they came from`() {
        val state = calculate(consecutiveDaysEndingAt(today.minus(2, DateTimeUnit.DAY), 10))

        assertEquals(FirePhase.EMBERS, state.phase)
        assertEquals(FireSize.CAMPFIRE, state.size)
    }

    @Test
    fun `two missed days in a row put the fire out`() {
        val state = calculate(daysAgo(3, 4, 5))

        assertEquals(FirePhase.OUT, state.phase)
        assertEquals(0, state.runDays)
        assertNull(state.size)
        assertEquals(3, state.longestRunDays)
        assertEquals(3, state.totalDaysJournaled)
    }

    @Test
    fun `a single missed day inside a fire does not split it`() {
        val state = calculate(daysAgo(0, 1, 3, 4))

        assertEquals(FirePhase.BURNING, state.phase)
        assertEquals(4, state.runDays)
        assertFalse(state.isRekindled)
    }

    @Test
    fun `two missed days inside history split the fires`() {
        val state = calculate(daysAgo(0, 1, 4, 5, 6))

        assertEquals(2, state.runDays)
        assertEquals(3, state.longestRunDays)
        assertTrue(state.isRekindled)
    }

    @Test
    fun `logging every other day is one fire`() {
        val state = calculate(daysAgo(0, 2, 4, 6, 8, 10))

        assertEquals(FirePhase.BURNING, state.phase)
        assertEquals(6, state.runDays)
        assertEquals(FireSize.SMALL, state.size)
    }

    @Test
    fun `the longest fire comes from earlier history`() {
        val oldFire = consecutiveDaysEndingAt(today.minus(40, DateTimeUnit.DAY), 20)
        val state = calculate(oldFire + daysAgo(0, 1))

        assertEquals(2, state.runDays)
        assertEquals(20, state.longestRunDays)
    }

    @Test
    fun `the longest fire includes the current one`() {
        val state = calculate(consecutiveDaysEndingAt(today, 12) + daysAgo(30, 31))

        assertEquals(12, state.longestRunDays)
    }

    @Test
    fun `days journaled counts every distinct logged day across all fires`() {
        val state = calculate(daysAgo(0, 1, 10, 11, 12, 50))

        assertEquals(6, state.totalDaysJournaled)
    }

    @Test
    fun `a fire that went out is not marked rekindled`() {
        val state = calculate(daysAgo(5, 6, 20, 21))

        assertEquals(FirePhase.OUT, state.phase)
        assertFalse(state.isRekindled)
    }

    @Test
    fun `fire size grows through every tier`() {
        val expected =
            mapOf(
                1 to FireSize.SPARK,
                2 to FireSize.SPARK,
                3 to FireSize.SMALL,
                6 to FireSize.SMALL,
                7 to FireSize.CAMPFIRE,
                29 to FireSize.CAMPFIRE,
                30 to FireSize.BONFIRE,
                99 to FireSize.BONFIRE,
                100 to FireSize.BEACON,
                400 to FireSize.BEACON,
            )

        expected.forEach { (runDays, size) ->
            val state = calculate(consecutiveDaysEndingAt(today, runDays))
            assertEquals(runDays, state.runDays, "runDays for $runDays")
            assertEquals(size, state.size, "size for $runDays")
        }
    }

    @Test
    fun `a fire continues across a month boundary`() {
        val state =
            CampfireCalculator.calculate(
                loggedDays = setOf(LocalDate(2026, 2, 27), LocalDate(2026, 2, 28), LocalDate(2026, 3, 1)),
                today = LocalDate(2026, 3, 1),
            )

        assertEquals(3, state.runDays)
    }

    @Test
    fun `a fire continues across a leap day`() {
        val state =
            CampfireCalculator.calculate(
                loggedDays = setOf(LocalDate(2028, 2, 28), LocalDate(2028, 3, 1)),
                today = LocalDate(2028, 3, 1),
            )

        // February 29 is the single missed day between them.
        assertEquals(2, state.runDays)
        assertEquals(FirePhase.BURNING, state.phase)
    }

    @Test
    fun `history longer than a year is fully counted`() {
        val state = calculate(consecutiveDaysEndingAt(today, 500))

        assertEquals(500, state.runDays)
        assertEquals(500, state.longestRunDays)
        assertEquals(500, state.totalDaysJournaled)
    }

    @Test
    fun `days after today are ignored`() {
        val state = calculate(daysAgo(3) + setOf(LocalDate(2026, 3, 20)))

        assertEquals(FirePhase.OUT, state.phase)
        assertEquals(1, state.totalDaysJournaled)
    }
}
