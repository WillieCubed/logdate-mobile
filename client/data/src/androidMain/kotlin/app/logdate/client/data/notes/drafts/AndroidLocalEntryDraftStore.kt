package app.logdate.client.data.notes.drafts

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.logdate.client.repository.journals.EntryDraft
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.uuid.Uuid

private val Context.entryDraftDataStore by preferencesDataStore(name = "entry_drafts")

/**
 * Android implementation of the LocalEntryDraftStore using DataStore.
 */
class AndroidLocalEntryDraftStore internal constructor(
    private val dataStore: DataStore<Preferences>,
) : LocalEntryDraftStore {
    constructor(context: Context) : this(context.entryDraftDataStore)

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun saveDraft(draft: EntryDraft) {
        val key = stringPreferencesKey(draft.id.toString())
        val serializedDraft = json.encodeToString(draft)

        dataStore.edit { preferences ->
            preferences[key] = serializedDraft
        }
    }

    override suspend fun getDraft(id: Uuid): EntryDraft? {
        val key = stringPreferencesKey(id.toString())
        val preferences = dataStore.data.first()

        return preferences[key]?.let { serialized -> decodeDraft(serialized, id.toString()) }
    }

    override suspend fun getAllDrafts(): List<EntryDraft> {
        val preferences = dataStore.data.first()

        return preferences
            .asMap()
            .values
            .filterIsInstance<String>()
            .map { serialized -> decodeDraft(serialized) }
    }

    override suspend fun deleteDraft(id: Uuid): Boolean {
        val key = stringPreferencesKey(id.toString())
        var deleted = false

        dataStore.edit { preferences ->
            if (preferences.contains(key)) {
                preferences.remove(key)
                deleted = true
            }
        }

        return deleted
    }

    override suspend fun clearAllDrafts() {
        dataStore.edit { preferences ->
            preferences.clear()
        }
    }

    private fun decodeDraft(
        serialized: String,
        draftIdentity: String? = null,
    ): EntryDraft =
        try {
            json.decodeFromString<EntryDraft>(serialized)
        } catch (e: Exception) {
            val identitySuffix = draftIdentity?.let { " $it" }.orEmpty()
            Napier.e("Failed to read local draft$identitySuffix", e)
            throw EntryDraftStorageException("Local draft$identitySuffix is unreadable", e)
        }
}
