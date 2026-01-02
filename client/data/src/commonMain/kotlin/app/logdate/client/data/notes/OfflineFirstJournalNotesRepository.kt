package app.logdate.client.data.notes

import app.logdate.client.database.dao.AudioNoteDao
import app.logdate.client.database.dao.ImageNoteDao
import app.logdate.client.database.dao.JournalNotesDao
import app.logdate.client.database.dao.TextNoteDao
import app.logdate.client.database.dao.VideoNoteDao
import app.logdate.client.repository.journals.ExportableJournalContentRepository
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.JournalRepository
import app.logdate.client.repository.journals.SyncableJournalNotesRepository
import app.logdate.client.sync.NoOpSyncManager
import app.logdate.client.sync.SyncManager
import app.logdate.client.sync.metadata.AssociationPendingKey
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.metadata.SyncMetadataService
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.uuid.Uuid
import java.io.File

/**
 * A repository for journal notes that stores data locally.
 */
class OfflineFirstJournalNotesRepository(
    private val textNoteDao: TextNoteDao,
    private val imageNoteDao: ImageNoteDao,
    // TODO: Rename VoiceNoteDao to AudioNoteDao to better represent general audio content
    private val audioNoteDao: AudioNoteDao,
    private val videoNoteDao: VideoNoteDao,
    private val journalNotesDao: JournalNotesDao,
    private val journalRepository: JournalRepository,
    private val syncManagerProvider: () -> SyncManager = { NoOpSyncManager },
    private val syncMetadataService: SyncMetadataService,
    private val syncScope: CoroutineScope = CoroutineScope(Dispatchers.IO),
) : JournalNotesRepository, ExportableJournalContentRepository, SyncableJournalNotesRepository {

    override val allNotesObserved: Flow<List<JournalNote>> =
        textNoteDao.getAllNotes().combine(
            imageNoteDao.getAllNotes()
                .combine(audioNoteDao.getAllNotes()) { imageNotes, voiceNotes ->
                    imageNotes.map { it.toModel() } + voiceNotes.map { it.toModel() }
                }
                .combine(videoNoteDao.getAllNotes()) { imageAndAudioNotes, videoNotes ->
                    imageAndAudioNotes + videoNotes.map { it.toModel() }
                }
        ) { textNotes, otherNotes ->
            textNotes.map { it.toModel() } + otherNotes
        }

    override fun observeNotesInJournal(journalId: Uuid): Flow<List<JournalNote>> {
        // Get all notes-to-journal mappings for this journal
        return journalNotesDao.getNotesForJournal(journalId)
            .combine(allNotesObserved) { crossRefs, allNotes ->
                // Extract note IDs from the cross-references
                val noteIds = crossRefs.map { it.noteId }
                
                // Filter notes that belong to this journal using the note IDs from the cross-references
                allNotes.filter { note -> noteIds.contains(note.uid) }
            }
    }

    override fun observeNotesInRange(start: Instant, end: Instant): Flow<List<JournalNote>> {
        val startMillis = start.toEpochMilliseconds()
        val endMillis = end.toEpochMilliseconds()

        return textNoteDao.getNotesInRange(startMillis, endMillis)
            .combine(
                imageNoteDao.getNotesInRange(startMillis, endMillis)
                    .combine(audioNoteDao.getNotesInRange(startMillis, endMillis)) { imageNotes, voiceNotes ->
                        imageNotes.map { it.toModel() } + voiceNotes.map { it.toModel() }
                    }
                    .combine(videoNoteDao.getNotesInRange(startMillis, endMillis)) { imageAndAudioNotes, videoNotes ->
                        imageAndAudioNotes + videoNotes.map { it.toModel() }
                    }
            ) { textNotes, otherNotes ->
                val result = textNotes.map { it.toModel() } + otherNotes
                result
            }
    }

    override fun observeNotesPage(pageSize: Int, offset: Int): Flow<List<JournalNote>> {
        // Since AudioNoteDao and VideoNoteDao don't have getNotesPage, handle pagination in memory.
        return textNoteDao.getAllNotes()
            .combine(imageNoteDao.getAllNotes()) { textNotes, imageNotes ->
                val textModels = textNotes.map { it.toModel() }
                val imageModels = imageNotes.map { it.toModel() }
                textModels + imageModels
            }
            .combine(audioNoteDao.getAllNotes()) { initialNotes, voiceNotes ->
                val voiceModels = voiceNotes.map { it.toModel() }
                initialNotes + voiceModels
            }
            .combine(videoNoteDao.getAllNotes()) { initialNotes, videoNotes ->
                val videoModels = videoNotes.map { it.toModel() }
                (initialNotes + videoModels)
                    .sortedByDescending { it.creationTimestamp }
                    .drop(offset)
                    .take(pageSize)
            }
    }

    override fun observeNotesStream(pageSize: Int): Flow<List<JournalNote>> = 
        // Use the existing allNotesObserved for real-time updates
        // This gives us immediate responsiveness since Room already caches data
        allNotesObserved

    override fun observeRecentNotes(limit: Int): Flow<List<JournalNote>> {
        return textNoteDao.getRecentNotes(limit)
            .combine(
                imageNoteDao.getRecentNotes(limit)
                    .combine(audioNoteDao.getRecentNotes(limit)) { imageNotes, voiceNotes ->
                        imageNotes.map { it.toModel() } + voiceNotes.map { it.toModel() }
                    }
                    .combine(videoNoteDao.getRecentNotes(limit)) { imageAndAudioNotes, videoNotes ->
                        imageAndAudioNotes + videoNotes.map { it.toModel() }
                    }
            ) { textNotes, otherNotes ->
                (textNotes.map { it.toModel() } + otherNotes)
                    .sortedByDescending { it.creationTimestamp }
                    .take(limit)
            }
    }

    override suspend fun create(note: JournalNote): Uuid {
        val noteId = when (note) {
            is JournalNote.Text -> {
                textNoteDao.addNote(note.toEntity())
                note.uid
            }

            is JournalNote.Image -> {
                imageNoteDao.addNote(note.toEntity())
                note.uid
            }

            is JournalNote.Audio -> {
                audioNoteDao.addNote(note.toEntity())
                note.uid
            }
            
            is JournalNote.Video -> {
                videoNoteDao.addNote(note.toEntity())
                note.uid
            }
        }

        syncMetadataService.enqueuePending(
            entityId = note.uid.toString(),
            entityType = EntityType.NOTE,
            operation = PendingOperation.CREATE
        )

        triggerContentSync()
        
        
        return noteId
    }

    override suspend fun remove(note: JournalNote) {
        when (note) {
            is JournalNote.Text -> {
                textNoteDao.removeNote(note.uid)
            }

            is JournalNote.Image -> {
                imageNoteDao.removeNote(note.uid)
            }

            is JournalNote.Audio -> {
                audioNoteDao.removeNote(note.uid)
            }
            
            is JournalNote.Video -> {
                videoNoteDao.removeNote(note.uid)
            }
        }

        syncMetadataService.enqueuePending(
            entityId = note.uid.toString(),
            entityType = EntityType.NOTE,
            operation = PendingOperation.DELETE
        )

        triggerContentSync()
        
    }

    override suspend fun removeById(noteId: Uuid) {
        textNoteDao.removeNote(noteId)
        imageNoteDao.removeNote(noteId)
        audioNoteDao.removeNote(noteId)
        videoNoteDao.removeNote(noteId)

        syncMetadataService.enqueuePending(
            entityId = noteId.toString(),
            entityType = EntityType.NOTE,
            operation = PendingOperation.DELETE
        )

        triggerContentSync()
        
    }

    override suspend fun create(note: JournalNote, journalId: Uuid) {
        // Add the note first
        create(note)
        
        // Link it to the journal
        journalNotesDao.addNoteToJournal(journalId, note.uid)

        syncMetadataService.enqueuePending(
            entityId = AssociationPendingKey(journalId, note.uid).toPendingId(),
            entityType = EntityType.ASSOCIATION,
            operation = PendingOperation.CREATE
        )

        triggerAssociationSync()
        
    }

    override suspend fun removeFromJournal(noteId: Uuid, journalId: Uuid) {
        journalNotesDao.removeNoteFromJournal(journalId, noteId)

        syncMetadataService.enqueuePending(
            entityId = AssociationPendingKey(journalId, noteId).toPendingId(),
            entityType = EntityType.ASSOCIATION,
            operation = PendingOperation.DELETE
        )

        triggerAssociationSync()
        
    }

    override suspend fun createFromSync(note: JournalNote) {
        when (note) {
            is JournalNote.Text -> textNoteDao.addNote(note.toEntity())
            is JournalNote.Image -> imageNoteDao.addNote(note.toEntity())
            is JournalNote.Audio -> audioNoteDao.addNote(note.toEntity())
            is JournalNote.Video -> videoNoteDao.addNote(note.toEntity())
        }
    }

    override suspend fun deleteFromSync(noteId: Uuid) {
        textNoteDao.removeNote(noteId)
        imageNoteDao.removeNote(noteId)
        audioNoteDao.removeNote(noteId)
        videoNoteDao.removeNote(noteId)
    }

    override suspend fun updateSyncMetadata(note: JournalNote, syncVersion: Long, syncedAt: Instant) {
        when (note) {
            is JournalNote.Text -> textNoteDao.updateSyncMetadata(note.uid, syncVersion, syncedAt)
            is JournalNote.Image -> imageNoteDao.updateSyncMetadata(note.uid, syncVersion, syncedAt)
            is JournalNote.Audio -> audioNoteDao.updateSyncMetadata(note.uid, syncVersion, syncedAt)
            is JournalNote.Video -> videoNoteDao.updateSyncMetadata(note.uid, syncVersion, syncedAt)
        }
    }

    private fun triggerContentSync() {
        syncScope.launch {
            try {
                syncManagerProvider().syncContent()
            } catch (e: Exception) {
                Napier.w("Failed to sync content after note change", e)
            }
        }
    }

    private fun triggerAssociationSync() {
        syncScope.launch {
            try {
                syncManagerProvider().syncAssociations()
            } catch (e: Exception) {
                Napier.w("Failed to sync associations after journal change", e)
            }
        }
    }

    override suspend fun exportContentToFile(
        destination: String,
        overwrite: Boolean,
        startTimestamp: Instant,
        endTimestamp: Instant,
    ) {
        // Get all notes within the time range
        val textNotes = textNoteDao.getNotesInRange(
            startTimestamp.toEpochMilliseconds(), 
            endTimestamp.toEpochMilliseconds()
        ).first().map { it.toModel() }
        
        val imageNotes = imageNoteDao.getNotesInRange(
            startTimestamp.toEpochMilliseconds(), 
            endTimestamp.toEpochMilliseconds()
        ).first().map { it.toModel() }
        
        val audioNotes = audioNoteDao.getNotesInRange(
            startTimestamp.toEpochMilliseconds(), 
            endTimestamp.toEpochMilliseconds()
        ).first().map { it.toModel() }

        val videoNotes = videoNoteDao.getNotesInRange(
            startTimestamp.toEpochMilliseconds(),
            endTimestamp.toEpochMilliseconds()
        ).first().map { it.toModel() }
        
        // Combine all notes
        val allNotes = textNotes + imageNotes + audioNotes + videoNotes
        
        // Get all journals
        val journals = journalRepository.allJournalsObserved.first()
        
        // Create a map of journal ID to note IDs
        val journalToNotesMap = mutableMapOf<Uuid, List<Uuid>>()
        
        // For each journal, get all its notes and add them to the map
        journals.forEach { journal ->
            // Get notes for this journal
            val journalNotes = observeNotesInJournal(journal.id).first()
            
            // Map to just the IDs
            val noteIds = journalNotes.map { it.uid }
            
            // Only add to map if there are notes
            if (noteIds.isNotEmpty()) {
                journalToNotesMap[journal.id] = noteIds
            }
        }
        
        // Create export structure
        val backup = JournalContentBackup(
            notes = allNotes,
            journalToNotesMap = journalToNotesMap,
            generated = Clock.System.now()
        )
        
        // Serialize to JSON
        val json = Json {
            prettyPrint = true
            encodeDefaults = true
        }
        val jsonContent = json.encodeToString(backup)
        
        // Write to file
        val file = File(destination)
        if (file.exists() && !overwrite) {
            throw IllegalStateException("File already exists and overwrite is set to false.")
        }
        
        file.writeText(jsonContent)
    }
}

@Serializable
data class JournalContentBackup(
    val notes: List<JournalNote>,
    @Serializable(with = UuidToUuidListMapSerializer::class)
    val journalToNotesMap: Map<Uuid, List<Uuid>> = emptyMap(),
    val generated: Instant = Clock.System.now(),
    val version: String = "1.0"
)
