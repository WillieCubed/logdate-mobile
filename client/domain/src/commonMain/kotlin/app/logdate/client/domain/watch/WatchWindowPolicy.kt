package app.logdate.client.domain.watch

import app.logdate.client.domain.streak.nextStreakDayStart
import app.logdate.client.domain.streak.toStreakDay
import app.logdate.client.sync.datalayer.v2.WindowReason
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * One note the phone could put in the watch's library window.
 *
 * @property sizeBytes The size of the note's body as it crosses; media does not count until D6 sends it.
 */
data class WindowCandidate(
    val id: Uuid,
    val createdAt: Instant,
    val sizeBytes: Long,
)

/** Notes other features pick for the watch; empty until Rewind and starred notes supply them. */
data class WindowExtraIds(
    val rewind: Set<Uuid> = emptySet(),
    val starred: Set<Uuid> = emptySet(),
)

/** How much of the library the watch stores. */
data class WindowBudget(
    val maxItems: Int = DEFAULT_MAX_ITEMS,
    val maxBytes: Long = DEFAULT_MAX_BYTES,
) {
    companion object {
        const val DEFAULT_MAX_ITEMS = 400
        const val DEFAULT_MAX_BYTES = 8L * 1024 * 1024
    }
}

data class WindowMember(
    val id: Uuid,
    val reasons: Set<WindowReason>,
)

/**
 * @property members In budget order; the first ones are chosen when the budget runs out.
 * @property dropped How many notes qualified but did not fit the budget.
 * @property nextChangeAt When the window changes with no new notes: the next day start.
 */
data class WindowPlan(
    val today: LocalDate,
    val members: List<WindowMember>,
    val dropped: Int,
    val nextChangeAt: Instant,
)

/**
 * Chooses the notes in the watch's library window. Days are logical days: a day runs from the phone's
 * day-start hour to the same hour the next morning, in the phone's time zone, as streaks count them.
 */
class WatchWindowPolicy(
    private val recentDays: Int = DEFAULT_RECENT_DAYS,
    private val budget: WindowBudget = WindowBudget(),
) {
    fun choose(
        candidates: List<WindowCandidate>,
        extras: WindowExtraIds,
        now: Instant,
        zone: TimeZone,
        dayStartHour: Int,
    ): WindowPlan {
        val today = now.toStreakDay(zone, dayStartHour)
        val firstRecentDay = today.minus(recentDays - 1, DateTimeUnit.DAY)
        val qualified =
            candidates.mapNotNull { candidate ->
                val day = candidate.createdAt.toStreakDay(zone, dayStartHour)
                val reasons =
                    buildSet {
                        if (day in firstRecentDay..today) add(WindowReason.RECENT)
                        if (isOnThisDay(day, today)) add(WindowReason.ON_THIS_DAY)
                        if (candidate.id in extras.starred) add(WindowReason.STARRED)
                        if (candidate.id in extras.rewind) add(WindowReason.REWIND)
                    }
                if (reasons.isEmpty()) null else candidate to reasons
            }
        val ordered =
            qualified.sortedWith(
                compareBy<Pair<WindowCandidate, Set<WindowReason>>> { (_, reasons) -> tier(reasons) }
                    .thenByDescending { (candidate, _) -> candidate.createdAt },
            )
        val members = mutableListOf<WindowMember>()
        var bytes = 0L
        for ((candidate, reasons) in ordered) {
            if (members.size == budget.maxItems) break
            if (candidate.sizeBytes > budget.maxBytes - bytes) continue
            bytes += candidate.sizeBytes
            members += WindowMember(candidate.id, reasons)
        }
        return WindowPlan(
            today = today,
            members = members,
            dropped = qualified.size - members.size,
            nextChangeAt = nextStreakDayStart(now, zone, dayStartHour),
        )
    }

    companion object {
        const val DEFAULT_RECENT_DAYS = 14

        /** What someone most likely looks for on the wrist comes first when the budget runs out. */
        private val BUDGET_ORDER = listOf(WindowReason.RECENT, WindowReason.STARRED, WindowReason.ON_THIS_DAY, WindowReason.REWIND)

        /** The same date in an earlier year; on February 28 of a year without February 29, February 29 counts too. */
        fun isOnThisDay(
            day: LocalDate,
            today: LocalDate,
        ): Boolean {
            if (day.year >= today.year) return false
            if (day.month == today.month && day.day == today.day) return true
            val leapDayMissing = today.month == Month.FEBRUARY && today.day == 28 && !isLeapYear(today.year)
            return leapDayMissing && day.month == Month.FEBRUARY && day.day == 29
        }

        private fun isLeapYear(year: Int): Boolean = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)

        private fun tier(reasons: Set<WindowReason>): Int = BUDGET_ORDER.indexOfFirst { it in reasons }
    }
}
