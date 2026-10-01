package app.logdate.client.feature.widgets

import app.logdate.client.domain.recommendation.MemoryRecallData
import app.logdate.client.domain.recommendation.WidgetContentType
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.transcription.TranscriptionData
import app.logdate.client.repository.transcription.TranscriptionRepository

internal fun TranscriptionData.displayText(): String? =
    transcriptDocument?.plainText?.takeIf(String::isNotBlank) ?: text?.takeIf(String::isNotBlank)

internal suspend fun MemoryRecallData.toWidgetStateWithAudio(
    notesRepository: JournalNotesRepository,
    transcriptionRepository: TranscriptionRepository,
    contentTypes: Set<WidgetContentType>,
): OnThisDayWidgetState.HasMemory {
    val base = toWidgetState()
    if (WidgetContentType.AUDIO !in contentTypes || base.summary.isNotBlank() || base.thumbnailUri != null) return base
    val audio = notesRepository.getNotesForDay(date).filterIsInstance<JournalNote.Audio>().firstOrNull() ?: return base
    val transcript = runCatching { transcriptionRepository.getTranscription(audio.uid)?.displayText() }.getOrNull().orEmpty()
    return base.copy(summary = transcript.take(160), audioNoteId = audio.uid.toString(), audioUri = audio.mediaRef)
}
