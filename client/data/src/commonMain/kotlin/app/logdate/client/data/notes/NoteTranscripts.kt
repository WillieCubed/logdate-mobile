package app.logdate.client.data.notes

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.transcription.TranscriptionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

internal fun Flow<List<JournalNote>>.withTranscripts(repository: TranscriptionRepository?): Flow<List<JournalNote>> {
    if (repository == null) return this
    return combine(repository.observeAllTranscriptions()) { notes, transcripts ->
        val documents = transcripts.associate { it.noteId to it.transcriptDocument }
        notes.map { note ->
            if (note is JournalNote.Audio) note.copy(transcript = documents[note.uid] ?: note.transcript) else note
        }
    }
}

internal suspend fun JournalNote.withTranscript(repository: TranscriptionRepository?): JournalNote =
    if (this is JournalNote.Audio) {
        copy(transcript = repository?.getTranscription(uid)?.transcriptDocument ?: transcript)
    } else {
        this
    }

internal suspend fun JournalNote.Audio.persistTranscript(repository: TranscriptionRepository?) {
    transcript?.let { document ->
        check(repository?.acceptSyncedTranscript(uid, document) == true) { "Could not save audio transcript" }
    }
}
