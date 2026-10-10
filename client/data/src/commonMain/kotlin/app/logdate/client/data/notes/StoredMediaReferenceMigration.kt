package app.logdate.client.data.notes

import app.logdate.client.database.dao.AudioNoteDao
import app.logdate.client.database.dao.ImageNoteDao
import app.logdate.client.database.dao.VideoNoteDao
import app.logdate.client.database.entities.NoteContentUri
import app.logdate.client.media.storage.MediaRescuer
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
 * collections change ([StoredMediaReferences] leaves every other reference as written), and
 * media a note stores outside them, such as in a purgeable cache, is moved in by the
 * [MediaRescuer] while it still exists. A missing file keeps its original reference. Rows are updated in place without queueing a sync
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
    private val rescuer: MediaRescuer = MediaRescuer.None,
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
            val stored =
                mediaReferences.storedReference(row.contentUri).takeIf { it != row.contentUri }
                    ?: rescuer.rescue(row.contentUri)
            if (stored != null) update(row.uid, row.contentUri, stored)
            stored != null
        }
}

/**
 * Runs [StoredMediaReferenceMigration] once per app process, off the calling thread.
 *
 * The platform's app entry point calls [start] when the app is actually in use (the app launch on
 * Android, the first screen on iOS and desktop), not while the dependency graph is built: building
 * the migration opens the database, which can be locked or slow during a background launch. The
 * migration is created inside the coroutine for the same reason.
 *
 * A failure is logged and the migration is tried again the next time the app starts: it only
 * touches rows that still hold a local path, so a partial run loses nothing.
 */
class StoredMediaReferenceMigrationLauncher(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val migration: () -> StoredMediaReferenceMigration,
) {
    private var job: Job? = null

    /** Starts the migration the first time it is called and returns that run on every later call. */
    fun start(): Job =
        job ?: scope
            .launch {
                try {
                    migration().run()
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (error: Exception) {
                    Napier.e("Could not store media references; will retry the next time the app starts", error)
                }
            }.also { job = it }
}
