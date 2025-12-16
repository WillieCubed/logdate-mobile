package app.logdate.client.data.notes.drafts

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.logdate.client.repository.journals.EntryDraft
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.uuid.Uuid

/**
 * Android implementation of the LocalEntryDraftStore using DataStore.
 */
class AndroidLocalEntryDraftStore(
    private val context: Context
) : LocalEntryDraftStore {
    
    private val Context.dataStore by preferencesDataStore(name = "entry_drafts")
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun saveDraft(draft: EntryDraft) {
        val key = stringPreferencesKey(draft.id.toString())
        val serializedDraft = json.encodeToString(draft)
        
        context.dataStore.edit { preferences ->
            preferences[key] = serializedDraft
        }
    }
    
    override suspend fun getDraft(id: Uuid): EntryDraft? {
        val key = stringPreferencesKey(id.toString())
        val preferences = context.dataStore.data.first()
        
        return preferences[key]?.let { serialized ->
            json.decodeFromString<EntryDraft>(serialized)
        }
    }
    
    override suspend fun getAllDrafts(): List<EntryDraft> {
        val preferences = context.dataStore.data.first()
        
        return preferences.asMap().values
            .filterIsInstance<String>()
            .mapNotNull { serialized ->
                try {
                    json.decodeFromString<EntryDraft>(serialized)
                } catch (e: Exception) {
                    null
                }
            }
    }
    
    override suspend fun deleteDraft(id: Uuid): Boolean {
        val key = stringPreferencesKey(id.toString())
        var deleted = false
        
        context.dataStore.edit { preferences ->
            if (preferences.contains(key)) {
                preferences.remove(key)
                deleted = true
            }
        }
        
        return deleted
    }
    
    override suspend fun clearAllDrafts() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }
}