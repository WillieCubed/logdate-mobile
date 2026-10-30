package app.logdate.client.domain.notes

import app.logdate.client.domain.activity.CaptureActivitySource
import app.logdate.client.domain.location.LogCurrentLocationUseCase
import app.logdate.client.domain.world.LogLocationUseCase
import app.logdate.client.location.settings.LocationTrackingSettingsRepository
import app.logdate.client.media.MediaManager
import app.logdate.client.repository.journals.EntryEditBase
import app.logdate.client.repository.journals.JournalContentRepository
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.withCapturedDuring
import app.logdate.client.repository.location.LocationCaptureSource
import app.logdate.client.util.runCatchingUnlessCancelled
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.uuid.Uuid

/**
 * Adds a note to the repository.
 *
 * This also adds a location to the repository if the note has a location and
 * begins uploading any media attachments. A note captured now also records what the person is doing,
 * from [captureActivity], unless it already names an activity.
 *
 * Notes can be associated with multiple journals at creation time.
 */
class AddNoteUseCase(
    private val repository: JournalNotesRepository,
    private val journalContentRepository: JournalContentRepository,
    private val logLocationUseCase: LogLocationUseCase,
    private val logCurrentLocationUseCase: LogCurrentLocationUseCase,
    private val settingsRepository: LocationTrackingSettingsRepository,
    private val mediaManager: MediaManager,
    private val captureActivity: CaptureActivitySource = CaptureActivitySource.None,
) {
    suspend operator fun invoke(
        notes: List<JournalNote>,
        journalIds: List<Uuid> = emptyList(),
        attachments: List<String> = emptyList(),
        captureCurrentLocation: Boolean = true,
        editBase: EntryEditBase? = null,
    ) {
        invoke(
            notes = notes.toTypedArray(),
            journalIds = journalIds.toTypedArray(),
            attachments = attachments,
            captureCurrentLocation = captureCurrentLocation,
            editBase = editBase,
        )
    }

    suspend operator fun invoke(
        notes: Array<out JournalNote>,
        journalIds: Array<out Uuid>,
        attachments: List<String> = emptyList(),
        captureCurrentLocation: Boolean = true,
        editBase: EntryEditBase? = null,
    ) = coroutineScope {
        val shouldTrackLocation =
            captureCurrentLocation &&
                runCatchingUnlessCancelled { settingsRepository.getSettings().autoTrackForJournalEntries }
                    .onFailure { Napier.w("Location settings are unreadable; logging the entry location by default", it) }
                    .getOrDefault(true)
        // A note written about another time (a visit, a mark) is not stamped with what is happening now.
        val activity = if (captureCurrentLocation) captureActivity.now() else null
        val noteJobs =
            notes.map { original ->
                val note = if (activity == null || original.capturedDuring != null) original else original.withCapturedDuring(activity)
                async {
                    try {
                        // Create the note
                        val updated =
                            if (editBase == null) {
                                repository.create(note)
                                false
                            } else {
                                repository.persistEditorNote(note, editBase)
                            }

                        val failedLinks = linkToJournals(note.uid, journalIds)

                        if (shouldTrackLocation && !updated && note.uid != editBase?.copyNoteId) logEntryLocation()
                        failedLinks
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (e: Exception) {
                        Napier.e("Failed to create note ${note.uid}", e)
                        throw e // Rethrow to properly handle failure
                    }
                }
            }

        val failedLinks =
            try {
                noteJobs.awaitAll().flatten()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (e: Exception) {
                Napier.e("Error in AddNoteUseCase", e)
                throw e
            }

        // Process attachments after notes are created
        attachments.forEach { uri ->
            try {
                mediaManager.addToDefaultCollection(uri)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (e: Exception) {
                Napier.e("Failed to add attachment to collection: $uri", e)
                // Continue with other attachments even if one fails
            }
        }
        if (failedLinks.isNotEmpty()) throw NoteJournalLinkException(failedLinks)
    }

    /** Logs where a new entry was written. A failure here never fails the save; logging retries on its own. */
    private suspend fun logEntryLocation() {
        try {
            logLocationUseCase() // For activity timeline
            logCurrentLocationUseCase(
                LogCurrentLocationUseCase.LocationLogRequest.LogLocation(
                    captureSource = LocationCaptureSource.JOURNAL_ENTRY,
                ),
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (e: Exception) {
            Napier.w("Failed to log location when creating note", e)
        }
    }

    /**
     * Links [noteId] to every journal in [journalIds], attempting each one even after a failure so a
     * retry only has to repeat the links that were not saved.
     */
    private suspend fun linkToJournals(
        noteId: Uuid,
        journalIds: Array<out Uuid>,
    ): List<FailedJournalLink> =
        journalIds.mapNotNull { journalId ->
            runCatchingUnlessCancelled { journalContentRepository.addContentToJournal(noteId, journalId) }
                .exceptionOrNull()
                ?.let { cause ->
                    Napier.e("Failed to add note $noteId to journal $journalId", cause)
                    FailedJournalLink(noteId, journalId, cause)
                }
        }
}

/** A journal that a saved note could not be added to. */
data class FailedJournalLink(
    val noteId: Uuid,
    val journalId: Uuid,
    val cause: Throwable,
)

/**
 * Thrown when every note was saved but at least one requested journal link was not.
 *
 * Saving the same notes again is safe: an unchanged note is not duplicated, and only the
 * [failedLinks] still need to be added.
 */
class NoteJournalLinkException(
    val failedLinks: List<FailedJournalLink>,
) : Exception(
        "The entry was saved but could not be added to ${failedLinks.map { it.journalId }.distinct().size} journal(s)",
        failedLinks.first().cause,
    )
