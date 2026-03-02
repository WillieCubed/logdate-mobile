package app.logdate.client.data.notes.drafts

import app.logdate.client.repository.journals.EntryDraft
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.NoteType
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import platform.Foundation.NSUserDefaults
import platform.Foundation.setValue
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * iOS implementation of LocalEntryDraftStore using NSUserDefaults.
 */
class IosLocalEntryDraftStore : LocalEntryDraftStore {
    private val userDefaults = NSUserDefaults.standardUserDefaults
    private val json = Json { ignoreUnknownKeys = true }
    private val draftsKeyPrefix = "entry_draft_"
    private val draftsIndexKey = "entry_drafts_index"

    /**
     * Serializable version of EntryDraft for storage
     */
    @Serializable
    private data class SerializableEntryDraft(
        val id: String,
        val notes: List<SerializableJournalNote>,
        val createdAt: Long,
        val updatedAt: Long,
    )

    @Serializable
    private data class SerializableJournalNote(
        val id: String,
        val type: String,
        val content: String,
        val createdAt: Long,
        val durationMs: Long = 0,
    )

    private fun EntryDraft.toSerializable(): SerializableEntryDraft =
        SerializableEntryDraft(
            id = id.toString(),
            notes = notes.map { it.toSerializable() },
            createdAt = createdAt.toEpochMilliseconds(),
            updatedAt = updatedAt.toEpochMilliseconds(),
        )

    private fun JournalNote.toSerializable(): SerializableJournalNote {
        val noteContent =
            when (this) {
                is JournalNote.Text -> this.content
                is JournalNote.Audio -> this.mediaRef
                is JournalNote.Image -> this.mediaRef
                is JournalNote.Video -> this.mediaRef
                else -> ""
            }

        return SerializableJournalNote(
            id = this.uid.toString(),
            type = this.type.toString(),
            content = noteContent,
            createdAt = this.creationTimestamp.toEpochMilliseconds(),
            durationMs = (this as? JournalNote.Audio)?.durationMs ?: 0,
        )
    }

    private fun SerializableEntryDraft.toDomain(): EntryDraft =
        EntryDraft(
            id = Uuid.parse(id),
            notes = notes.map { it.toDomain() },
            createdAt = Instant.fromEpochMilliseconds(createdAt),
            updatedAt = Instant.fromEpochMilliseconds(updatedAt),
        )

    private fun SerializableJournalNote.toDomain(): JournalNote {
        val noteType =
            try {
                NoteType.valueOf(type)
            } catch (e: IllegalArgumentException) {
                // Default to TEXT if type is not recognized
                NoteType.TEXT
            }

        val noteId =
            try {
                Uuid.parse(id)
            } catch (e: Exception) {
                Uuid.random()
            }

        val timestamp = Instant.fromEpochMilliseconds(createdAt)

        return when (noteType) {
            NoteType.TEXT ->
                JournalNote.Text(
                    uid = noteId,
                    creationTimestamp = timestamp,
                    lastUpdated = timestamp,
                    content = content,
                )
            NoteType.AUDIO ->
                JournalNote.Audio(
                    uid = noteId,
                    creationTimestamp = timestamp,
                    lastUpdated = timestamp,
                    mediaRef = content,
                    durationMs = durationMs,
                )
            NoteType.IMAGE ->
                JournalNote.Image(
                    uid = noteId,
                    creationTimestamp = timestamp,
                    lastUpdated = timestamp,
                    mediaRef = content,
                )
            NoteType.VIDEO ->
                JournalNote.Video(
                    uid = noteId,
                    creationTimestamp = timestamp,
                    lastUpdated = timestamp,
                    mediaRef = content,
                )
            else ->
                JournalNote.Text(
                    uid = noteId,
                    creationTimestamp = timestamp,
                    lastUpdated = timestamp,
                    content = content,
                )
        }
    }

    override suspend fun saveDraft(draft: EntryDraft) {
        val key = draftsKeyPrefix + draft.id.toString()
        val serializedDraft = json.encodeToString(draft.toSerializable())

        userDefaults.setValue(serializedDraft, key)

        // Update index
        val currentIndex = getDraftIndex()
        if (!currentIndex.contains(draft.id.toString())) {
            val updatedIndex = currentIndex + draft.id.toString()
            userDefaults.setValue(json.encodeToString(updatedIndex), draftsIndexKey)
        }
    }

    private fun getDraftIndex(): List<String> {
        val indexJson = userDefaults.stringForKey(draftsIndexKey) ?: return emptyList()
        return try {
            json.decodeFromString<List<String>>(indexJson)
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getDraft(id: Uuid): EntryDraft? {
        val key = draftsKeyPrefix + id.toString()
        val serialized = userDefaults.stringForKey(key) ?: return null

        return try {
            val serializedDraft = json.decodeFromString<SerializableEntryDraft>(serialized)
            serializedDraft.toDomain()
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun getAllDrafts(): List<EntryDraft> {
        val draftIds = getDraftIndex()
        return draftIds.mapNotNull { id ->
            val key = draftsKeyPrefix + id
            val serialized = userDefaults.stringForKey(key) ?: return@mapNotNull null

            try {
                val serializedDraft = json.decodeFromString<SerializableEntryDraft>(serialized)
                serializedDraft.toDomain()
            } catch (e: Exception) {
                null
            }
        }
    }

    override suspend fun deleteDraft(id: Uuid): Boolean {
        val key = draftsKeyPrefix + id.toString()
        val exists = userDefaults.stringForKey(key) != null

        if (exists) {
            userDefaults.removeObjectForKey(key)

            // Update index
            val currentIndex = getDraftIndex()
            val updatedIndex = currentIndex.filter { it != id.toString() }
            userDefaults.setValue(json.encodeToString(updatedIndex), draftsIndexKey)
        }

        return exists
    }

    override suspend fun clearAllDrafts() {
        val draftIds = getDraftIndex()

        draftIds.forEach { id ->
            userDefaults.removeObjectForKey(draftsKeyPrefix + id)
        }

        userDefaults.removeObjectForKey(draftsIndexKey)
    }
}
