package app.logdate.feature.search.ui

import app.logdate.client.repository.search.SearchContentType
import app.logdate.client.repository.search.SearchResult
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

class SearchResultBucketsTest {
    private val tz = TimeZone.of("America/Los_Angeles")
    private val monday = DayOfWeek.MONDAY

    // Wednesday, 2026-05-06 14:30 PT — middle of the week so all 5 buckets are reachable.
    private val now: Instant = LocalDateTime(2026, 5, 6, 14, 30).toInstant(tz)

    @Test
    fun `empty input returns empty list`() {
        val grouped = bucketSearchResults(emptyList(), now, tz, monday)
        assertTrue(grouped.isEmpty())
    }

    @Test
    fun `result created today goes in Today bucket`() {
        val today = LocalDate(2026, 5, 6).atStartOfDayIn(tz)
        val grouped = bucketSearchResults(listOf(resultAt(today)), now, tz, monday)
        assertEquals(listOf(ResultDateBucket.Today), grouped.map { it.first })
    }

    @Test
    fun `result created yesterday goes in Yesterday bucket`() {
        val yesterday = LocalDate(2026, 5, 5).atStartOfDayIn(tz)
        val grouped = bucketSearchResults(listOf(resultAt(yesterday)), now, tz, monday)
        assertEquals(listOf(ResultDateBucket.Yesterday), grouped.map { it.first })
    }

    @Test
    fun `result two days ago goes in ThisWeek bucket when within the week`() {
        // Monday 2026-05-04 — week starts Monday, so still in ThisWeek.
        val mondayDate = LocalDate(2026, 5, 4).atStartOfDayIn(tz)
        val grouped = bucketSearchResults(listOf(resultAt(mondayDate)), now, tz, monday)
        assertEquals(listOf(ResultDateBucket.ThisWeek), grouped.map { it.first })
    }

    @Test
    fun `result before this week but in this month goes in ThisMonth bucket`() {
        val mayFirst = LocalDate(2026, 5, 1).atStartOfDayIn(tz)
        val grouped = bucketSearchResults(listOf(resultAt(mayFirst)), now, tz, monday)
        assertEquals(listOf(ResultDateBucket.ThisMonth), grouped.map { it.first })
    }

    @Test
    fun `result before this month goes in Earlier bucket`() {
        val lastYear = LocalDate(2025, 12, 25).atStartOfDayIn(tz)
        val grouped = bucketSearchResults(listOf(resultAt(lastYear)), now, tz, monday)
        assertEquals(listOf(ResultDateBucket.Earlier), grouped.map { it.first })
    }

    @Test
    fun `mixed input is grouped into the correct buckets in display order`() {
        val results =
            listOf(
                resultAt(LocalDate(2025, 12, 25).atStartOfDayIn(tz)), // Earlier
                resultAt(LocalDate(2026, 5, 4).atStartOfDayIn(tz)), // ThisWeek
                resultAt(LocalDate(2026, 5, 6).atStartOfDayIn(tz)), // Today
                resultAt(LocalDate(2026, 5, 1).atStartOfDayIn(tz)), // ThisMonth
                resultAt(LocalDate(2026, 5, 5).atStartOfDayIn(tz)), // Yesterday
            )
        val grouped = bucketSearchResults(results, now, tz, monday)
        assertEquals(
            listOf(
                ResultDateBucket.Today,
                ResultDateBucket.Yesterday,
                ResultDateBucket.ThisWeek,
                ResultDateBucket.ThisMonth,
                ResultDateBucket.Earlier,
            ),
            grouped.map { it.first },
        )
    }

    @Test
    fun `multiple results in the same bucket preserve input order`() {
        val a = resultAt(LocalDate(2026, 5, 6).atStartOfDayIn(tz))
        val b = resultAt(LocalDate(2026, 5, 6).atStartOfDayIn(tz))
        val c = resultAt(LocalDate(2026, 5, 6).atStartOfDayIn(tz))
        val grouped = bucketSearchResults(listOf(a, b, c), now, tz, monday)
        assertEquals(1, grouped.size)
        assertEquals(listOf(a, b, c), grouped[0].second)
    }

    @Test
    fun `Sunday-start week shifts ThisWeek boundary back one day`() {
        // 2026-05-03 is a Sunday. With Sunday-start week (now is Wed 2026-05-06):
        //   - week runs Sun 2026-05-03 through Sat 2026-05-09
        //   - so 2026-05-03 falls in ThisWeek (NOT ThisMonth)
        val sunday = LocalDate(2026, 5, 3).atStartOfDayIn(tz)
        val grouped = bucketSearchResults(listOf(resultAt(sunday)), now, tz, DayOfWeek.SUNDAY)
        assertEquals(listOf(ResultDateBucket.ThisWeek), grouped.map { it.first })
    }

    private fun resultAt(created: Instant) =
        SearchResult(
            uid = Uuid.random(),
            content = "test",
            created = created,
            contentType = SearchContentType.TEXT_NOTE,
        )
}
