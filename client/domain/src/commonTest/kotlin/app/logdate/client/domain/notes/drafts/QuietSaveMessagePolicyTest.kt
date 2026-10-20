package app.logdate.client.domain.notes.drafts

import app.logdate.client.domain.reminders.WakingHours
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class QuietSaveMessagePolicyTest {
    private val zone = TimeZone.UTC
    private val hours = WakingHours(LocalTime(7, 30), LocalTime(22, 30))

    @Test
    fun `morning follows local waking time across daylight saving transitions`() {
        val newYork = TimeZone.of("America/New_York")
        for ((saved, viewed) in listOf(
            "2026-03-08T03:45:00Z" to "2026-03-08T12:00:00Z",
            "2026-11-01T02:45:00Z" to "2026-11-01T13:00:00Z",
        )) {
            assertEquals(
                QuietSaveMessageKind.Morning,
                quietSaveMessageKind(Instant.parse(saved), Instant.parse(viewed), newYork, newYork, hours),
            )
        }
    }

    @Test
    fun `a bedtime save gets morning wording when opened after waking`() {
        assertEquals(QuietSaveMessageKind.Morning, message("2026-10-19T22:45:00Z", "2026-10-20T08:00:00Z"))
        assertEquals(QuietSaveMessageKind.Bedtime, message("2026-10-19T22:45:00Z", "2026-10-19T22:46:00Z"))
    }

    @Test
    fun `old saves and unknown bedtimes avoid assumptions about sleep`() {
        assertEquals(QuietSaveMessageKind.Dated, message("2026-10-19T22:45:00Z", "2026-10-21T08:00:00Z"))
        assertEquals(
            QuietSaveMessageKind.Neutral,
            quietSaveMessageKind(Instant.parse("2026-10-19T22:45:00Z"), Instant.parse("2026-10-19T22:46:00Z"), zone, zone, null),
        )
    }

    @Test
    fun `bedtime after midnight belongs to that mornings sleep period`() {
        assertEquals(
            QuietSaveMessageKind.Morning,
            quietSaveMessageKind(
                Instant.parse("2026-10-20T00:45:00Z"),
                Instant.parse("2026-10-20T08:00:00Z"),
                zone,
                zone,
                WakingHours(LocalTime(7, 30), LocalTime(0, 30)),
            ),
        )
    }

    @Test
    fun `travel uses a date rather than a misleading last night`() {
        assertEquals(
            QuietSaveMessageKind.Dated,
            quietSaveMessageKind(
                Instant.parse("2026-10-19T22:45:00Z"),
                Instant.parse("2026-10-20T08:00:00Z"),
                zone,
                TimeZone.of("Asia/Tokyo"),
                hours,
            ),
        )
    }

    private fun message(
        saved: String,
        viewed: String,
    ) = quietSaveMessageKind(Instant.parse(saved), Instant.parse(viewed), zone, zone, hours)
}
