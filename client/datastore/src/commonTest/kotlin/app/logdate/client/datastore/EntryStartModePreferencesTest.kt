package app.logdate.client.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class EntryStartModePreferencesTest {
    @Test
    fun `fresh preferences start with text`() =
        runTest {
            assertEquals(EntryStartMode.TEXT, LogdatePreferencesDataSource(Store()).observeEntryStartMode().first())
        }

    @Test
    fun `every capture default survives reopening preferences`() =
        runTest {
            val store = Store()
            for (mode in EntryStartMode.entries) {
                LogdatePreferencesDataSource(store).setEntryStartMode(mode)
                assertEquals(mode, LogdatePreferencesDataSource(store).observeEntryStartMode().first())
            }
        }

    @Test
    fun `an unrecognized saved mode falls back to text`() =
        runTest {
            val store = Store()
            val modeKey = stringPreferencesKey("entry_default_capture_mode")
            store.updateData {
                it.toMutablePreferences().apply { this[modeKey] = "FUTURE_MODE" }
            }
            assertEquals(EntryStartMode.TEXT, LogdatePreferencesDataSource(store).observeEntryStartMode().first())
        }

    private class Store : DataStore<Preferences> {
        private val values = MutableStateFlow(emptyPreferences())
        override val data: Flow<Preferences> = values

        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            transform(values.value).also {
                values.value =
                    it
            }
    }
}
