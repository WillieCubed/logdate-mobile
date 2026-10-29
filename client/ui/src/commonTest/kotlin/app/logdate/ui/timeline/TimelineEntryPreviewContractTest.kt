package app.logdate.ui.timeline

import app.logdate.ui.location.PlaceUiState
import app.logdate.ui.profiles.PersonUiState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.uuid.Uuid

class TimelineEntryPreviewContractTest {
    @Test
    fun newestEntriesKeepSharedContextWhenTheFirstSourceIsOutsideThePreview() {
        val at = Instant.parse("2026-10-28T12:00:00Z")
        val notes = List(3) { index -> TextNoteUiState(Uuid.random(), "Entry $index", at + index.minutes) }
        val people = listOf(PersonUiState(Uuid.random(), "Alex"))
        val places = listOf(PlaceUiState("cafe", "Cafe"))
        val context =
            MomentUiState(
                "shared",
                "Lunch together",
                textNoteId = notes.first().noteId,
                people = people,
                places = places,
                sourceNoteIds = notes.map { it.noteId }.toSet(),
            )
        val day = TimelineDayUiState("", notes = notes, moments = listOf(context))
        val preview = day.entryPreview(expanded = false).moments
        assertEquals(notes.takeLast(2).reversed().map { it.noteId }, preview.map { it.textNoteId })
        preview.forEach { moment ->
            assertEquals(context.label, moment.label)
            assertEquals(people, moment.people)
            assertEquals(places, moment.places)
        }
    }

    @Test
    fun laterRecordingsKeepSharedContextWithTheirOwnBodyDestinations() {
        val at = Instant.parse("2026-10-28T12:00:00Z")
        val notes =
            List(3) { index ->
                AudioNoteUiState(Uuid.random(), "file:///recording-$index.wav", at + index.minutes, 4000)
            }
        val context =
            MomentUiState(
                "shared-audio",
                "A conversation",
                audio = MomentAudioUiState(notes.first().uri, 4000, "Oldest transcript", notes.first().noteId, at),
                sourceNoteIds = notes.map { it.noteId }.toSet(),
            )
        val preview = TimelineDayUiState("", notes = notes, moments = listOf(context)).entryPreview(expanded = false).moments
        assertEquals(notes.takeLast(2).reversed().map { it.noteId }, preview.map { it.audio?.noteId })
        preview.forEach { moment ->
            assertEquals(context.label, moment.label)
            assertEquals(null, moment.audio?.transcript)
        }
    }

    @Test
    fun originalRecordingTimeAndMatchingTranscriptSurviveProjection() {
        val id = Uuid.random()
        val at = Instant.parse("2025-01-01T12:00:00Z")
        val audio = AudioNoteUiState(noteId = id, uri = "file:///retained.wav", timestamp = at, duration = 4000)
        val context = MomentAudioUiState(audio.uri, audio.duration, "Retained transcript", id, at)
        val day = TimelineDayUiState("", notes = listOf(audio), moments = listOf(MomentUiState("audio", "", audio = context)))
        val projected =
            day
                .entryPreview(expanded = true)
                .moments
                .single()
                .audio
        assertEquals(at, projected?.recordedAt)
        assertEquals("Retained transcript", projected?.transcript)
    }
}
