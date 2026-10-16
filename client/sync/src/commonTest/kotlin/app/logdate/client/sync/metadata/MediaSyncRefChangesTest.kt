package app.logdate.client.sync.metadata

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import app.logdate.client.datastore.DataStoreKeyValueStorage
import app.logdate.client.datastore.KeyValueStorage
import app.logdate.client.datastore.PrefixedKeyValueStorage
import app.logdate.client.sync.InMemoryKeyValueStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.uuid.Uuid

class MediaSyncRefChangesTest {
    private val origin = "https://journal.example"
    private val ref = MediaSyncRef(Uuid.random().toString(), "local", "$origin/media/a", "a", 1)

    @Test
    fun `production preference write failures never publish a durable media mapping`() =
        runTest {
            for (prefixed in listOf(false, true)) {
                val data =
                    object : DataStore<Preferences> {
                        override val data: Flow<Preferences> = flow { emit(emptyPreferences()) }

                        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
                            error("preferences write failed")
                    }
                val storage = DataStoreKeyValueStorage(data)
                val store =
                    KeyValueMediaSyncRefStore(
                        if (prefixed) PrefixedKeyValueStorage(storage, "watch.") else storage,
                        { origin },
                        { "owner" },
                    )
                assertFailsWith<IllegalStateException> { store.upsert(ref) }
                assertEquals(0L, store.changes.first())
            }
        }

    @Test
    fun `production preference read failures cannot be mistaken for an absent mapping`() =
        runTest {
            val data =
                object : DataStore<Preferences> {
                    override val data: Flow<Preferences> = flow { error("preferences read failed") }

                    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
                        error("must not write after a failed read")
                }
            val store = KeyValueMediaSyncRefStore(DataStoreKeyValueStorage(data), { origin }, { "owner" })
            assertFailsWith<IllegalStateException> { store.upsert(ref) }
            assertEquals(0L, store.changes.first())
        }

    @Test
    fun `rapid durable inventory changes remain visible when collection resumes later`() =
        runTest {
            val store = KeyValueMediaSyncRefStore(InMemoryKeyValueStorage(), { origin }, { "owner" })
            store.upsert(ref)
            store.upsert(ref.copy(localUri = "recovered-original"))
            assertEquals(2L, store.changes.first())
        }

    @Test
    fun `unchanged mappings do not wake repeated foreground recovery`() =
        runTest {
            val store = KeyValueMediaSyncRefStore(InMemoryKeyValueStorage(), { origin }, { "owner" })
            store.upsert(ref)
            store.upsert(ref)
            assertEquals(1L, store.changes.first())
        }

    @Test
    fun `failed reference persistence cannot publish a recoverable inventory`() =
        runTest {
            val storage =
                object : KeyValueStorage by InMemoryKeyValueStorage() {
                    override suspend fun putStringOrThrow(
                        key: String,
                        value: String,
                    ) {
                        error("disk unavailable")
                    }
                }
            val store = KeyValueMediaSyncRefStore(storage, { origin }, { "owner" })
            assertFailsWith<IllegalStateException> { store.upsert(ref) }
            assertEquals(0L, store.changes.first())
        }

    @Test
    fun `a changed draft original also publishes inventory readiness`() =
        runTest {
            val store = KeyValueMediaSyncRefStore(InMemoryKeyValueStorage(), { origin }, { "owner" })
            store.upsertDraftAsset(Uuid.random(), Uuid.random(), "video", ref)
            assertEquals(1L, store.changes.first())
        }
}
