package app.logdate.client.repository.journals

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Tests [CaptureActivity] and how notes carry it. */
class CaptureActivityTest {
    private val at = Instant.fromEpochMilliseconds(1_000)
    private val notes =
        listOf(
            JournalNote.Text(creationTimestamp = at, lastUpdated = at, content = "Walk"),
            JournalNote.Image(creationTimestamp = at, lastUpdated = at, mediaRef = "photo.jpg"),
            JournalNote.Video(creationTimestamp = at, lastUpdated = at, mediaRef = "clip.mp4"),
            JournalNote.Audio(mediaRef = "voice.m4a", creationTimestamp = at, lastUpdated = at),
        )
    private val json = Json { encodeDefaults = true }

    @Test
    fun `a wire name reads back as its activity`() {
        assertEquals(CaptureActivity.MOVING, CaptureActivity.fromWireName("moving"))
        assertEquals(CaptureActivity.IN_VEHICLE, CaptureActivity.fromWireName("in_vehicle"))
    }

    @Test
    fun `an unknown or missing wire name is unknown`() {
        assertNull(CaptureActivity.fromWireName("swimming"))
        assertNull(CaptureActivity.fromWireName("MOVING"))
        assertNull(CaptureActivity.fromWireName(null))
    }

    @Test
    fun `recording the activity changes nothing else`() {
        notes.forEach { note ->
            val stamped = note.withCapturedDuring(CaptureActivity.IN_VEHICLE)

            assertEquals(CaptureActivity.IN_VEHICLE, stamped.capturedDuring)
            assertEquals(note, stamped.withCapturedDuring(null))
        }
    }

    @Test
    fun `a note keeps its activity through JSON`() {
        val note =
            JournalNote.Text(
                creationTimestamp = at,
                lastUpdated = at,
                content = "Drive",
                capturedDuring = CaptureActivity.IN_VEHICLE,
            )

        val encoded = json.encodeToString(JournalNote.serializer(), note)

        assertTrue("\"capturedDuring\":\"in_vehicle\"" in encoded)
        assertEquals(note, json.decodeFromString(JournalNote.serializer(), encoded))
    }

    @Test
    fun `a note without an activity encodes as it did before the field existed`() {
        val encoded = json.encodeToString(JournalNote.serializer(), notes.first())

        assertFalse("capturedDuring" in encoded)
    }

    @Test
    fun `an activity from a newer app version reads as unknown`() {
        val encoded =
            json
                .encodeToString(JournalNote.serializer(), notes.first())
                .replace("\"content\":\"Walk\"", "\"content\":\"Walk\",\"capturedDuring\":\"swimming\"")

        assertNull(json.decodeFromString(JournalNote.serializer(), encoded).capturedDuring)
    }
}
