package app.logdate.client.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import app.logdate.shared.model.profile.LogDateProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArchiveProfileRecoveryStoreTest {
    @Test
    fun `profile receipt survives a later clear and prevents replay after restart`() =
        runTest {
            val store = MemoryPreferences()
            val source = LogdatePreferencesDataSource(store)
            val profile = LogDateProfile(displayName = "Restored name")
            assertTrue(source.archiveProfileRecoveryStore().compareAndSet(LogDateProfile(), profile, "owner:origin:recovery") {})
            source.updateDisplayName("").getOrThrow()
            val restarted = LogdatePreferencesDataSource(store)
            assertFalse(restarted.archiveProfileRecoveryStore().compareAndSet(LogDateProfile(), profile, "owner:origin:recovery") {})
            assertEquals("", restarted.getCurrentUserData().displayName)
        }

    @Test
    fun `profile recovery cannot overwrite a concurrent edit`() =
        runTest {
            val source = LogdatePreferencesDataSource(MemoryPreferences())
            source.updateDisplayName("New local name").getOrThrow()
            assertFalse(
                source.archiveProfileRecoveryStore().compareAndSet(
                    LogDateProfile(),
                    LogDateProfile(displayName = "Old name"),
                    "receipt",
                ) {},
            )
            assertEquals("New local name", source.getCurrentUserData().displayName)
        }

    private class MemoryPreferences : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        override val data = state

        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            transform(state.value).also {
                state.value =
                    it
            }
    }
}
