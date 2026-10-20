package app.logdate.client.data.journals

import app.logdate.client.data.fakes.FakeSyncMetadataService
import app.logdate.client.repository.journals.DraftRecoveryScope
import app.logdate.client.sync.SyncTransactionManager
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.metadata.PendingUpload
import app.logdate.client.sync.metadata.UploadScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class DraftMutationStoreTest {
    @Test
    fun `committed deletion intent survives enqueue failure and process restart`() =
        runTest {
            val f = Fixture()
            f.record(PendingOperation.DELETE)
            f.metadata.fail = true
            assertFailsWith<IllegalStateException> { f.flush() }
            assertEquals(1, f.read().pendingMutations.size)
            assertTrue(f.read().drafts.isEmpty())
            f.flush()
            assertEquals(PendingOperation.DELETE, f.metadata.pending?.operation)
            assertEquals(f.scope, f.metadata.pending?.scope)
            assertTrue(f.read().pendingMutations.isEmpty())
        }

    @Test
    fun `outer SQL rollback retains local delete intent until the next successful commit`() =
        runTest {
            val f = Fixture()
            f.record(PendingOperation.DELETE)
            assertFailsWith<IllegalStateException> {
                f.transactions.withTransaction {
                    f.helper().flush(KEY)
                    error("rollback outer apply")
                }
            }
            assertEquals(null, f.metadata.pending)
            assertEquals(1, f.read().pendingMutations.size)
            f.flush()
            assertEquals(PendingOperation.DELETE, f.metadata.pending?.operation)
            assertTrue(f.read().pendingMutations.isEmpty())
        }

    @Test
    fun `older replay cannot replace a newer pending local mutation`() =
        runTest {
            val f = Fixture()
            f.metadata.pending = f.pending(PendingOperation.UPDATE, "original")
            f.record(PendingOperation.DELETE)
            val newer = f.pending(PendingOperation.UPDATE, "newer-edit")
            f.metadata.pending = newer
            f.flush()
            assertEquals(newer, f.metadata.pending)
            assertEquals(0, f.metadata.enqueues)
            assertTrue(f.read().pendingMutations.isEmpty())
        }

    @Test
    fun `cleanup failure after SQL commit does not duplicate or replace the queued mutation`() =
        runTest {
            val f = Fixture()
            f.record(PendingOperation.DELETE)
            f.transactions.beforeCallbacks = { f.storage.failPrefix = KEY }
            assertFailsWith<IllegalStateException> { f.flush() }
            val queued = f.metadata.pending
            assertEquals(1, f.read().pendingMutations.size)
            f.transactions.beforeCallbacks = {}
            f.flush()
            assertEquals(queued, f.metadata.pending)
            assertEquals(1, f.metadata.enqueues)
            assertTrue(f.read().pendingMutations.isEmpty())
        }

    @Test
    fun `older commit callback cannot erase a newer durable mutation receipt`() =
        runTest {
            val f = Fixture()
            f.record(PendingOperation.DELETE)
            f.transactions.withTransaction {
                f.helper().flush(KEY)
                f.record(PendingOperation.UPDATE)
            }
            assertEquals(listOf(PendingOperation.UPDATE.name), f.read().pendingMutations.map { it.operation })
            f.flush()
            assertEquals(PendingOperation.UPDATE, f.metadata.pending?.operation)
            assertTrue(f.read().pendingMutations.isEmpty())
        }

    @Test
    fun `multiple accepted edits retain the oldest SQL predecessor across outer rollback`() =
        runTest {
            val f = Fixture()
            f.metadata.pending = f.pending(PendingOperation.UPDATE, "before-outer-transaction")
            assertFailsWith<IllegalStateException> {
                f.transactions.withTransaction {
                    f.record(PendingOperation.UPDATE)
                    f.helper().flush(KEY)
                    f.record(PendingOperation.DELETE)
                    f.helper().flush(KEY)
                    error("outer rollback after two durable local edits")
                }
            }
            f.flush()
            assertEquals(PendingOperation.DELETE, f.metadata.pending?.operation)
            assertTrue(f.read().pendingMutations.isEmpty())
        }

    @Test
    fun `receipt remains until fallback cleanup succeeds after SQL commit`() =
        runTest {
            val f = Fixture()
            f.record(PendingOperation.DELETE)
            f.transactions.withTransaction { f.helper().flush(KEY) { false } }
            assertEquals(1, f.read().pendingMutations.size)
            assertEquals(PendingOperation.DELETE, f.metadata.pending?.operation)
            f.flush()
            assertTrue(f.read().pendingMutations.isEmpty())
        }

    @Test
    fun `same anonymous draft retains independent accepted intents for different servers`() =
        runTest {
            val f = Fixture()
            f.record(PendingOperation.DELETE)
            val second = DraftRecoveryScope(f.scope.ownerId, "https://another.example")
            val state = f.helper().prepare(f.read(), f.id, PendingOperation.UPDATE, second)
            assertEquals(2, state.pendingMutations.size)
            assertEquals(
                setOf(f.scope, UploadScope(f.scope.ownerId, "https://another.example")),
                state.pendingMutations.map { it.scope }.toSet(),
            )
        }

    private class Fixture {
        val id = Uuid.random()
        val scope = UploadScope("retained-owner", "https://retained.example")
        val storage = CustodyStorage()
        val metadata = MutationMetadata()
        val transactions = MutationTransactions(metadata)

        fun helper() = DraftMutationStore(storage, Json, metadata, transactions)

        suspend fun read() = ArchiveDraftStorageState.decode(Json, storage.getStringOrThrow(KEY))

        suspend fun record(operation: PendingOperation) {
            val state = helper().prepare(read(), id, operation, DraftRecoveryScope(scope.ownerId, scope.serverOrigin))
            storage.putStringOrThrow(KEY, state.encode(Json))
        }

        suspend fun flush() = transactions.withTransaction { helper().flush(KEY) }

        fun pending(
            operation: PendingOperation,
            operationId: String,
        ) = PendingUpload(
            id.toString(),
            operation,
            scope = scope,
            operationId = operationId,
        )
    }

    private class MutationMetadata : FakeSyncMetadataService() {
        var pending: PendingUpload? = null
        var fail = false
        var enqueues = 0

        override suspend fun getPendingUploadForScope(
            entityType: EntityType,
            entityId: String,
            scope: UploadScope,
        ) = pending?.takeIf { it.entityId == entityId && it.scope == scope }

        override suspend fun enqueuePendingForScope(
            entityId: String,
            entityType: EntityType,
            operation: PendingOperation,
            scope: UploadScope,
        ) {
            if (fail) {
                fail = false
                error("outbox unavailable")
            }
            enqueues++
            pending = PendingUpload(entityId, operation, scope = scope, operationId = Uuid.random().toString())
        }
    }

    private class MutationTransactions(
        private val metadata: MutationMetadata,
    ) : SyncTransactionManager {
        private val callbacks = mutableListOf<suspend () -> Unit>()
        var beforeCallbacks: suspend () -> Unit = {}

        override suspend fun afterCommit(block: suspend () -> Unit) {
            callbacks += block
        }

        override suspend fun <T> withTransaction(block: suspend () -> T): T {
            val before = metadata.pending
            val result =
                try {
                    block()
                } catch (error: Throwable) {
                    metadata.pending = before
                    callbacks.clear()
                    throw error
                }
            val committed = callbacks.toList()
            callbacks.clear()
            beforeCallbacks()
            committed.forEach { it() }
            return result
        }
    }

    private companion object {
        const val KEY = "draft-mutation-test"
    }
}
