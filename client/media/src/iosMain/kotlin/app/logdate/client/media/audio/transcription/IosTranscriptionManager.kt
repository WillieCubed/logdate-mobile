package app.logdate.client.media.audio.transcription

import app.logdate.client.media.storage.MediaFileResolver
import app.logdate.client.repository.transcription.TranscriptionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import platform.Foundation.NSFileManager

/** Persists file recognition and keeps real coroutine ownership for cancellation. */
class IosTranscriptionManager(
    transcriptionService: TranscriptionService,
    mediaFiles: MediaFileResolver,
    repository: () -> TranscriptionRepository,
) : TranscriptionManager by FileTranscriptionScheduler(
        service = transcriptionService,
        repository = repository,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        fileExists = { uri ->
            val path = mediaFiles.filePath(uri)
            path != null && NSFileManager.defaultManager.fileExistsAtPath(path)
        },
    )
