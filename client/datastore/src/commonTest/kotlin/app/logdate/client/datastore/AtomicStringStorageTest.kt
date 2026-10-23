package app.logdate.client.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class AtomicStringStorageTest {
    @Test
    fun `owner and binding commit together in one transaction and one read snapshot`() =
        runTest {
            val backend = TransactionStore()
            val storage = DataStoreKeyValueStorage(backend)
            storage.updateStringsOrThrow(mapOf("owner" to "remote", "bound" to "true"))
            assertEquals(1, backend.commits)
            assertEquals(mapOf("owner" to "remote", "bound" to "true"), storage.getStringsOrThrow(setOf("owner", "bound")))
            assertEquals(1, backend.reads)
        }

    @Test
    fun `rejected binding cannot leave a committed owner-only change after recreation`() =
        runTest {
            val backend = TransactionStore(rejectBound = true)
            assertFailsWith<IllegalStateException> {
                DataStoreKeyValueStorage(backend).updateStringsOrThrow(mapOf("owner" to "remote", "bound" to "true"))
            }
            assertEquals("local", backend.peek("owner"))
            assertNull(backend.peek("bound"))
            assertEquals(0, backend.commits)
            val reopened = DataStoreKeyValueStorage(backend)
            assertEquals(mapOf("owner" to "local", "bound" to null), reopened.getStringsOrThrow(setOf("owner", "bound")))
        }

    @Test
    fun `owner and binding removal is one commit and retains unrelated values`() =
        runTest {
            val backend = TransactionStore()
            val storage = DataStoreKeyValueStorage(backend)
            storage.updateStringsOrThrow(mapOf("bound" to "true"))
            val before = backend.commits
            storage.updateStringsOrThrow(mapOf("owner" to null, "bound" to null))
            assertEquals(before + 1, backend.commits)
            assertNull(backend.peek("owner"))
            assertNull(backend.peek("bound"))
            assertEquals("keep", backend.peek("unrelated"))
        }

    @Test
    fun `prefixed snapshots and updates stay atomic and inside their namespace`() =
        runTest {
            val backend = TransactionStore()
            val storage = PrefixedKeyValueStorage(DataStoreKeyValueStorage(backend), "journal.")
            storage.updateStringsOrThrow(mapOf("owner" to "remote", "bound" to "true"))
            assertEquals(1, backend.commits)
            assertEquals(mapOf("owner" to "remote", "bound" to "true"), storage.getStringsOrThrow(setOf("owner", "bound")))
            assertEquals(1, backend.reads)
            assertEquals("local", backend.peek("owner"))
            assertEquals("remote", backend.peek("journal.owner"))
            assertNull(backend.peek("bound"))
        }

    @Test
    fun `failed snapshot read propagates rather than returning empty ownership`() =
        runTest {
            val backend =
                object : DataStore<Preferences> {
                    override val data: Flow<Preferences> = flow { error("Synthetic read failure") }

                    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
                        error("Unexpected write")
                }
            assertFailsWith<IllegalStateException> {
                DataStoreKeyValueStorage(backend).getStringsOrThrow(setOf("owner", "bound"))
            }
        }

    @Test
    fun `stale owner snapshot cannot replace a concurrently bound account`() =
        runTest {
            val backend = TransactionStore()
            val storage = DataStoreKeyValueStorage(backend)
            storage.updateStringsOrThrow(mapOf("owner" to "account-a", "bound" to "true"))
            assertEquals(
                false,
                storage.compareAndSetStringsOrThrow(
                    mapOf("owner" to "local", "bound" to null),
                    mapOf("owner" to "account-b", "bound" to "true"),
                ),
            )
            assertEquals("account-a", backend.peek("owner"))
            assertEquals("true", backend.peek("bound"))
        }

    @Test
    fun `prefixed conditional update matches and changes only the selected namespace`() =
        runTest {
            val backend = TransactionStore()
            val storage = PrefixedKeyValueStorage(DataStoreKeyValueStorage(backend), "journal.")
            assertEquals(
                true,
                storage.compareAndSetStringsOrThrow(
                    mapOf("owner" to null, "bound" to null),
                    mapOf("owner" to "remote", "bound" to "true"),
                ),
            )
            assertEquals("remote", backend.peek("journal.owner"))
            assertEquals("true", backend.peek("journal.bound"))
            assertEquals("local", backend.peek("owner"))
            assertNull(backend.peek("bound"))
        }

    private class TransactionStore(
        private val rejectBound: Boolean = false,
    ) : DataStore<Preferences> {
        private var state: Preferences =
            mutablePreferencesOf(
                stringPreferencesKey("owner") to "local",
                stringPreferencesKey("unrelated") to "keep",
            )
        var commits = 0
            private set
        var reads = 0
            private set
        override val data: Flow<Preferences> =
            flow {
                reads++
                emit(state)
            }

        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            val candidate = transform(state)
            if (rejectBound && candidate[stringPreferencesKey("bound")] == "true") error("Synthetic binding commit failure")
            state = candidate
            commits++
            return state
        }

        fun peek(key: String): String? = state[stringPreferencesKey(key)]
    }
}
