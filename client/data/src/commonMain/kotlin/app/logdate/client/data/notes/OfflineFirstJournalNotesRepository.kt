package app.logdate.client.data.notes

import app.logdate.client.database.dao.ImageNoteDao
import app.logdate.client.database.dao.JournalNotesDao
import app.logdate.client.database.dao.TextNoteDao
import app.logdate.client.database.dao.AudioNoteDao
import app.logdate.client.repository.journals.ExportableJournalContentRepository
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.JournalRepository
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlin.uuid.Uuid

/**
 * A repository for journal notes that stores data locally.
 */
class OfflineFirstJournalNotesRepository(
    private val textNoteDao: TextNoteDao,
    private val imageNoteDao: ImageNoteDao,
    // TODO: Rename VoiceNoteDao to AudioNoteDao to better represent general audio content
    private val audioNoteDao: AudioNoteDao,
    private val journalNotesDao: JournalNotesDao,
    private val journalRepository: JournalRepository,
) : JournalNotesRepository, ExportableJournalContentRepository {

    override val allNotesObserved: Flow<List<JournalNote>> =
        textNoteDao.getAllNotes().combine(
            imageNoteDao.getAllNotes().combine(
                audioNoteDao.getAllNotes()
            ) { imageNotes, voiceNotes ->
                imageNotes.map { it.toModel() } + voiceNotes.map { it.toModel() }
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
                imageNoteDao.getNotesInRange(startMillis, endMillis).combine(
                    audioNoteDao.getNotesInRange(startMillis, endMillis)
                ) { imageNotes, voiceNotes ->
                    imageNotes.map { it.toModel() } + voiceNotes.map { it.toModel() }
                }
            ) { textNotes, otherNotes ->
                val result = textNotes.map { it.toModel() } + otherNotes
                result
            }
    }

    override fun observeNotesPage(pageSize: Int, offset: Int): Flow<List<JournalNote>> {
        // Since VoiceNoteDao doesn't have getNotesPage, we'll use getAllNotes and handle pagination in memory
        return textNoteDao.getNotesPage(pageSize, offset)
            .combine(imageNoteDao.getNotesPage(pageSize, offset)) { textNotes, imageNotes ->
                val textModels = textNotes.map { it.toModel() }
                val imageModels = imageNotes.map { it.toModel() }
                textModels + imageModels
            }.combine(audioNoteDao.getAllNotes()) { initialNotes, voiceNotes ->
                val voiceModels = voiceNotes.map { it.toModel() }
                (initialNotes + voiceModels)
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
                imageNoteDao.getRecentNotes(limit).combine(
                    audioNoteDao.getRecentNotes(limit)
                ) { imageNotes, voiceNotes ->
                    imageNotes.map { it.toModel() } + voiceNotes.map { it.toModel() }
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
            
            is JournalNote.Video -> TODO()
        }
        
        
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
            
            is JournalNote.Video -> TODO()
        }
        
    }

    override suspend fun removeById(noteId: Uuid) {
        // TODO: Properly handle deletes, probably need a metadata table just to store the type of note.
        // For now, try removing from all types of note tables
        textNoteDao.removeNote(noteId)
        imageNoteDao.removeNote(noteId)
        audioNoteDao.removeNote(noteId)
        
    }

    override suspend fun create(note: JournalNote, journalId: Uuid) {
        // Add the note first
        create(note)
        
        // Link it to the journal
        journalNotesDao.addNoteToJournal(journalId, note.uid)
        
    }

    override suspend fun removeFromJournal(noteId: Uuid, journalId: Uuid) {
        journalNotesDao.removeNoteFromJournal(journalId, noteId)
        
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
        
        // Combine all notes
        val allNotes = textNotes + imageNotes + audioNotes
        
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
        val json = kotlinx.serialization.json.Json {
            prettyPrint = true
            encodeDefaults = true
        }
        val jsonContent = json.encodeToString(backup)
        
        // Write to file
        val file = java.io.File(destination)
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