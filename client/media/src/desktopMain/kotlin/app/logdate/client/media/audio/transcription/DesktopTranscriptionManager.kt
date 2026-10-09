package app.logdate.client.media.audio.transcription

import app.logdate.client.repository.transcription.TranscriptionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import java.net.URI

/** Persists local recognition independently of the audio view lifecycle. */
class DesktopTranscriptionManager(
    transcriptionService: TranscriptionService,
    repository: () -> TranscriptionRepository,
) : TranscriptionManager by FileTranscriptionScheduler(
        service = transcriptionService,
        repository = repository,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        fileExists = { uri -> runCatching { (if (uri.startsWith("file:")) File(URI(uri)) else File(uri)).isFile }.getOrDefault(false) },
    )
