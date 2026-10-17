package app.logdate.feature.core.main

import app.logdate.client.domain.timeline.Moment
import app.logdate.client.domain.timeline.MomentInferenceSource
import app.logdate.client.domain.timeline.MomentTextFragment
import app.logdate.client.domain.timeline.TimelineDay
import app.logdate.client.repository.journals.CaptureActivity
import app.logdate.client.repository.journals.CaptureDevice
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.moments.MarkState
import app.logdate.client.repository.moments.MarkedMoment
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
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
    fun `the day detail carries what the person was doing for each note`() {
        val walking = fromPhone.copy(capturedDuring = CaptureActivity.MOVING)
        val day = TimelineDay(start = at, end = at, tldr = "", date = LocalDate(2026, 10, 10), entries = listOf(walking, fromWatch))

        val notes = day.toHomeTimelineUiState().notes

        assertEquals(CaptureActivity.MOVING, notes.single { it.noteId == walking.uid }.capturedDuring)
        assertEquals(null, notes.single { it.noteId == fromWatch.uid }.capturedDuring)
    }

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

    @Test
    fun `Home projects a pending mark into the semantic day instead of a detached prefix`() {
        val mark = MarkedMoment(Uuid.random(), at, "UTC", null, null, MarkState.OPEN, "", emptySet(), "watch", at, 0)
        val day = TimelineDay(start = at, end = at, tldr = "", date = LocalDate(2026, 10, 10), marks = listOf(mark))
        assertEquals(
            mark,
            day
                .toHomeTimelineUiState()
                .moments
                .single()
                .pendingMark,
        )
    }

    @Test
    fun `Home retains fallback phone note while pending mark is part of same day`() {
        val mark = MarkedMoment(Uuid.random(), at, "UTC", null, null, MarkState.OPEN, "", emptySet(), "watch", at, 0)
        val day =
            TimelineDay(
                start = at,
                end = at,
                tldr = "",
                date = LocalDate(2026, 10, 10),
                entries = listOf(fromPhone),
                marks = listOf(mark),
            )
        val moments = day.toHomeTimelineUiState().moments
        assertEquals(2, moments.size)
        assertTrue(moments.any { it.textSnippet == fromPhone.content })
        assertTrue(moments.any { it.textNoteId == fromPhone.uid })
        assertTrue(moments.any { it.pendingMark?.id == mark.id })
    }

    @Test
    fun `only text note snippets open a text entry directly`() {
        val textMoment = moment(listOf(fromPhone)).copy(textFragments = listOf(MomentTextFragment(fromPhone.content, fromPhone.uid)))
        val audioMoment = moment(listOf(fromWatch)).copy(textFragments = listOf(MomentTextFragment("Transcript", fromWatch.uid)))
        val day = TimelineDay(start = at, end = at, tldr = "", date = LocalDate(2026, 10, 10), moments = listOf(textMoment, audioMoment))

        val moments = day.toHomeTimelineUiState().moments

        assertEquals(fromPhone.uid, moments.single { it.id == textMoment.id.toString() }.textNoteId)
        assertEquals(null, moments.single { it.id == audioMoment.id.toString() }.textNoteId)
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
