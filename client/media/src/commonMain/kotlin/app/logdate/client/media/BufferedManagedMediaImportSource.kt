package app.logdate.client.media

import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlin.uuid.Uuid

/** Stages photo-library assets on platforms without Android's streaming import source. */
class BufferedManagedMediaImportSource(
    private val media: MediaManager,
    private val temporaryDirectory: String,
) : ManagedMediaImportSource {
    override suspend fun stage(sourceUri: String): StagedMediaFile {
        val payload = media.readMedia(sourceUri)
        val path = Path(temporaryDirectory, "logdate-mark-import-${Uuid.random()}")
        try {
            SystemFileSystem.sink(path).buffered().use { it.write(payload.data) }
        } catch (failure: Throwable) {
            SystemFileSystem.delete(path, mustExist = false)
            throw failure
        }
        return StagedMediaFile(path.toString(), payload.fileName, payload.mimeType)
    }

    override suspend fun discard(stagedMedia: StagedMediaFile) {
        SystemFileSystem.delete(Path(stagedMedia.sourceFilePath), mustExist = false)
    }
}
