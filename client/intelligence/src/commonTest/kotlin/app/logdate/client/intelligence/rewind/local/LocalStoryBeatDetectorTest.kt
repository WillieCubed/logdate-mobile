package app.logdate.client.intelligence.rewind.local

import app.logdate.client.repository.journals.JournalNote
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
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
