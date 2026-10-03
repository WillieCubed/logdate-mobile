package app.logdate.client.domain.today

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.NoteCoordinates
import app.logdate.client.repository.journals.NoteLocation
import app.logdate.client.repository.journals.NotePlace
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.Uuid

class TodaySummaryCalculatorTest {
    @Test
    fun `an empty day has no moments or places or voice or feeling`() {
        assertEquals(TodaySummary.Empty, TodaySummaryCalculator.calculate(emptyList()))
    }

    @Test
    fun `mood check-ins are not moments`() {
        val summary =
            TodaySummaryCalculator.calculate(
                listOf(text("Lunch by the water", minute = 0), text("#mood:good Feeling good", minute = 5)),
            )

        assertEquals(1, summary.momentCount)
    }

    @Test
    fun `voice time adds up every recording`() {
        val summary =
            TodaySummaryCalculator.calculate(
                listOf(audio(durationMs = 200_000, minute = 0), audio(durationMs = 52_000, minute = 10)),
            )

        assertEquals(252.seconds, summary.voiceDuration)
        assertEquals(2, summary.momentCount)
    }

    @Test
    fun `a recording with no known length adds no voice time`() {
        val summary = TodaySummaryCalculator.calculate(listOf(audio(durationMs = 0, minute = 0)))

        assertEquals(Duration.ZERO, summary.voiceDuration)
    }

    @Test
    fun `the latest moment is the newest note that is not a check-in`() {
        val lunch = text("Lunch", minute = 30)

        val summary =
            TodaySummaryCalculator.calculate(
                listOf(lunch, text("Coffee", minute = 0), text("#mood:great Feeling great", minute = 45)),
            )

        assertEquals(lunch, summary.latestMoment)
    }

    @Test
    fun `the feeling is the newest check-in`() {
        val summary =
            TodaySummaryCalculator.calculate(
                listOf(text("#mood:great Feeling great", minute = 20), text("#mood:sad Feeling sad", minute = 0)),
            )

        assertEquals("great", summary.feelingTag)
    }

    @Test
    fun `a newer check-in with a tag the caller does not know is skipped`() {
        val notes = listOf(text("#mood:good Feeling good", minute = 0), text("#mood:elated Feeling elated", minute = 20))

        val summary = TodaySummaryCalculator.calculate(notes) { tag -> tag != "elated" }

        assertEquals("good", summary.feelingTag)
    }

    @Test
    fun `notes at one saved place are one place however far apart`() {
        val park = NotePlace(id = Uuid.random(), name = "Tempelhofer Feld", latitude = berlin.latitude, longitude = berlin.longitude)
        val notes =
            listOf(
                text("North gate", minute = 0, location = NoteLocation(coordinates = berlin, place = park)),
                text("South gate", minute = 30, location = NoteLocation(coordinates = north(meters = -2_000.0), place = park)),
            )

        assertEquals(1, TodaySummaryCalculator.calculate(notes).placeCount)
    }

    @Test
    fun `notes within 150 meters of each other are one place`() {
        val notes =
            listOf(
                text("Coffee", minute = 0, location = NoteLocation(coordinates = berlin)),
                text("Bench outside", minute = 10, location = NoteLocation(coordinates = north(meters = 100.0))),
            )

        assertEquals(1, TodaySummaryCalculator.calculate(notes).placeCount)
    }

    @Test
    fun `notes more than 150 meters apart are two places`() {
        val notes =
            listOf(
                text("Coffee", minute = 0, location = NoteLocation(coordinates = berlin)),
                text("Office", minute = 10, location = NoteLocation(coordinates = north(meters = 300.0))),
            )

        assertEquals(2, TodaySummaryCalculator.calculate(notes).placeCount)
    }

    @Test
    fun `notes without a location add no place`() {
        assertEquals(0, TodaySummaryCalculator.calculate(listOf(text("Coffee", minute = 0))).placeCount)
    }

    @Test
    fun `check-ins add no place`() {
        val notes = listOf(text("#mood:ok Feeling ok", minute = 0, location = NoteLocation(coordinates = berlin)))

        assertEquals(0, TodaySummaryCalculator.calculate(notes).placeCount)
    }
}

private val morning = Instant.parse("2026-10-03T08:00:00Z")
private val berlin = NoteCoordinates(latitude = 52.5200, longitude = 13.4050)
private const val METERS_PER_DEGREE_LATITUDE = 111_195.0

private fun north(meters: Double) = berlin.copy(latitude = berlin.latitude + meters / METERS_PER_DEGREE_LATITUDE)

private fun text(
    content: String,
    minute: Int,
    location: NoteLocation? = null,
) = JournalNote.Text(
    creationTimestamp = morning + minute.minutes,
    lastUpdated = morning + minute.minutes,
    content = content,
    location = location,
)

private fun audio(
    durationMs: Long,
    minute: Int,
) = JournalNote.Audio(
    mediaRef = "audio/$minute.m4a",
    durationMs = durationMs,
    creationTimestamp = morning + minute.minutes,
    lastUpdated = morning + minute.minutes,
)
