package app.logdate.client.domain.watch

import app.logdate.client.sync.datalayer.v2.WindowReason
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Tests [WatchWindowPolicy]: which notes the watch's library window contains, and which come first. */
class WatchWindowPolicyTest {
    private val zone = TimeZone.of("America/New_York")
    private val policy = WatchWindowPolicy()

    @Test
    fun `selected seven and thirty day windows use logical day boundaries`() {
        val older = note(at(2026, 9, 20, 12, 0))
        val now = at(2026, 10, 10, 10, 0)
        val seven = policy.choose(listOf(older), WindowExtraIds(), now, zone, 4, WatchWindowPreferences(days = 7))
        val thirty = policy.choose(listOf(older), WindowExtraIds(), now, zone, 4, WatchWindowPreferences(days = 30))
        assertTrue(seven.members.isEmpty())
        assertEquals(listOf(older.id), thirty.members.map { it.id })
    }

    @Test
    fun `disabled recall reasons do not remove starred content`() {
        val anniversary = note(at(2025, 10, 10, 12, 0))
        val rewind = note(at(2025, 10, 11, 12, 0))
        val starred = note(at(2025, 10, 12, 12, 0))
        val extras = WindowExtraIds(rewind = setOf(rewind.id), starred = setOf(starred.id))
        val plan =
            policy.choose(
                listOf(anniversary, rewind, starred),
                extras,
                at(2026, 10, 10, 10, 0),
                zone,
                4,
                WatchWindowPreferences(onThisDay = false, rewind = false),
            )
        assertEquals(listOf(starred.id), plan.members.map { it.id })
        assertEquals(setOf(WindowReason.STARRED), plan.members.single().reasons)
    }

    @Test
    fun `the fourteen logical days up to today are recent`() {
        val firstDay = note(at(2026, 9, 27, 5, 0))
        val dayBefore = note(at(2026, 9, 27, 3, 0))

        val plan = policy.choose(listOf(firstDay, dayBefore), WindowExtraIds(), at(2026, 10, 10, 10, 0), zone, dayStartHour = 4)

        assertEquals(listOf(WindowMember(firstDay.id, setOf(WindowReason.RECENT))), plan.members)
    }

    @Test
    fun `before the day start it is still the day before`() {
        val plan = policy.choose(emptyList(), WindowExtraIds(), at(2026, 10, 10, 2, 0), zone, dayStartHour = 4)

        assertEquals(LocalDate(2026, 10, 9), plan.today)
    }

    @Test
    fun `the window next changes at the day start even across a clock change`() {
        val plan = policy.choose(emptyList(), WindowExtraIds(), at(2026, 3, 7, 12, 0), zone, dayStartHour = 4)

        assertEquals(Instant.parse("2026-03-08T08:00:00Z"), plan.nextChangeAt)
    }

    @Test
    fun `nearby memories from last year are retained for offline recall`() {
        val lastYear = note(at(2025, 10, 10, 12, 0))
        val nearby = note(at(2025, 10, 11, 12, 0))
        val tooFar = note(at(2025, 10, 14, 12, 0))

        val plan = policy.choose(listOf(lastYear, nearby, tooFar), WindowExtraIds(), at(2026, 10, 10, 10, 0), zone, dayStartHour = 4)

        assertEquals(setOf(lastYear.id, nearby.id), plan.members.map { it.id }.toSet())
        assertTrue(plan.members.all { it.reasons == setOf(WindowReason.ON_THIS_DAY) })
    }

    @Test
    fun `anniversary fallback crosses a year boundary`() {
        val near = note(at(2024, 12, 29, 12, 0))
        val far = note(at(2024, 12, 28, 12, 0))
        val plan = policy.choose(listOf(near, far), WindowExtraIds(), at(2026, 1, 1, 10, 0), zone, dayStartHour = 4)

        assertEquals(listOf(near.id), plan.members.map { it.id })
    }

    @Test
    fun `on February 28 of a year without a leap day notes from February 29 are on this day`() {
        assertTrue(WatchWindowPolicy.isOnThisDay(LocalDate(2024, 2, 29), LocalDate(2027, 2, 28)))
        assertFalse(WatchWindowPolicy.isOnThisDay(LocalDate(2024, 2, 29), LocalDate(2028, 2, 28)))
        assertTrue(WatchWindowPolicy.isOnThisDay(LocalDate(2024, 2, 29), LocalDate(2028, 2, 29)))
    }

    @Test
    fun `this year's notes are never on this day`() {
        assertFalse(WatchWindowPolicy.isOnThisDay(LocalDate(2026, 10, 10), LocalDate(2026, 10, 10)))
    }

    @Test
    fun `starred and Rewind notes join the window with their reasons`() {
        val old = note(at(2020, 1, 5, 12, 0))
        val extras = WindowExtraIds(rewind = setOf(old.id), starred = setOf(old.id))

        val plan = policy.choose(listOf(old), extras, at(2026, 10, 10, 10, 0), zone, dayStartHour = 4)

        assertEquals(setOf(WindowReason.STARRED, WindowReason.REWIND), plan.members.single().reasons)
    }

    @Test
    fun `over the budget recent notes come first and newer before older`() {
        val onThisDay = note(at(2025, 10, 10, 12, 0))
        val older = note(at(2026, 10, 1, 12, 0))
        val newer = note(at(2026, 10, 9, 12, 0))
        val small = WatchWindowPolicy(budget = WindowBudget(maxItems = 2))

        val plan = small.choose(listOf(onThisDay, older, newer), WindowExtraIds(), at(2026, 10, 10, 10, 0), zone, dayStartHour = 4)

        assertEquals(listOf(newer.id, older.id), plan.members.map { it.id })
        assertEquals(1, plan.dropped)
    }

    @Test
    fun `a note too large for what is left is skipped and smaller ones still fit`() {
        val large = note(at(2026, 10, 9, 12, 0), sizeBytes = 900)
        val little = note(at(2026, 10, 8, 12, 0), sizeBytes = 100)
        val tight = WatchWindowPolicy(budget = WindowBudget(maxBytes = 500))

        val plan = tight.choose(listOf(large, little), WindowExtraIds(), at(2026, 10, 10, 10, 0), zone, dayStartHour = 4)

        assertEquals(listOf(little.id), plan.members.map { it.id })
        assertEquals(1, plan.dropped)
    }

    @Test
    fun `budget priority is recent then starred then anniversary then Rewind`() {
        val recent = note(at(2026, 10, 9, 12, 0))
        val starred = note(at(2020, 1, 5, 12, 0))
        val anniversary = note(at(2025, 10, 10, 12, 0))
        val rewind = note(at(2025, 10, 20, 12, 0))
        val extras = WindowExtraIds(starred = setOf(starred.id), rewind = setOf(rewind.id))
        val small = WatchWindowPolicy(budget = WindowBudget(maxItems = 3))

        val plan = small.choose(listOf(rewind, anniversary, starred, recent), extras, at(2026, 10, 10, 10, 0), zone, 4)

        assertEquals(listOf(recent.id, starred.id, anniversary.id), plan.members.map { it.id })
        assertEquals(1, plan.dropped)
    }

    @Test
    fun `recent starred and Rewind reasons are retained together`() {
        val recent = note(at(2026, 10, 9, 12, 0))
        val extras = WindowExtraIds(starred = setOf(recent.id), rewind = setOf(recent.id))

        val plan = policy.choose(listOf(recent), extras, at(2026, 10, 10, 10, 0), zone, 4)

        assertEquals(setOf(WindowReason.RECENT, WindowReason.STARRED, WindowReason.REWIND), plan.members.single().reasons)
        assertEquals(0, plan.dropped)
    }

    @Test
    fun `future days do not enter the recent window`() {
        val future = note(at(2026, 10, 11, 12, 0))
        val old = note(at(2020, 1, 5, 12, 0))

        val plan = policy.choose(listOf(future, old), WindowExtraIds(), at(2026, 10, 10, 10, 0), zone, 4)

        assertTrue(plan.members.isEmpty())
        assertEquals(0, plan.dropped)
    }

    @Test
    fun `the default window stops at four hundred items`() {
        val candidates = List(401) { note(at(2026, 10, 9, 12, 0), sizeBytes = 1) }
        val plan = policy.choose(candidates, WindowExtraIds(), at(2026, 10, 10, 10, 0), zone, 4)

        assertEquals(400, plan.members.size)
        assertEquals(1, plan.dropped)
    }

    @Test
    fun `the default byte budget is eight MiB and accepts an exact fit`() {
        val full = note(at(2026, 10, 9, 12, 0), sizeBytes = 8_388_608)
        val extra = note(at(2026, 10, 8, 12, 0), sizeBytes = 1)
        val plan = policy.choose(listOf(extra, full), WindowExtraIds(), at(2026, 10, 10, 10, 0), zone, 4)

        assertEquals(listOf(full.id), plan.members.map { it.id })
        assertEquals(1, plan.dropped)
    }

    @Test
    fun `oversized candidates cannot overflow the remaining byte budget`() {
        val first = note(at(2026, 10, 9, 12, 0), sizeBytes = 100)
        val enormous = note(at(2026, 10, 8, 12, 0), sizeBytes = Long.MAX_VALUE)
        val last = note(at(2026, 10, 7, 12, 0), sizeBytes = 100)
        val small = WatchWindowPolicy(budget = WindowBudget(maxBytes = 200))

        val plan = small.choose(listOf(enormous, last, first), WindowExtraIds(), at(2026, 10, 10, 10, 0), zone, 4)

        assertEquals(listOf(first.id, last.id), plan.members.map { it.id })
        assertEquals(1, plan.dropped)
    }

    @Test
    fun `a zero item budget drops all qualifying items`() {
        val recent = note(at(2026, 10, 9, 12, 0))
        val none = WatchWindowPolicy(budget = WindowBudget(maxItems = 0))

        val plan = none.choose(listOf(recent), WindowExtraIds(), at(2026, 10, 10, 10, 0), zone, 4)

        assertTrue(plan.members.isEmpty())
        assertEquals(1, plan.dropped)
    }

    @Test
    fun `the window turns over at the repeated autumn day start`() {
        val plan = policy.choose(emptyList(), WindowExtraIds(), at(2026, 10, 31, 12, 0), zone, 4)

        assertEquals(Instant.parse("2026-11-01T09:00:00Z"), plan.nextChangeAt)
    }

    private fun at(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
    ): Instant = LocalDateTime(year, month, day, hour, minute).toInstant(zone)

    private fun note(
        createdAt: Instant,
        sizeBytes: Long = 100,
    ) = WindowCandidate(Uuid.random(), createdAt, sizeBytes)
}
