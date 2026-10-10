package app.logdate.client.data.notes

import app.logdate.client.database.dao.AudioNoteDao
import app.logdate.client.database.dao.ImageNoteDao
import app.logdate.client.database.dao.VideoNoteDao
import app.logdate.client.database.entities.NoteContentUri
import app.logdate.client.media.storage.StoredMediaReferences
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

/**
 * Rewrites media notes that still store a local file path into `logdate-media://` references.
 *
 * Builds from before media references existed stored absolute paths, which stop working when the
 * operating system moves the app's storage. Only rows whose file is in one of this install's media
 * collections change ([StoredMediaReferences] leaves every other reference as written), so a
 * missing file keeps its original reference. Rows are updated in place without queueing a sync
 * upload: the media itself has not changed. A row is rewritten only while it still holds the value
 * that was read, so a reference changed in the meantime is never overwritten.
 *
 * It only reads rows that still hold a local path, so running it at every launch costs almost
 * nothing once it has finished. See `docs/reference/media-references.md`.
 */
class StoredMediaReferenceMigration(
    private val imageNoteDao: ImageNoteDao,
    private val audioNoteDao: AudioNoteDao,
    private val videoNoteDao: VideoNoteDao,
    private val mediaReferences: StoredMediaReferences,
) {
    /** Rewrites what it can and returns how many notes changed. */
    suspend fun run(): Int {
        val rewritten =
            rewrite(imageNoteDao.localFileContentUris(), imageNoteDao::updateContentUriIfUnchanged) +
                rewrite(audioNoteDao.localFileContentUris(), audioNoteDao::updateContentUriIfUnchanged) +
                rewrite(videoNoteDao.localFileContentUris(), videoNoteDao::updateContentUriIfUnchanged)
        if (rewritten > 0) Napier.i("Stored $rewritten media notes as LogDate media references")
        return rewritten
    }

    private suspend fun rewrite(
        rows: List<NoteContentUri>,
        update: suspend (Uuid, String, String) -> Unit,
    ): Int =
        rows.count { row ->
            val stored = mediaReferences.storedReference(row.contentUri)
            if (stored != row.contentUri) update(row.uid, row.contentUri, stored)
            stored != row.contentUri
        }
}

/**
 * Runs [StoredMediaReferenceMigration] once per app launch, off the main thread.
 *
 * A failure is logged and the migration is retried at the next launch: it only touches rows
 * that still hold a local path, so a partial run loses nothing.
 */
class StoredMediaReferenceMigrationLauncher(
    private val migration: StoredMediaReferenceMigration,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    fun start(): Job =
        scope.launch {
            try {
                migration.run()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                Napier.e("Could not store media references; will retry at the next launch", error)
            }
        }
}
