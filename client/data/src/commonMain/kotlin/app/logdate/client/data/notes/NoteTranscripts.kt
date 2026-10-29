package app.logdate.client.data.notes

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.transcription.TranscriptionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart

internal fun Flow<List<JournalNote>>.withTranscripts(
    repository: TranscriptionRepository?,
    waitForInitialTranscripts: Boolean = true,
): Flow<List<JournalNote>> {
    if (repository == null) return this
    val transcripts =
        repository.observeAllTranscriptions().let { flow ->
            if (waitForInitialTranscripts) flow else flow.onStart { emit(emptyList()) }
        }
    return combine(transcripts) { notes, transcripts ->
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
