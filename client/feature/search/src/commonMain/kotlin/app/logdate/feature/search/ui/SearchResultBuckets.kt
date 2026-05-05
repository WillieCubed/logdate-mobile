@file:Suppress("ktlint:standard:filename")

package app.logdate.feature.search.ui

import app.logdate.client.repository.search.SearchResult
import app.logdate.util.getLocaleFirstDayOfWeek
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Date bucket used to group search results into sections in the UI.
 *
 * Order is fixed (newest first) and matches the display order of [bucketSearchResults].
 */
enum class ResultDateBucket {
    Today,
    Yesterday,
    ThisWeek,
    ThisMonth,
    Earlier,
}

/**
 * Groups [results] into [ResultDateBucket]s based on each result's created date in [timeZone].
 *
 * Buckets are mutually exclusive — a result is always assigned to the most-recent bucket it
 * fits in (Today wins over Yesterday wins over ThisWeek, etc.). [firstDayOfWeek] determines
 * where the "This week" bucket starts; defaults to the platform locale's first day so search
 * agrees with the rest of the app (e.g. rewinds).
 *
 * Empty buckets are omitted from the output. Within each bucket, the relative order of
 * [results] is preserved (typically FTS5 BM25 ranking).
 */
fun bucketSearchResults(
    results: List<SearchResult>,
    now: Instant,
    timeZone: TimeZone,
    firstDayOfWeek: DayOfWeek = getLocaleFirstDayOfWeek(),
): List<Pair<ResultDateBucket, List<SearchResult>>> {
    if (results.isEmpty()) return emptyList()
    val today: LocalDate = now.toLocalDateTime(timeZone).date
    val yesterday = today.minus(1, DateTimeUnit.DAY)
    val weekStart = today.minus(weekOffset(today.dayOfWeek, firstDayOfWeek), DateTimeUnit.DAY)
    val monthStart = LocalDate(today.year, today.month, 1)

    val grouped = linkedMapOf<ResultDateBucket, MutableList<SearchResult>>()
    for (result in results) {
        val resultDate = result.created.toLocalDateTime(timeZone).date
        val bucket =
            when {
                resultDate >= today -> ResultDateBucket.Today
                resultDate == yesterday -> ResultDateBucket.Yesterday
                resultDate >= weekStart -> ResultDateBucket.ThisWeek
                resultDate >= monthStart -> ResultDateBucket.ThisMonth
                else -> ResultDateBucket.Earlier
            }
        grouped.getOrPut(bucket) { mutableListOf() }.add(result)
    }
    return ResultDateBucket.entries.mapNotNull { bucket ->
        grouped[bucket]?.let { bucket to it }
    }
}

private fun weekOffset(
    today: DayOfWeek,
    firstDayOfWeek: DayOfWeek,
): Int = (today.ordinal - firstDayOfWeek.ordinal + 7) % 7
