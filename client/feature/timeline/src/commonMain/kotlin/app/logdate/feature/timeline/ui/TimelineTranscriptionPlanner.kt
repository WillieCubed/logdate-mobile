package app.logdate.feature.timeline.ui

import app.logdate.client.repository.transcription.TranscriptionData
import kotlin.uuid.Uuid

internal fun autoRequestableNoteIds(
    visibleNoteIds: Set<Uuid>,
    transcriptions: Map<Uuid, TranscriptionData?>,
    alreadyRequestedNoteIds: Set<Uuid>,
): Set<Uuid> =
    visibleNoteIds.filterTo(mutableSetOf()) { noteId ->
        noteId !in alreadyRequestedNoteIds && transcriptions[noteId] == null
    }
