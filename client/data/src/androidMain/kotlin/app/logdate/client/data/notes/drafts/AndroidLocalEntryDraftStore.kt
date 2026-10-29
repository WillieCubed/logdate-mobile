package app.logdate.client.data.notes.drafts

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.logdate.client.device.restore.admitNativeContent
import app.logdate.client.media.storage.MediaClaimFence
import app.logdate.client.repository.journals.EntryDraft
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import java.io.File
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.uuid.Uuid

private val Context.entryDraftDataStore by preferencesDataStore(name = "entry_drafts")

/**
 * Android implementation of the LocalEntryDraftStore using DataStore.
 */
class AndroidLocalEntryDraftStore internal constructor(
    private val dataStore: DataStore<Preferences>,
    private val admitContent: suspend () -> Unit = {},
    private val claimFence: MediaClaimFence = MediaClaimFence.Disabled,
) : LocalEntryDraftStore,
    RecoveryEntryDraftStore,
    EntryDraftMutationGuard {
    constructor(
        context: Context,
        claimFence: MediaClaimFence = MediaClaimFence.Disabled,
    ) : this(context.entryDraftDataStore, { admitNativeContent(File(context.filesDir, "datastore")) }, claimFence)

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun <T> withDraftMutation(
        admitContent: Boolean,
        block: suspend () -> T,
    ): T {
        if (admitContent && currentCoroutineContext()[AdmittedDraftStore]?.store !== this) {
            this.admitContent()
            return withContext(AdmittedDraftStore(this)) { claimFence.withClaim(block) }
        }
        return claimFence.withClaim(block)
    }

    override suspend fun insertIfAbsentForRecovery(
        draft: EntryDraft,
        canCommit: () -> Boolean,
    ): Boolean {
        val key = stringPreferencesKey(draft.id.toString())
        val serializedDraft = json.encodeToString(draft)
        var inserted = false
        dataStore.edit { preferences ->
            if (!canCommit() || preferences.contains(key)) return@edit
            preferences[key] = serializedDraft
            if (!canCommit()) throw CancellationException("Draft recovery authority changed")
            inserted = true
        }
        return inserted
    }

    override suspend fun insertForNativeTransferRecovery(
        draft: EntryDraft,
        transferMarker: String,
        canCommit: () -> Boolean,
    ): Boolean {
        require(transferMarker == draft.id.toString())
        val key = stringPreferencesKey(draft.id.toString())
        val marker = nativeTransferMarkerKey(draft.id)
        var admitted = false
        dataStore.edit { preferences ->
            if (preferences[marker] == true) {
                admitted = true
                return@edit
            }
            if (!canCommit() || preferences.contains(key)) return@edit
            preferences[key] = json.encodeToString(draft)
            preferences[marker] = true
            if (!canCommit()) throw CancellationException("Draft recovery authority changed")
            admitted = true
        }
        return admitted
    }

    override suspend fun isNativeTransferRecoveryAdmitted(
        draftId: Uuid,
        transferMarker: String,
    ): Boolean {
        if (transferMarker != draftId.toString()) return false
        return dataStore.data.first()[nativeTransferMarkerKey(draftId)] == true
    }

    override suspend fun compareAndSetForRecovery(
        expected: EntryDraft,
        replacement: EntryDraft,
        canCommit: () -> Boolean,
    ): Boolean {
        require(expected.id == replacement.id) { "Recovery cannot change draft identity" }
        val key = stringPreferencesKey(expected.id.toString())
        val completeJson = Json(json) { encodeDefaults = true }
        val expectedJson = completeJson.encodeToJsonElement(expected)
        val replacementJson = completeJson.encodeToJsonElement(replacement)
        var applied = false
        claimFence.withClaim {
            dataStore.edit { preferences ->
                if (!canCommit()) return@edit
                val current = preferences[key]?.let { decodeDraft(it, expected.id.toString()) }
                if (current != expected) return@edit
                if (!canCommit()) return@edit
                val raw = json.parseToJsonElement(requireNotNull(preferences[key]))
                preferences[key] = patchDraftRecoveryJson(raw, expectedJson, replacementJson).toString()
                if (!canCommit()) throw CancellationException("Draft recovery authority changed")
                applied = true
            }
        }
        return applied
    }

    override suspend fun saveDraft(draft: EntryDraft) {
        withDraftMutation(admitContent = true) {
            val key = stringPreferencesKey(draft.id.toString())
            val serializedDraft = json.encodeToString(draft)
            dataStore.edit { preferences -> preferences[key] = serializedDraft }
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

        claimFence.withClaim {
            dataStore.edit { preferences ->
                if (preferences.contains(key)) {
                    preferences.remove(key)
                    deleted = true
                }
            }
        }

        return deleted
    }

    override suspend fun clearAllDrafts() {
        claimFence.withClaim {
            dataStore.edit { preferences ->
                preferences.asMap().filterValues { it is String }.keys.forEach { key ->
                    preferences.remove(stringPreferencesKey(key.name))
                }
            }
        }
    }

    private class AdmittedDraftStore(
        val store: AndroidLocalEntryDraftStore,
    ) : AbstractCoroutineContextElement(AdmittedDraftStore) {
        companion object : CoroutineContext.Key<AdmittedDraftStore>
    }

    private fun nativeTransferMarkerKey(id: Uuid) = booleanPreferencesKey("native_transfer_admitted.$id")

    private fun decodeDraft(
        serialized: String,
        draftIdentity: String? = null,
    ): EntryDraft =
        try {
            json.decodeFromString<EntryDraft>(serialized)
        } catch (_: Exception) {
            val identitySuffix = draftIdentity?.let { " $it" }.orEmpty()
            Napier.e("Failed to read local draft$identitySuffix")
            throw EntryDraftStorageException("Local draft$identitySuffix is unreadable")
        }
}
