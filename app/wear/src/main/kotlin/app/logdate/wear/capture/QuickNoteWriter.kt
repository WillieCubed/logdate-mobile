package app.logdate.wear.capture

import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.SystemCaptureTimeZone
import app.logdate.wear.location.WearLocationCaptureCoordinator
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Clock
import kotlin.uuid.Uuid

/** Saves a feeling as a mood check-in note. */
fun interface FeelingSaver {
    /** Returns false when the check-in could not be stored. */
    suspend fun save(feeling: Feeling): Boolean

    companion object {
        /** For a recorder that offers no feelings after a save. */
        val None = FeelingSaver { false }
    }
}

/**
 * Saves the short notes the watch captures in a tap or two: a feeling, as a mood check-in in the
 * same `#mood:` form the check-in screen writes. Each note has the place when it can be read
 * within [LOCATION_TIMEOUT_MS].
 */
class QuickNoteWriter(
    private val notesRepository: JournalNotesRepository,
    private val locationCaptureCoordinator: WearLocationCaptureCoordinator,
    private val clock: Clock = Clock.System,
) : FeelingSaver {
    override suspend fun save(feeling: Feeling): Boolean = write(MomentNoteCodec.encode(feeling)) != null

    private suspend fun write(content: String): Uuid? =
        try {
            val now = clock.now()
            val note =
                JournalNote.Text(
                    content = content,
                    uid = Uuid.random(),
                    creationTimestamp = now,
                    lastUpdated = now,
                    location = withTimeoutOrNull(LOCATION_TIMEOUT_MS) { locationCaptureCoordinator.captureForJournalEntry() },
                    timeZoneId = SystemCaptureTimeZone.currentTimeZoneId(),
                )
            notesRepository.create(note)
            note.uid
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.e("Couldn't save a note from the watch", e)
            null
        }

    companion object {
        const val LOCATION_TIMEOUT_MS = 3_000L
    }
}
