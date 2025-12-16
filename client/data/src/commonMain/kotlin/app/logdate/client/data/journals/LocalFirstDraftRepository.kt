package app.logdate.client.data.journals

import app.logdate.client.datastore.KeyValueStorage
import app.logdate.client.repository.journals.DraftRepository
import app.logdate.shared.model.EditorDraft
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.uuid.Uuid

/**
 * Implementation of [DraftRepository] that stores drafts using platform-agnostic [KeyValueStorage].
 * This provides a consistent API across all platforms with local-first data management.
 */
class LocalFirstDraftRepository(
    private val keyValueStorage: KeyValueStorage,
    private val json: Json
) : DraftRepository {

    companion object {
        private const val KEY_DRAFTS = "editor_drafts"
    }

    override suspend fun saveDraft(draft: EditorDraft) {
        try {
            Napier.d("Saving draft with ID: ${draft.id}, blocks: ${draft.blocks.size}")
            
            // Get existing drafts
            val existingDrafts = getAllDrafts().toMutableList()
            
            // Update existing draft or add new one
            val index = existingDrafts.indexOfFirst { it.id == draft.id }
            if (index >= 0) {
                // Update existing draft with new lastModifiedAt timestamp
                existingDrafts[index] = draft.copy(lastModifiedAt = Clock.System.now())
                Napier.d("Updated existing draft at index $index")
            } else {
                // Add new draft
                existingDrafts.add(draft)
                Napier.d("Added new draft, total drafts: ${existingDrafts.size}")
            }
            
            // Save all drafts
            val draftsJson = json.encodeToString(existingDrafts)
            Napier.d("Draft JSON length: ${draftsJson.length}")
            
            // Save to KeyValueStorage
            keyValueStorage.putString(KEY_DRAFTS, draftsJson)
            
            Napier.d("Successfully saved draft ${draft.id}")
        } catch (e: Exception) {
            Napier.e("Error saving draft: ${e.message}", e)
        }
    }

    override suspend fun getLatestDraft(): EditorDraft? {
        try {
            Napier.d("Getting latest draft...")
            val allDrafts = getAllDrafts()
            Napier.d("Found ${allDrafts.size} drafts")
            
            if (allDrafts.isEmpty()) {
                Napier.d("No drafts found")
                return null
            }
            
            val latestDraft = allDrafts.maxByOrNull { it.lastModifiedAt }
            Napier.d("Latest draft ID: ${latestDraft?.id}, blocks: ${latestDraft?.blocks?.size}")
            return latestDraft
        } catch (e: Exception) {
            Napier.e("Error getting latest draft: ${e.message}", e)
            return null
        }
    }

    override suspend fun getAllDrafts(): List<EditorDraft> {
        try {
            // Get the drafts JSON from KeyValueStorage
            val draftsJson = keyValueStorage.getString(KEY_DRAFTS) ?: ""
                
            if (draftsJson.isEmpty()) {
                Napier.d("No drafts JSON found in storage")
                return emptyList()
            }
            
            Napier.d("Found drafts JSON, length: ${draftsJson.length}")
            
            // Parse the JSON into EditorDraft objects
            return try {
                val drafts = json.decodeFromString<List<EditorDraft>>(draftsJson)
                Napier.d("Successfully decoded ${drafts.size} drafts")
                drafts
            } catch (e: Exception) {
                Napier.e("Error decoding drafts JSON: ${e.message}", e)
                emptyList()
            }
        } catch (e: Exception) {
            Napier.e("Error getting all drafts: ${e.message}", e)
            return emptyList()
        }
    }

    override val allDrafts: Flow<List<EditorDraft>> = keyValueStorage.observeString(KEY_DRAFTS)
        .map { draftsJson ->
            try {
                if (draftsJson.isNullOrEmpty()) {
                    Napier.d("Flow: No drafts JSON found in storage")
                    emptyList()
                } else {
                    Napier.d("Flow: Found drafts JSON, length: ${draftsJson.length}")
                    try {
                        val drafts = json.decodeFromString<List<EditorDraft>>(draftsJson)
                        Napier.d("Flow: Successfully decoded ${drafts.size} drafts")
                        drafts
                    } catch (e: Exception) {
                        Napier.e("Flow: Error decoding drafts JSON: ${e.message}", e)
                        emptyList()
                    }
                }
            } catch (e: Exception) {
                Napier.e("Flow: Error getting all drafts: ${e.message}", e)
                emptyList()
            }
        }

    override suspend fun getDraft(id: Uuid): EditorDraft? {
        try {
            Napier.d("Getting draft with ID: $id")
            val draft = getAllDrafts().find { it.id == id }
            if (draft != null) {
                Napier.d("Found draft with ID: $id")
            } else {
                Napier.d("No draft found with ID: $id")
            }
            return draft
        } catch (e: Exception) {
            Napier.e("Error getting draft with ID $id: ${e.message}", e)
            return null
        }
    }

    override suspend fun deleteDraft(id: Uuid) {
        try {
            Napier.d("Deleting draft with ID: $id")
            val existingDrafts = getAllDrafts().toMutableList()
            val initialSize = existingDrafts.size
            existingDrafts.removeAll { it.id == id }
            
            if (initialSize == existingDrafts.size) {
                Napier.d("No draft found with ID: $id to delete")
                return
            }
            
            val draftsJson = json.encodeToString(existingDrafts)
            keyValueStorage.putString(KEY_DRAFTS, draftsJson)
            Napier.d("Successfully deleted draft with ID: $id")
        } catch (e: Exception) {
            Napier.e("Error deleting draft with ID $id: ${e.message}", e)
        }
    }

    override suspend fun clearAllDrafts() {
        try {
            Napier.d("Clearing all drafts")
            keyValueStorage.putString(KEY_DRAFTS, json.encodeToString(emptyList<EditorDraft>()))
            Napier.d("Successfully cleared all drafts")
        } catch (e: Exception) {
            Napier.e("Error clearing all drafts: ${e.message}", e)
        }
    }
}