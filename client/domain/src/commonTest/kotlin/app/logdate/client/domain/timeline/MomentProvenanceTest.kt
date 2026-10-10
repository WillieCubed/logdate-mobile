package app.logdate.client.domain.timeline

import app.logdate.client.repository.journals.CaptureDevice
import app.logdate.client.repository.journals.JournalNote
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** A moment counts as captured on the watch only when every note behind it was. */
class MomentProvenanceTest {
    private val at = Instant.fromEpochMilliseconds(1_000)
    private val fromWatch =
        JournalNote.Audio(mediaRef = "voice.m4a", creationTimestamp = at, lastUpdated = at, capturedOn = CaptureDevice.WATCH)
    private val fromPhone = JournalNote.Text(creationTimestamp = at, lastUpdated = at, content = "Typed", capturedOn = CaptureDevice.PHONE)
    private val unrecorded = JournalNote.Text(creationTimestamp = at, lastUpdated = at, content = "Written before devices were recorded")

    @Test
    fun `a moment whose notes all came from the watch was captured on the watch`() {
        assertTrue(moment(fromWatch, fromWatch.copy(uid = Uuid.random())).capturedOnWatch())
    }

    @Test
    fun `a moment with a note from elsewhere was not`() {
        assertFalse(moment(fromWatch, fromPhone).capturedOnWatch())
        assertFalse(moment(fromWatch, unrecorded).capturedOnWatch())
    }

    @Test
    fun `a moment without notes was not`() {
        assertFalse(moment().capturedOnWatch())
    }

    private fun moment(vararg notes: JournalNote) =
        Moment(
            id = Uuid.random(),
            label = "",
            estimatedStart = at,
            estimatedEnd = at,
            sourceNotes = notes.toList(),
            textFragments = emptyList(),
            media = emptyList(),
            audio = emptyList(),
            places = emptyList(),
            people = emptyList(),
            inferenceSource = MomentInferenceSource.TIME_OF_DAY_FALLBACK,
        )
}
