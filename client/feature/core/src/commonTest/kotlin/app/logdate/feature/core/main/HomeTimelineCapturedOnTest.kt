package app.logdate.feature.core.main

import app.logdate.client.domain.timeline.Moment
import app.logdate.client.domain.timeline.MomentInferenceSource
import app.logdate.client.domain.timeline.TimelineDay
import app.logdate.client.repository.journals.CaptureDevice
import app.logdate.client.repository.journals.JournalNote
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Home marks the notes and moments captured on the watch, and only those. */
class HomeTimelineCapturedOnTest {
    private val at = Instant.parse("2026-10-10T15:42:00Z")
    private val fromWatch =
        JournalNote.Audio(
            mediaRef = "voice.m4a",
            durationMs = 4_000,
            creationTimestamp = at,
            lastUpdated = at,
            capturedOn = CaptureDevice.WATCH,
        )
    private val fromPhone = JournalNote.Text(creationTimestamp = at, lastUpdated = at, content = "Typed on the phone")

    @Test
    fun `notes captured on the watch are marked in the day detail`() {
        val day = TimelineDay(start = at, end = at, tldr = "", date = LocalDate(2026, 10, 10), entries = listOf(fromWatch, fromPhone))

        val notes = day.toHomeTimelineUiState().notes

        assertTrue(notes.single { it.noteId == fromWatch.uid }.capturedOnWatch)
        assertFalse(notes.single { it.noteId == fromPhone.uid }.capturedOnWatch)
    }

    @Test
    fun `a moment is marked only when every note behind it came from the watch`() {
        val watchOnly = moment(listOf(fromWatch))
        val mixed = moment(listOf(fromWatch, fromPhone))
        val day = TimelineDay(start = at, end = at, tldr = "", date = LocalDate(2026, 10, 10), moments = listOf(watchOnly, mixed))

        val moments = day.toHomeTimelineUiState().moments

        assertTrue(moments.single { it.id == watchOnly.id.toString() }.capturedOnWatch)
        assertFalse(moments.single { it.id == mixed.id.toString() }.capturedOnWatch)
    }

    private fun moment(notes: List<JournalNote>) =
        Moment(
            id = Uuid.random(),
            label = "Morning run",
            estimatedStart = at,
            estimatedEnd = at,
            sourceNotes = notes,
            textFragments = emptyList(),
            media = emptyList(),
            audio = emptyList(),
            places = emptyList(),
            people = emptyList(),
            inferenceSource = MomentInferenceSource.TIME_OF_DAY_FALLBACK,
        )
}
