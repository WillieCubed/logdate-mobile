package app.logdate.client.data.notes.drafts

import app.logdate.client.repository.journals.EntryDraft
import app.logdate.client.repository.journals.JournalNote
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Paths
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.uuid.Uuid

/**
 * Desktop implementation of LocalEntryDraftStore using file system storage.
 */
class DesktopLocalEntryDraftStore : LocalEntryDraftStore {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    
    // Directory structure for storing drafts
    private val appDataDir = System.getProperty("user.home") + File.separator + ".logdate"
    private val draftsDir = appDataDir + File.separator + "drafts"
    private val indexFile = draftsDir + File.separator + "index.json"
    
    init {
        // Ensure directories exist
        val path = Paths.get(draftsDir)
        if (!path.exists()) {
            path.createDirectories()
        }
    }
    
    /**
     * Serializable version of EntryDraft for storage
     */
    @Serializable
    private data class SerializableEntryDraft(
        val id: String,
        val notes: List<JournalNote>,
        val createdAt: Long,
        val updatedAt: Long
    )
    
    private fun EntryDraft.toSerializable(): SerializableEntryDraft {
        return SerializableEntryDraft(
            id = id.toString(),
            notes = notes,
            createdAt = createdAt.toEpochMilliseconds(),
            updatedAt = updatedAt.toEpochMilliseconds()
        )
    }
    
    private fun SerializableEntryDraft.toDomain(): EntryDraft {
        return EntryDraft(
            id = Uuid.parse(id),
            notes = notes,
            createdAt = Instant.fromEpochMilliseconds(createdAt),
            updatedAt = Instant.fromEpochMilliseconds(updatedAt)
        )
    }
    
    private fun getDraftFile(id: Uuid): File {
        return File(draftsDir + File.separator + id.toString() + ".json")
    }
    
    private fun getDraftIndex(): List<String> {
        val indexFilePath = File(indexFile)
        if (!indexFilePath.exists()) {
            return emptyList()
        }
        
        return try {
            val content = indexFilePath.readText()
            if (content.isBlank()) {
                emptyList()
            } else {
                json.decodeFromString<List<String>>(content)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    private fun saveDraftIndex(ids: List<String>) {
        val indexFilePath = File(indexFile)
        indexFilePath.writeText(json.encodeToString(ids))
    }
    
    override suspend fun saveDraft(draft: EntryDraft) {
        val draftFile = getDraftFile(draft.id)
        val serializedDraft = json.encodeToString(draft.toSerializable())
        
        draftFile.writeText(serializedDraft)
        
        // Update index
        val currentIndex = getDraftIndex()
        if (!currentIndex.contains(draft.id.toString())) {
            val updatedIndex = currentIndex + draft.id.toString()
            saveDraftIndex(updatedIndex)
        }
    }
    
    override suspend fun getDraft(id: Uuid): EntryDraft? {
        val draftFile = getDraftFile(id)
        if (!draftFile.exists()) {
            return null
        }
        
        return try {
            val content = draftFile.readText()
            val serializedDraft = json.decodeFromString<SerializableEntryDraft>(content)
            serializedDraft.toDomain()
        } catch (e: Exception) {
            null
        }
    }
    
    override suspend fun getAllDrafts(): List<EntryDraft> {
        val draftIds = getDraftIndex()
        return draftIds.mapNotNull { id ->
            try {
                val draftFile = File(draftsDir + File.separator + id + ".json")
                if (draftFile.exists()) {
                    val content = draftFile.readText()
                    val serializedDraft = json.decodeFromString<SerializableEntryDraft>(content)
                    serializedDraft.toDomain()
                } else null
            } catch (e: Exception) {
                null
            }
        }
    }
    
    override suspend fun deleteDraft(id: Uuid): Boolean {
        val draftFile = getDraftFile(id)
        val exists = draftFile.exists()
        
        if (exists) {
            draftFile.delete()
            
            // Update index
            val currentIndex = getDraftIndex()
            val updatedIndex = currentIndex.filter { it != id.toString() }
            saveDraftIndex(updatedIndex)
        }
        
        return exists
    }
    
    override suspend fun clearAllDrafts() {
        val draftIds = getDraftIndex()
        
        // Delete all draft files
        draftIds.forEach { id ->
            val file = File(draftsDir + File.separator + id + ".json")
            if (file.exists()) {
                file.delete()
            }
        }
        
        // Clear index
        saveDraftIndex(emptyList())
    }
}
