package app.logdate.client.data.restore

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import app.logdate.client.data.journals.ArchiveDraftStorageState
import app.logdate.client.device.identity.sameOwnerId
import app.logdate.client.repository.journals.DraftRecoveryScope
import app.logdate.shared.model.EditorDraft
import kotlinx.serialization.json.Json

internal fun RetainedJournalSourceScope.matchesDraftRecoveryScope(
    current: RetainedJournalSourceScope,
    draft: DraftRecoveryScope,
): Boolean {
    if (!matchesCurrent(current) || normalizeBackendOrigin(backendOrigin) != backendOrigin) return false
    if (!sameOwnerId(draft.outboxOwnerId, current.ownerId) ||
        normalizeBackendOrigin(draft.outboxServerOrigin.orEmpty()) != current.backendOrigin
    ) {
        return false
    }
    return if (bound) {
        sameOwnerId(draft.ownerId, current.ownerId) &&
            normalizeBackendOrigin(draft.serverOrigin.orEmpty()) == current.backendOrigin
    } else {
        draft.ownerId == null && draft.serverOrigin == null
    }
}

internal fun RetainedJournalSourceScope.retainedDraftStorageKey(): String? {
    val owner = ownerId?.takeIf { it.isNotBlank() } ?: return null
    if (normalizeBackendOrigin(backendOrigin) != backendOrigin) return null
    if (!bound) return "editor_drafts_anonymous"
    val origin = draftStorageOrigin
    if (normalizeBackendOrigin(origin) != backendOrigin) return null
    return "editor_drafts_scope_${owner.length}:$owner:${origin.length}:$origin"
}

internal fun isRetainedDraftStorageKey(name: String): Boolean =
    name == "editor_drafts" || name == "editor_drafts_anonymous" || name.startsWith("editor_drafts_scope_")

internal data class RetainedDraftContents(
    val drafts: List<EditorDraft>,
    val covered: Boolean,
)

internal fun readRetainedDraftContents(
    preferences: Preferences,
    key: String,
    json: Json,
): RetainedDraftContents {
    val selected = ArchiveDraftStorageState.decode(json, preferences[stringPreferencesKey(key)])
    val selectedCovered = selected.envelopeCovered()
    val othersCovered =
        preferences.asMap().all { (candidate, value) ->
            if (candidate.name == key || !isRetainedDraftStorageKey(candidate.name)) {
                true
            } else {
                val state = runCatching { ArchiveDraftStorageState.decode(json, value as String) }.getOrNull()
                state != null && state.drafts.isEmpty() && state.envelopeCovered()
            }
        }
    return RetainedDraftContents(selected.drafts, selectedCovered && othersCovered)
}

private fun ArchiveDraftStorageState.envelopeCovered(): Boolean =
    appliedRecoveries.isEmpty() && localDeletionIds.isEmpty() && custodyCopies.isEmpty() && pendingMutations.isEmpty()
