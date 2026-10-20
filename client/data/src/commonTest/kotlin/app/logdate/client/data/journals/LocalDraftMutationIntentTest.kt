package app.logdate.client.data.journals

import app.logdate.client.repository.journals.DraftRecoveryScope
import app.logdate.client.sync.SyncTransactionManager
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.withTransactionCompletion
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocalDraftMutationIntentTest {
    @Test
    fun `normal accepted save retires only its exact local source after scoped enqueue commits`() =
        runTest {
            val f = CustodyFixture()
            f.signIn()
            val original = custodyDraft("Accepted before sign in")
            f.seedAnonymous(original)
            val replacement = original.copy(blocks = listOf(custodyBlock("Edited normally")))
            assertTrue(f.repository().persistDraftMutation(original.id, replacement, mutationScope()))
            assertEquals(listOf(replacement), f.repository().getAllDraftsForSync())
            assertFalse(f.repository().hasUnclaimedDraft(original.id))
            assertEquals(
                PendingOperation.UPDATE,
                f.metadata.pending
                    .single()
                    .operation,
            )
            assertEquals(
                f.scope,
                f.metadata.pending
                    .single()
                    .scope,
            )
        }

    @Test
    fun `postwrite server switch retains captured intent and original source until that server resumes`() =
        runTest {
            val f = CustodyFixture()
            f.signIn()
            val original = custodyDraft("Unclaimed source before scope switch")
            f.seedAnonymous(original)
            val replacement = original.copy(blocks = listOf(custodyBlock("Committed under server A")))
            f.storage.afterWrite = { key -> if (key.startsWith("editor_drafts_scope_")) f.config.updateBackendUrl(SERVER_B) }
            assertTrue(f.repository().persistDraftMutation(original.id, replacement, mutationScope()))
            f.storage.afterWrite = {}
            assertEquals(
                f.scope,
                f.metadata.pending
                    .single()
                    .scope,
            )
            assertTrue(f.repository().hasUnclaimedDraft(original.id))
            assertTrue(f.repository().getAllDraftsForSync().isEmpty())
            f.config.updateBackendUrl(SERVER_A)
            assertEquals(listOf(replacement), f.repository().getAllDraftsForSync())
            assertFalse(f.repository().hasUnclaimedDraft(original.id))
            assertFalse(
                f.storage
                    .getStringOrThrow(scopeKey())
                    .orEmpty()
                    .contains("pendingMutations"),
            )
        }

    @Test
    fun `failed enqueue recreates from durable save intent without losing either storage boundary`() =
        runTest {
            val f = CustodyFixture()
            f.signIn()
            val original = custodyDraft("Source retained on SQL failure")
            f.seedAnonymous(original)
            val replacement = original.copy(blocks = listOf(custodyBlock("Durable replacement")))
            f.metadata.failNextEnqueue = true
            assertFailsWith<IllegalStateException> { f.repository().persistDraftMutation(original.id, replacement, mutationScope()) }
            assertTrue(
                f.storage
                    .getStringOrThrow(scopeKey())
                    .orEmpty()
                    .contains("pendingMutations"),
            )
            assertTrue(f.repository().hasUnclaimedDraft(original.id))
            assertEquals(listOf(replacement), f.repository().getAllDraftsForSync())
            assertFalse(f.repository().hasUnclaimedDraft(original.id))
            assertEquals(
                f.scope,
                f.metadata.pending
                    .single()
                    .scope,
            )
        }

    @Test
    fun `failed delete enqueue retains tombstone then retries without resurrecting local source`() =
        runTest {
            val f = CustodyFixture()
            f.signIn()
            val secret = "Synthetic draft explicitly deleted with interrupted enqueue"
            val draft = custodyDraft(secret)
            f.seedAnonymous(draft)
            f.metadata.failNextEnqueue = true
            assertFailsWith<IllegalStateException> { f.repository().persistDraftMutation(draft.id, null, mutationScope()) }
            assertTrue(f.repository().hasUnclaimedDraft(draft.id))
            assertTrue(f.repository().getAllDrafts().isEmpty())
            assertFalse(f.repository().hasUnclaimedDraft(draft.id))
            assertEquals(
                PendingOperation.DELETE,
                f.metadata.pending
                    .single()
                    .operation,
            )
            assertEquals(
                f.scope,
                f.metadata.pending
                    .single()
                    .scope,
            )
            assertFalse(
                f.storage
                    .getStringOrThrow("editor_drafts_anonymous")
                    .orEmpty()
                    .contains(secret),
            )
            assertFalse(
                f.storage
                    .getStringOrThrow(scopeKey())
                    .orEmpty()
                    .contains(secret),
            )
        }

    @Test
    fun `outer SQL rollback preserves data-store intent for recreated scoped replay`() =
        runTest {
            val f = CustodyFixture()
            f.signIn()
            val draft = custodyDraft("Accepted despite outer transaction rollback")
            val transactions = RollbackCustodyTransactions(f.metadata)
            val repository = f.repository(transactions = transactions)
            assertFailsWith<IllegalStateException> {
                transactions.withTransactionCompletion {
                    assertTrue(repository.persistDraftMutation(draft.id, draft, mutationScope()))
                    error("Synthetic outer SQL rollback")
                }
            }
            assertTrue(f.metadata.pending.isEmpty())
            assertTrue(
                f.storage
                    .getStringOrThrow(scopeKey())
                    .orEmpty()
                    .contains("pendingMutations"),
            )
            assertEquals(listOf(draft), f.repository(transactions = transactions).getAllDraftsForSync())
            assertEquals(
                f.scope,
                f.metadata.pending
                    .single()
                    .scope,
            )
            assertFalse(
                f.storage
                    .getStringOrThrow(scopeKey())
                    .orEmpty()
                    .contains("pendingMutations"),
            )
        }

    private fun mutationScope() = DraftRecoveryScope("owner-a", SERVER_A, "owner-a", SERVER_A)

    private fun scopeKey() = "editor_drafts_scope_7:owner-a:${SERVER_A.length}:$SERVER_A"

    private companion object {
        const val SERVER_A = "https://server-a.example"
        const val SERVER_B = "https://server-b.example"
    }
}

private class RollbackCustodyTransactions(
    private val metadata: CustodyMetadata,
) : SyncTransactionManager {
    override suspend fun <T> withTransaction(block: suspend () -> T): T {
        val snapshot = metadata.pending.toList()
        return try {
            block()
        } catch (failure: Throwable) {
            metadata.pending.clear()
            metadata.pending.addAll(snapshot)
            throw failure
        }
    }
}
