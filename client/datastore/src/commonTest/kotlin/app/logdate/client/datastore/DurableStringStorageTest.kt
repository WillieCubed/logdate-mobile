package app.logdate.client.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith

class DurableStringStorageTest {
    @Test
    fun `durable read propagates a storage failure instead of reporting missing content`() =
        runTest {
            val store =
                object : DataStore<Preferences> {
                    override val data: Flow<Preferences> = flow { throw IllegalStateException("Unavailable preferences") }

                    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
                        transform(emptyPreferences())
                }
            assertFailsWith<IllegalStateException> { DataStoreKeyValueStorage(store).getStringOrThrow("editor_drafts") }
        }

    @Test
    fun `durable write propagates a failure instead of acknowledging unpersisted content`() =
        runTest {
            val store =
                object : DataStore<Preferences> {
                    override val data: Flow<Preferences> = flow { emit(emptyPreferences()) }

                    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
                        throw IllegalStateException("Preferences write failed")
                }
            assertFailsWith<IllegalStateException> { DataStoreKeyValueStorage(store).putStringOrThrow("editor_drafts", "retained") }
        }
}
