package app.logdate.ui.timeline

import kotlin.time.Instant
import kotlin.uuid.Uuid

internal const val INITIAL_DAY_ENTRY_COUNT = 2

/** One canonical entry sequence supplies both the preview and its expanded history. */
internal fun TimelineDayUiState.entryPreview(expanded: Boolean): TimelineDayUiState {
    if (notes.isEmpty()) {
        val canonical = moments.distinctBy(MomentUiState::id).sortedByDescending { it.capturedAt }
        return copy(moments = if (expanded) canonical else canonical.take(INITIAL_DAY_ENTRY_COUNT))
    }
    val canonicalNotes = notes.distinctBy(NoteUiState::noteId).sortedByDescending(NoteUiState::capturedAt)
    val noteIds = canonicalNotes.map(NoteUiState::noteId).toSet()
    val pending = moments.filter { it.pendingMark != null && it.pendingMark.id !in noteIds }.distinctBy { it.pendingMark?.id }
    val selectedNotes = if (expanded) canonicalNotes else canonicalNotes.take(INITIAL_DAY_ENTRY_COUNT)
    val contexts = moments.flatMap { moment -> moment.entryIds().map { it to moment } }.toMap()
    val entries =
        selectedNotes.mapNotNull { note ->
            synthesizeMoment(date, listOf(note), emptyList())?.let { entry ->
                val context = contexts[note.noteId]
                entry.copy(
                    id = "note-${note.noteId}",
                    label = context?.label.orEmpty(),
                    timeOfDay = context?.timeOfDay,
                    labelPlaceName = context?.labelPlaceName,
                    people = context?.people.orEmpty(),
                    places = context?.places.orEmpty(),
                    audio =
                        entry.audio?.let { audio ->
                            audio.copy(transcript = context?.audio?.takeIf { it.noteId == audio.noteId && it.uri == audio.uri }?.transcript)
                        },
                )
            }
        }
    val sequence = (entries + pending).sortedByDescending { it.capturedAt }
    return copy(
        moments =
            (if (expanded) sequence else sequence.take(INITIAL_DAY_ENTRY_COUNT)).mapIndexed { index, moment ->
                moment.copy(
                    isHero =
                        index == 0 && moment.pendingMark == null,
                )
            },
    )
}

internal fun TimelineDayUiState.entryCount(): Int =
    if (notes.isEmpty()) {
        moments.distinctBy(MomentUiState::id).size
    } else {
        val ids = notes.map(NoteUiState::noteId).toSet()
        ids.size + moments.mapNotNull(MomentUiState::pendingMark).distinctBy { it.id }.count { it.id !in ids }
    }

private fun MomentUiState.entryIds(): List<Uuid> =
    sourceNoteIds.toList() + listOfNotNull(textNoteId, audio?.noteId) +
        media.mapNotNull(MomentMediaUiState::noteId) + handwriting.map(NoteUiState::noteId)

private fun NoteUiState.capturedAt(): Instant =
    when (this) {
        is TextNoteUiState -> timestamp
        is ImageNoteUiState -> timestamp
        is VideoNoteUiState -> timestamp
        is AudioNoteUiState -> timestamp
        is HandwritingNoteUiState -> timestamp
    }
