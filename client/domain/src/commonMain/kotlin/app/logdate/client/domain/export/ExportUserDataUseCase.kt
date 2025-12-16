package app.logdate.client.domain.export

import app.logdate.client.device.AppInfoProvider
import app.logdate.client.device.identity.DeviceIdProvider
import app.logdate.client.domain.notes.GetAllAudioNotesUseCase
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.JournalRepository
import app.logdate.client.repository.journals.NoteType
import app.logdate.client.repository.user.UserStateRepository
import app.logdate.shared.model.Journal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Use case for exporting all user data to JSON format according to the LogDate export specification.
 */
class ExportUserDataUseCase(
    private val journalRepository: JournalRepository,
    private val journalNotesRepository: JournalNotesRepository,
    private val userStateRepository: UserStateRepository,
    private val deviceIdProvider: DeviceIdProvider,
    private val appInfoProvider: AppInfoProvider,
    private val getAllAudioNotesUseCase: GetAllAudioNotesUseCase
) {
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }
    
    /**
     * Exports all user data as a Flow that emits progress updates.
     * 
     * @return Flow of ExportProgress containing current status and completion percentage
     */
    fun exportUserData(): Flow<ExportProgress> = flow {
        emit(ExportProgress.Starting)
        
        try {
            // Gather all journals
            emit(ExportProgress.InProgress(0.1f, "Collecting journals..."))
            val journals = journalRepository.allJournalsObserved.first()
            
            // Gather all notes
            emit(ExportProgress.InProgress(0.3f, "Collecting notes..."))
            val notes = journalNotesRepository.allNotesObserved.first()
            
            // Specifically gather all audio notes for media export
            val audioNotes = getAllAudioNotesUseCase().first()
            
            // Gather all drafts - stub implementation
            emit(ExportProgress.InProgress(0.5f, "Collecting drafts..."))
            val drafts = emptyList<app.logdate.client.repository.journals.EntryDraft>()
            
            // Get app info and user ID
            val appInfo = appInfoProvider.getAppInfo()
            // Device ID will be our user ID if not signed in
            val userId = deviceIdProvider.getDeviceId().toString()
            
            // Get device ID
            val deviceId = deviceIdProvider.getDeviceId().toString()
            
            // Create export data structure
            emit(ExportProgress.InProgress(0.7f, "Preparing export data..."))
            
            // Map repository data to export models
            val exportNotes = notes.map { note ->
                when (note) {
                    is JournalNote.Text -> ExportNote(
                        id = note.uid.toString(),
                        type = "text",
                        content = note.content,
                        createdAt = note.creationTimestamp,
                        updatedAt = note.lastUpdated
                    )
                    is JournalNote.Image -> ExportNote(
                        id = note.uid.toString(),
                        type = "image",
                        mediaPath = note.mediaRef,
                        createdAt = note.creationTimestamp,
                        updatedAt = note.lastUpdated
                    )
                    is JournalNote.Audio -> ExportNote(
                        id = note.uid.toString(),
                        type = "audio",
                        mediaPath = note.mediaRef,
                        createdAt = note.creationTimestamp,
                        updatedAt = note.lastUpdated
                    )
                    is JournalNote.Video -> ExportNote(
                        id = note.uid.toString(),
                        type = "video",
                        mediaPath = note.mediaRef,
                        createdAt = note.creationTimestamp,
                        updatedAt = note.lastUpdated
                    )
                }
            }
            
            // Stub implementation
            val exportDrafts = emptyList<ExportDraft>()
            
            // Calculate actual stats
            val mediaCount = exportNotes.count { it.mediaPath != null } + 
                            exportDrafts.sumOf { it.mediaReferences.size } + 
                            audioNotes.size
                            
            val stats = ExportStats(
                journalCount = journals.size,
                noteCount = notes.size,
                draftCount = exportDrafts.size,
                mediaCount = mediaCount
            )
            
            val exportMetadata = ExportMetadata(
                exportDate = Clock.System.now(),
                userId = userId,
                deviceId = deviceId,
                appVersion = appInfo.versionName,
                stats = stats
            )
            
            // Create the separate export files
            val metadataJson = json.encodeToString(exportMetadata)
            val journalsJson = json.encodeToString(mapOf("journals" to journals))
            val notesJson = json.encodeToString(mapOf("notes" to exportNotes))
            val draftsJson = json.encodeToString(mapOf("drafts" to exportDrafts))
            
            // Create the final export data
            val exportData = ExportResult(
                metadata = metadataJson,
                journals = journalsJson,
                notes = notesJson,
                drafts = draftsJson,
                mediaFiles = getMediaFilesToExport(exportNotes, exportDrafts, audioNotes)
            )
            
            emit(ExportProgress.Completed(exportData))
            
        } catch (exception: Exception) {
            emit(ExportProgress.Failed(exception.message ?: "Unknown error occurred"))
        }
    }
    
    /**
     * Creates a standardized media path for export according to the specification.
     */
    private fun createMediaPath(uri: String, timestamp: kotlinx.datetime.Instant): String {
        val year = timestamp.toString().substring(0, 4)
        val formattedTimestamp = timestamp.toString().replace(":", "-")
        val id = uri.substringAfterLast("/")
        val extension = uri.substringAfterLast(".", "")
        
        return if (extension.isNotEmpty()) {
            "$year/${formattedTimestamp}_$id.$extension"
        } else {
            "$year/${formattedTimestamp}_$id"
        }
    }
    
    /**
     * Gets the list of media files to include in the export.
     */
    @Serializable
    data class ExportLocation(
        val latitude: Double,
        val longitude: Double,
        val placeName: String? = null
    )
    
    /**
     * Metadata for the export
     */
    @Serializable
    data class ExportMetadata(
        val exportDate: kotlinx.datetime.Instant,
        val userId: String,
        val deviceId: String,
        val appVersion: String,
        val stats: ExportStats
    )
    
    /**
     * Statistics about the export
     */
    @Serializable
    data class ExportStats(
        val journalCount: Int,
        val noteCount: Int,
        val draftCount: Int,
        val mediaCount: Int
    )
    
    /**
     * Draft data for export
     */
    @Serializable
    data class ExportDraft(
        val id: String,
        val journalId: String? = null,
        val content: String = "",
        val createdAt: kotlinx.datetime.Instant,
        val updatedAt: kotlinx.datetime.Instant,
        val location: ExportLocation? = null,
        val mediaReferences: List<String> = emptyList()
    )
    
    /**
     * Note data for export
     */
    @Serializable
    data class ExportNote(
        val id: String,
        val journalId: String? = null,
        val type: String,
        val content: String = "",
        val caption: String = "",
        val mediaPath: String? = null,
        val createdAt: kotlinx.datetime.Instant,
        val updatedAt: kotlinx.datetime.Instant,
        val location: ExportLocation? = null,
        val tags: List<String> = emptyList(),
        val people: List<String> = emptyList()
    )
    
    private fun getMediaFilesToExport(
        notes: List<ExportNote>, 
        drafts: List<ExportDraft>,
        audioNotes: List<JournalNote.Audio> = emptyList()
    ): List<ExportMediaFile> {
        val mediaFiles = mutableListOf<ExportMediaFile>()
        
        // Process regular notes with media
        notes.forEach { note ->
            if (note.mediaPath != null) {
                val exportPath = createMediaPath(note.mediaPath, note.createdAt)
                mediaFiles.add(ExportMediaFile(exportPath, note.mediaPath))
            }
        }
        
        // Process draft media references
        drafts.forEach { draft ->
            draft.mediaReferences.forEach { mediaRef ->
                val exportPath = createMediaPath(mediaRef, draft.createdAt)
                mediaFiles.add(ExportMediaFile(exportPath, mediaRef))
            }
        }
        
        // Process audio notes
        audioNotes.forEach { audioNote ->
            val exportPath = createMediaPath(audioNote.mediaRef, audioNote.creationTimestamp)
            mediaFiles.add(ExportMediaFile(exportPath, audioNote.mediaRef))
        }
        
        return mediaFiles
    }
}

/**
 * Represents the progress of the export operation.
 */
sealed class ExportProgress {
    /**
     * Export operation is starting.
     */
    data object Starting : ExportProgress()
    
    /**
     * Export operation is in progress.
     */
    data class InProgress(
        val percentage: Float,
        val message: String
    ) : ExportProgress()
    
    /**
     * Export operation completed successfully.
     */
    data class Completed(
        val result: ExportResult
    ) : ExportProgress()
    
    /**
     * Export operation failed.
     */
    data class Failed(
        val reason: String
    ) : ExportProgress()
}

/**
 * Result of a successful export operation.
 */
data class ExportResult(
    val metadata: String,
    val journals: String,
    val notes: String,
    val drafts: String,
    val mediaFiles: List<ExportMediaFile>
)

/**
 * Represents a media file to be included in the export.
 */
@Serializable
data class ExportMediaFile(
    val exportPath: String,
    val sourceUri: String
)