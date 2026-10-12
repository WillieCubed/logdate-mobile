package app.logdate.wear.sync.v2.window

import app.logdate.client.repository.transcription.TranscriptionRepository
import kotlinx.coroutines.CoroutineScope
import kotlin.uuid.Uuid

/** Reads and retains phone transcripts without starting the phone's recognition pipeline on Wear. */
class WatchTranscriptRepository(
    private val delegate: TranscriptionRepository,
) : TranscriptionRepository by delegate {
    override fun startAutomaticTranscriptions(scope: CoroutineScope) = Unit

    override suspend fun requestTranscription(noteId: Uuid): Boolean = false
}
