package app.logdate.client.data.journals

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.logdate.client.data.notes.quarantineNativeDraft
import app.logdate.client.media.storage.UnavailableMediaReferences
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlin.uuid.Uuid

/** Rewrites retained draft scopes before any account can expose source-device provider IDs. */
class NativeDraftMediaQuarantine(
    private val storage: DataStore<Preferences>,
    private val json: Json,
) {
    suspend fun run(
        isCurrent: () -> Boolean,
        quarantine: suspend (String) -> String? = { reference ->
            reference
                .takeIf(UnavailableMediaReferences::requiresNativeQuarantine)
                ?.let(UnavailableMediaReferences::nativeRestore)
        },
    ) {
        LocalFirstDraftRepository.storageMutex.withLock {
            check(isCurrent()) { "Native media delivery changed" }
            storage.edit { preferences ->
                preferences.asMap().toList().forEach { (key, value) ->
                    val name = key.name
                    if (value is String &&
                        (name == "editor_drafts" || name == "editor_drafts_anonymous" || name.startsWith("editor_drafts_scope_"))
                    ) {
                        val state =
                            try {
                                ArchiveDraftStorageState.decode(json, value)
                            } catch (_: IllegalArgumentException) {
                                val retained = "editor_drafts_native_unparsed_$name"
                                val existing = preferences[stringPreferencesKey(retained)]
                                val key = if (existing == null || existing == value) retained else "${retained}_${Uuid.random()}"
                                preferences[stringPreferencesKey(key)] = value
                                PartialDraftStorageRecovery.recover(json, value)
                            }
                        val repairedDrafts =
                            buildList {
                                for (draft in state.drafts) add(quarantineNativeDraft(draft, quarantine))
                            }
                        val rewritten = state.copy(drafts = repairedDrafts)
                        val encoded = rewritten.encode(json)
                        if (encoded != value) preferences[stringPreferencesKey(name)] = encoded
                    }
                }
                check(isCurrent()) { "Native media delivery changed" }
            }
        }
    }
}
