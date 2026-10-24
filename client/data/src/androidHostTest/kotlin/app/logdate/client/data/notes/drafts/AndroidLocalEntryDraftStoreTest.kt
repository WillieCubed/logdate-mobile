package app.logdate.client.data.notes.drafts

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import app.logdate.client.media.storage.MediaClaimFence
import app.logdate.client.repository.journals.EntryDraft
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

class AndroidLocalEntryDraftStoreTest {
    @Test
    fun `draft claims remain fenced through durable preference edits`() =
        runTest {
            var claimHeld = false
            val fence =
                object : MediaClaimFence {
                    override suspend fun <T> withClaim(block: suspend () -> T): T {
                        claimHeld = true
                        return try {
                            block()
                        } finally {
                            claimHeld = false
                        }
                    }

                    override suspend fun <T> withExclusiveReclamation(block: suspend () -> T): T = error("unused")
                }
            val dataStore = InMemoryPreferencesDataStore(mutablePreferencesOf()) { assertTrue(claimHeld) }
            val store = AndroidLocalEntryDraftStore(dataStore, admitContent = { assertFalse(claimHeld) }, claimFence = fence)
            val now = Clock.System.now()
            val draft = EntryDraft(id = Uuid.random(), notes = emptyList(), createdAt = now, updatedAt = now)

            store.saveDraft(draft)
            store.deleteDraft(draft.id)
            store.clearAllDrafts()
            assertTrue(!claimHeld)
        }

    @Test
    fun `malformed snapshot surfaces recoverable error and preserves data store value`() =
        runTest {
            val draftId = Uuid.random()
            val key = stringPreferencesKey(draftId.toString())
            val malformed = "{ malformed-draft"
            val dataStore = InMemoryPreferencesDataStore(mutablePreferencesOf(key to malformed))
            val store = AndroidLocalEntryDraftStore(dataStore)

            assertFailsWith<EntryDraftStorageException> {
                store.getAllDrafts()
            }
            assertEquals(malformed, dataStore.data.first()[key])
        }
}

private class InMemoryPreferencesDataStore(
    initial: Preferences,
    private val onUpdate: () -> Unit = {},
) : DataStore<Preferences> {
    private val state = MutableStateFlow(initial)

    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
        transform(state.value).also {
            onUpdate()
            state.value = it
        }
}
