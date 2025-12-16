package app.logdate.client.domain.notes

import app.logdate.client.media.MediaManager
import app.logdate.client.repository.journals.JournalContentRepository
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.domain.location.LogCurrentLocationUseCase
import app.logdate.client.domain.world.LogLocationUseCase
import io.github.aakira.napier.Napier
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.uuid.Uuid

/**
 * Adds a note to the repository.
 *
 * This also adds a location to the repository if the note has a location and
 * begins uploading any media attachments.
 * 
 * Notes can be associated with multiple journals at creation time.
 */
class AddNoteUseCase(
    private val repository: JournalNotesRepository,
    private val journalContentRepository: JournalContentRepository,
    private val logLocationUseCase: LogLocationUseCase,
    private val logCurrentLocationUseCase: LogCurrentLocationUseCase,
    private val mediaManager: MediaManager,
) {
    suspend operator fun invoke(
        notes: List<JournalNote>,
        journalIds: List<Uuid> = emptyList(),
        attachments: List<String> = emptyList(),
    ) {
        invoke(
            notes = notes.toTypedArray(),
            journalIds = journalIds.toTypedArray(), 
            attachments = attachments
        )
    }

    suspend operator fun invoke(
        notes: Array<out JournalNote>,
        journalIds: Array<out Uuid>,
        attachments: List<String> = emptyList(),
    ) = coroutineScope {
        val noteJobs = notes.map { note ->
            async {
                try {
                    // Create the note
                    repository.create(note)
                    
                    // Associate with journals if any are specified
                    if (journalIds.isNotEmpty()) {
                        // Link note to all specified journals
                        journalIds.forEach { journalId ->
                            try {
                                journalContentRepository.addContentToJournal(note.uid, journalId)
                            } catch (e: Exception) {
                                // Continue with other journals even if one fails
                            }
                        }
                    }
                    
                    // Log current location when note is created
                    try {
                        logLocationUseCase() // For activity timeline
                        logCurrentLocationUseCase(LogCurrentLocationUseCase.LocationLogRequest.LogLocation()) // For location history with automatic retry
                    } catch (e: Exception) {
                        // Location logging shouldn't fail note creation
                        // LogCurrentLocationUseCase handles retries automatically
                        Napier.w("Failed to log location when creating note", e)
                    }
                } catch (e: Exception) {
                    Napier.e("Failed to create note ${note.uid}", e)
                    throw e // Rethrow to properly handle failure
                }
            }
        }
        
        try {
            noteJobs.awaitAll()
            
            // Process attachments after notes are created
            attachments.forEach { uri ->
                try {
                    mediaManager.addToDefaultCollection(uri)
                } catch (e: Exception) {
                    Napier.e("Failed to add attachment to collection: $uri", e)
                    // Continue with other attachments even if one fails
                }
            }
        } catch (e: Exception) {
            Napier.e("Error in AddNoteUseCase", e)
            throw e
        }
    }
}