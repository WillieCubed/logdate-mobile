package app.logdate.client.data.journals

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.logdate.client.data.account.HistoryOwnerAdoption
import app.logdate.client.datastore.DataStoreKeyValueStorage
import app.logdate.client.datastore.KeyValueStorage
import app.logdate.client.device.identity.DefaultCanonicalOwnerProvider
import app.logdate.shared.config.DefaultLogDateConfigRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class DraftDeletionOwnerAdoptionTest {
    @Test
    fun `consented adoption moves scoped and anonymous deletion IDs without claiming draft bodies`() =
        runTest {
            fixture { f ->
                val anonymous = f.id()
                val scoped = f.id()
                val foreign = f.id()
                f.put("editor_drafts_anonymous", setOf(anonymous))
                val body =
                    app.logdate.shared.model.EditorDraft(
                        blocks =
                            listOf(
                                app.logdate.shared.model.SerializableTextBlock(
                                    Uuid.random(),
                                    kotlin.time.Instant.fromEpochMilliseconds(1),
                                    content = "Origin remains unclaimed",
                                ),
                            ),
                    )
                f.storage.putStringOrThrow(
                    "editor_drafts_anonymous",
                    f.state("editor_drafts_anonymous").copy(drafts = listOf(body)).encode(Json),
                )
                f.put(f.key(f.old, ORIGIN), setOf(scoped))
                f.put(f.key(f.old, "https://foreign.example"), setOf(foreign))
                f.put("editor_drafts", setOf(foreign))
                assertTrue(f.adoption().adopt(ACCOUNT))
                assertEquals(setOf(anonymous, scoped), f.state(f.key(ACCOUNT, ORIGIN)).localDeletionIds)
                assertTrue(f.state("editor_drafts_anonymous").localDeletionIds.isEmpty())
                assertTrue(f.state(f.key(f.old, ORIGIN)).localDeletionIds.isEmpty())
                assertEquals(setOf(foreign), f.state(f.key(f.old, "https://foreign.example")).localDeletionIds)
                assertEquals(setOf(foreign), f.state("editor_drafts").localDeletionIds)
                assertEquals(listOf(body), f.state("editor_drafts_anonymous").drafts)
                assertTrue(f.state(f.key(ACCOUNT, ORIGIN)).drafts.isEmpty())
            }
        }

    @Test
    fun `binding crash retains source shield and retry never readds an explicitly cleared target deletion`() =
        runTest {
            fixture { f ->
                val id = f.id()
                f.put("editor_drafts_anonymous", setOf(id))
                var fail = true
                val owner =
                    object : app.logdate.client.device.identity.CanonicalOwnerProvider by f.owner {
                        override suspend fun adoptRemoteOwnerIfUninitialized(remoteOwnerId: String): Boolean {
                            if (fail) error("binding interrupted")
                            return f.owner.adoptRemoteOwnerIfUninitialized(remoteOwnerId)
                        }
                    }
                assertFailsWith<IllegalStateException> { f.adoption(owner = owner).adopt(ACCOUNT) }
                assertEquals(setOf(id), f.state("editor_drafts_anonymous").localDeletionIds)
                assertEquals(setOf(id), f.state(f.key(ACCOUNT, ORIGIN)).localDeletionIds)
                fail = false
                assertTrue(f.adoption(owner = owner).adopt(ACCOUNT))
                f.put(f.key(ACCOUNT, ORIGIN), emptySet())
                f.adoption().reconcile(ACCOUNT, ORIGIN)
                assertTrue(f.state(f.key(ACCOUNT, ORIGIN)).localDeletionIds.isEmpty())
            }
        }

    @Test
    fun `atomic draft write failure leaves owner and deletion scopes unchanged then retries`() =
        runTest {
            fixture { f ->
                val id = f.id()
                f.put("editor_drafts_anonymous", setOf(id))
                var fail = true
                val storage =
                    object : KeyValueStorage by f.storage {
                        override suspend fun compareAndSetStringsOrThrow(
                            expected: Map<String, String?>,
                            values: Map<String, String?>,
                        ): Boolean {
                            if (fail) error("disk commit interrupted")
                            return f.storage.compareAndSetStringsOrThrow(expected, values)
                        }
                    }
                assertFailsWith<IllegalStateException> { f.adoption(storage).adopt(ACCOUNT) }
                assertEquals(f.old, f.owner.getCanonicalOwnerId())
                assertFalse(f.owner.hasBoundOwner())
                assertEquals(setOf(id), f.state("editor_drafts_anonymous").localDeletionIds)
                assertTrue(f.state(f.key(ACCOUNT, ORIGIN)).localDeletionIds.isEmpty())
                fail = false
                assertTrue(f.adoption(storage).adopt(ACCOUNT))
            }
        }

    @Test
    fun `pending adoption cannot move deletion intent to a different owner or origin`() =
        runTest {
            fixture { f ->
                val id = f.id()
                f.put("editor_drafts_anonymous", setOf(id))
                val owner =
                    object : app.logdate.client.device.identity.CanonicalOwnerProvider by f.owner {
                        override suspend fun adoptRemoteOwnerIfUninitialized(remoteOwnerId: String): Boolean = error("stop before binding")
                    }
                assertFailsWith<IllegalStateException> { f.adoption(owner = owner).adopt(ACCOUNT) }
                assertFalse(f.adoption().adopt(OTHER))
                assertFalse(f.adoption().adoptOnOrigin(ACCOUNT, "https://other.example"))
                assertTrue(f.state(f.key(OTHER, ORIGIN)).localDeletionIds.isEmpty())
                assertTrue(f.state(f.key(ACCOUNT, "https://other.example")).localDeletionIds.isEmpty())
            }
        }

    @Test
    fun `receipt failure after binding cannot reintroduce deletion cleared before retry`() =
        runTest {
            fixture { f ->
                val id = f.id()
                f.put("editor_drafts_anonymous", setOf(id))
                var fail = true
                val storage =
                    object : KeyValueStorage by f.storage {
                        override suspend fun putString(
                            key: String,
                            value: String,
                        ) {
                            if (fail && key == "identity.completed_history_owner_adoption.v1") error("receipt commit interrupted")
                            f.storage.putString(key, value)
                        }
                    }
                assertFailsWith<IllegalStateException> { f.adoption(storage).adopt(ACCOUNT) }
                assertEquals(ACCOUNT, f.owner.getCanonicalOwnerId())
                assertEquals(setOf(id), f.state("editor_drafts_anonymous").localDeletionIds)
                f.put(f.key(ACCOUNT, ORIGIN), emptySet())
                fail = false
                assertTrue(f.adoption(storage).adopt(ACCOUNT))
                assertTrue(f.state(f.key(ACCOUNT, ORIGIN)).localDeletionIds.isEmpty())
                assertTrue(f.state("editor_drafts_anonymous").localDeletionIds.isEmpty())
            }
        }

    @Test
    fun `cleanup failure keeps pending adoption recoverable without reversing a later save`() =
        runTest {
            fixture { f ->
                val id = f.id()
                f.put("editor_drafts_anonymous", setOf(id))
                var commits = 0
                val storage =
                    object : KeyValueStorage by f.storage {
                        override suspend fun compareAndSetStringsOrThrow(
                            expected: Map<String, String?>,
                            values: Map<String, String?>,
                        ): Boolean {
                            commits++
                            if (commits == 2) error("source cleanup interrupted")
                            return f.storage.compareAndSetStringsOrThrow(expected, values)
                        }
                    }
                assertFailsWith<IllegalStateException> { f.adoption(storage).adopt(ACCOUNT) }
                assertEquals(ACCOUNT, f.owner.getCanonicalOwnerId())
                f.put(f.key(ACCOUNT, ORIGIN), emptySet())
                assertTrue(f.adoption(storage).adopt(ACCOUNT))
                assertTrue(f.state("editor_drafts_anonymous").localDeletionIds.isEmpty())
                assertTrue(f.state(f.key(ACCOUNT, ORIGIN)).localDeletionIds.isEmpty())
            }
        }

    @Test
    fun `stale adoption proof never binds even without markers or when owners match`() =
        runTest {
            fixture { f ->
                f.storage.putStringOrThrow("proof", "current")
                var bound = false
                for (target in listOf(ACCOUNT, f.old)) {
                    assertFailsWith<IllegalStateException> {
                        DraftDeletionOwnerAdoption(f.storage).transfer(f.old, target, ORIGIN, "proof", "stale") { bound = true }
                    }
                    assertFalse(bound)
                }
            }
        }

    private suspend fun fixture(block: suspend (Fixture) -> Unit) {
        val path = Files.createTempDirectory("draft-deletion-adoption")
        val job = SupervisorJob()
        val scope = CoroutineScope(Dispatchers.IO + job)
        val backend = PreferenceDataStoreFactory.create(scope = scope, produceFile = { path.resolve("state.preferences_pb").toFile() })
        try {
            val storage = DataStoreKeyValueStorage(backend)
            val owner = DefaultCanonicalOwnerProvider(storage)
            block(Fixture(storage, owner, owner.getCanonicalOwnerId()))
        } finally {
            job.cancelAndJoin()
            path.toFile().deleteRecursively()
        }
    }

    private class Fixture(
        val storage: DataStoreKeyValueStorage,
        val owner: DefaultCanonicalOwnerProvider,
        val old: String,
    ) {
        fun id() = Uuid.random().toString()

        fun key(
            owner: String,
            origin: String,
        ) = "editor_drafts_scope_${owner.length}:$owner:${origin.length}:$origin"

        suspend fun state(key: String) = ArchiveDraftStorageState.decode(Json, storage.getStringOrThrow(key))

        suspend fun put(
            key: String,
            ids: Set<String>,
        ) = storage.putStringOrThrow(key, state(key).copy(localDeletionIds = ids).encode(Json))

        fun adoption(
            storage: KeyValueStorage = this.storage,
            owner: app.logdate.client.device.identity.CanonicalOwnerProvider = this.owner,
        ) = HistoryOwnerAdoption(
            storage,
            owner,
            DefaultLogDateConfigRepository(initialBackendUrl = ORIGIN),
            { "device" },
            { _, _, _, _ -> },
        )
    }

    private companion object {
        const val ACCOUNT = "11000000-0000-4000-8000-000000000001"
        const val OTHER = "11000000-0000-4000-8000-000000000002"
        const val ORIGIN = "https://adopt.example"
    }
}
