package app.logdate.client.intelligence.rewind.local

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.location.LocationCapturePipeline
import app.logdate.client.repository.location.LocationCaptureSource
import app.logdate.client.repository.location.LocationHistoryItem
import app.logdate.shared.model.AltitudeUnit
import app.logdate.shared.model.Location
import app.logdate.shared.model.LocationAltitude
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class LocalStoryBeatDetectorTest {
    private val detector = LocalStoryBeatDetector()
    private val day0 = Instant.fromEpochMilliseconds(0L)

    private fun textEntry(
        content: String,
        at: Instant = day0,
    ): JournalNote.Text =
        JournalNote.Text(
            creationTimestamp = at,
            lastUpdated = at,
            content = content,
        )

    private fun locationPoint(
        at: Instant,
        latitude: Double,
        longitude: Double,
    ): LocationHistoryItem =
        LocationHistoryItem(
            userId = "test-user",
            deviceId = "test-device",
            timestamp = at,
            location =
                Location(
                    latitude = latitude,
                    longitude = longitude,
                    altitude = LocationAltitude(0.0, AltitudeUnit.METERS),
                ),
            confidence = 1f,
            isGenuine = true,
            capturePipeline = LocationCapturePipeline.LEGACY,
            captureSource = LocationCaptureSource.BACKGROUND_PERIODIC,
        )

    @Test
    fun `returns empty list when there are no entries`() {
        val beats = detector.detect(emptyList(), day0, day0 + 7.days)
        assertEquals(emptyList(), beats)
    }

    @Test
    fun `produces one beat per distinct day with content`() {
        val entries =
            listOf(
                textEntry("had a great dinner with friends tonight.", at = day0 + 1.hours),
                textEntry("project deadline finally done — relief.", at = day0 + 1.days + 2.hours),
                textEntry("quiet evening, just reading.", at = day0 + 3.days + 4.hours),
            )
        val beats = detector.detect(entries, day0, day0 + 7.days)
        assertEquals(3, beats.size)
    }

    @Test
    fun `beat carries the day's entries as evidence ids`() {
        val mondayEntry = textEntry("had a great dinner with friends.", at = day0 + 1.hours)
        val wednesdayEntry = textEntry("project deadline crunch.", at = day0 + 2.days + 4.hours)
        val beats = detector.detect(listOf(mondayEntry, wednesdayEntry), day0, day0 + 7.days)
        assertEquals(2, beats.size)
        // Each beat references only its day's entry uid.
        assertTrue(beats[0].evidenceIds.contains(mondayEntry.uid.toString()))
        assertTrue(beats[1].evidenceIds.contains(wednesdayEntry.uid.toString()))
        assertEquals(
            emptyList(),
            beats[0].evidenceIds.intersect(beats[1].evidenceIds.toSet()).toList(),
        )
    }

    @Test
    fun `caps at six beats by collapsing oldest days into a phase`() {
        val entries =
            (0..9).map { dayIdx ->
                textEntry("entry on day $dayIdx", at = day0 + dayIdx.days + 1.hours)
            }
        val beats = detector.detect(entries, day0, day0 + 10.days)
        assertTrue(beats.size <= 6, "expected ≤6 beats, got ${beats.size}")
    }

    @Test
    fun `keeps one beat per day when all GPS points stay close`() {
        val day = day0 + 1.hours
        val entries =
            listOf(
                textEntry("morning at home with coffee.", at = day),
                textEntry("afternoon, kept reading.", at = day + 6.hours),
            )
        // Two points within 500m — same neighborhood.
        val locations =
            listOf(
                locationPoint(at = day, latitude = 37.7749, longitude = -122.4194),
                locationPoint(at = day + 6.hours, latitude = 37.7755, longitude = -122.4200),
            )
        val beats =
            detector.detect(
                textEntries = entries,
                periodStart = day0,
                periodEnd = day0 + 2.days,
                locationHistory = locations,
            )
        assertEquals(1, beats.size)
    }

    @Test
    fun `splits a single day into two beats when the user travels far`() {
        val day = day0 + 1.hours
        val morningEntry = textEntry("morning at home.", at = day)
        val afternoonEntry = textEntry("hit the coast in the afternoon.", at = day + 6.hours)
        val locations =
            listOf(
                // Morning cluster — San Francisco
                locationPoint(at = day, latitude = 37.7749, longitude = -122.4194),
                locationPoint(at = day + 30.minutes, latitude = 37.7755, longitude = -122.4200),
                // Afternoon cluster — Pacifica, ~13km south-west
                locationPoint(at = day + 6.hours, latitude = 37.6038, longitude = -122.4995),
                locationPoint(at = day + 7.hours, latitude = 37.6040, longitude = -122.5000),
            )
        val beats =
            detector.detect(
                textEntries = listOf(morningEntry, afternoonEntry),
                periodStart = day0,
                periodEnd = day0 + 2.days,
                locationHistory = locations,
            )
        assertEquals(2, beats.size)
        // First beat carries the morning entry; second carries the afternoon entry.
        assertTrue(beats[0].evidenceIds.contains(morningEntry.uid.toString()))
        assertTrue(beats[1].evidenceIds.contains(afternoonEntry.uid.toString()))
    }

    @Test
    fun `respects the six-beat cap even with location subdivision`() {
        val entries =
            (0..4).map { dayIdx ->
                textEntry("entry on day $dayIdx", at = day0 + dayIdx.days + 1.hours)
            }
        // Day 4 has three location clusters spread far apart.
        val locations =
            listOf(
                locationPoint(at = day0 + 4.days, latitude = 37.7749, longitude = -122.4194),
                locationPoint(at = day0 + 4.days + 1.hours, latitude = 37.7755, longitude = -122.4200),
                locationPoint(at = day0 + 4.days + 4.hours, latitude = 37.6038, longitude = -122.4995),
                locationPoint(at = day0 + 4.days + 5.hours, latitude = 37.6040, longitude = -122.5000),
                locationPoint(at = day0 + 4.days + 8.hours, latitude = 37.4419, longitude = -122.1430),
                locationPoint(at = day0 + 4.days + 9.hours, latitude = 37.4420, longitude = -122.1432),
            )
        val beats =
            detector.detect(
                textEntries = entries,
                periodStart = day0,
                periodEnd = day0 + 7.days,
                locationHistory = locations,
            )
        assertTrue(beats.size <= 6, "expected ≤6 beats, got ${beats.size}")
    }

    @Test
    fun `tags emotional weight from content words`() {
        val happy =
            textEntry(
                "I felt thrilled and grateful — what a wonderful, joyful day with friends.",
                at = day0 + 1.hours,
            )
        val rough =
            textEntry(
                "I felt completely overwhelmed and exhausted; everything went wrong.",
                at = day0 + 2.days + 1.hours,
            )
        val beats = detector.detect(listOf(happy, rough), day0, day0 + 5.days)
        assertEquals(2, beats.size)
        assertEquals("joyful", beats[0].emotionalWeight)
        assertEquals("heavy", beats[1].emotionalWeight)
    }
}
