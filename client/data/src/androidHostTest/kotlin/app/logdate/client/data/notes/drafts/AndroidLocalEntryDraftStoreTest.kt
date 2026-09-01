package app.logdate.client.data.notes.drafts

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.uuid.Uuid

class AndroidLocalEntryDraftStoreTest {
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
) : DataStore<Preferences> {
    private val state = MutableStateFlow(initial)

    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
        transform(state.value).also { state.value = it }
}
