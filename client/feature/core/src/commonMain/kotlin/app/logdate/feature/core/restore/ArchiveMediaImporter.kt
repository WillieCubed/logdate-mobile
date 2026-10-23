package app.logdate.feature.core.restore

import app.logdate.client.domain.restore.MediaImporter
import app.logdate.client.media.MediaManager
import app.logdate.client.media.storage.StoredMediaReferences
import app.logdate.client.media.storage.isRecoveredOriginal

/** Keeps readable local originals eligible during partial and metadata-only archive recovery. */
internal fun createArchiveMediaImporter(
    mediaManager: MediaManager,
    references: StoredMediaReferences,
    contains: (String) -> Boolean,
    importFile: suspend (String) -> String?,
): MediaImporter =
    object : MediaImporter {
        override suspend fun isAvailable(reference: String): Boolean = mediaManager.isRecoveredOriginal(reference, references)

        override fun containsMedia(exportPath: String): Boolean = contains(exportPath)

        override suspend fun importMedia(exportPath: String): String? = importFile(exportPath)
    }
