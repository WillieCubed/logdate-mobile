package app.logdate.client.data.journals

import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.metadata.PendingUpload
import app.logdate.client.sync.metadata.UploadScope
import app.logdate.client.sync.withTransactionCompletion
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class LocalDraftCustodySafetyTest {
    @Test
    fun `cold start awaits persisted origin before using the bound journal owner`() =
        runTest {
            val f = CustodyFixture()
            val ready = CompletableDeferred<Unit>()
            val draft = custodyDraft("Pending on persisted server")
            f.seedAnonymous(draft)
            f.metadata.pending += PendingUpload(draft.id.toString(), PendingOperation.UPDATE, scope = UploadScope("owner-a", SERVER_B))
            val read = async { f.repository(configurationReady = { ready.await() }).getAllDraftsForSync() }
            runCurrent()
            assertFalse(read.isCompleted)
            f.config.updateBackendUrl(SERVER_B)
            ready.complete(Unit)
            assertEquals(listOf(draft), read.await())
            f.config.updateBackendUrl(SERVER_A)
            assertTrue(f.repository().getAllDraftsForSync().isEmpty())
        }

    @Test
    fun `damaged unscoped bytes are retained and strict reads never pretend empty`() =
        runTest {
            val f = CustodyFixture()
            val damaged = "[{\"id\":\"retained-truncated-synthetic\""
            f.storage.putStringOrThrow("editor_drafts_anonymous", damaged)
            f.signIn()
            assertFailsWith<IllegalArgumentException> { f.repository().getAllDraftsForSync() }
            assertEquals(damaged, f.storage.getStringOrThrow("editor_drafts_anonymous"))
        }

    @Test
    fun `failed explicit save propagates without removing unknown accepted source`() =
        runTest {
            val f = CustodyFixture()
            val draft = custodyDraft("A normal user save")
            f.seedAnonymous(draft)
            f.signIn()
            f.storage.failPrefix = "editor_drafts_scope_"
            assertFailsWith<IllegalStateException> { f.repository().saveDraft(draft) }
            assertTrue(f.repository().hasUnclaimedDraft(draft.id))
            assertEquals(listOf(draft), f.repository().getAllDrafts())
            assertTrue(f.repository().getAllDraftsForSync().isEmpty())
        }

    @Test
    fun `server switch after durable copy retains source until the original scope resumes`() =
        runTest {
            val f = CustodyFixture()
            val draft = custodyDraft("Scope changes during copy")
            f.seedAnonymous(draft)
            f.metadata.pending += PendingUpload(draft.id.toString(), PendingOperation.UPDATE, scope = f.scope)
            f.signIn()
            f.storage.afterWrite = { key -> if (key.startsWith("editor_drafts_scope_")) f.config.updateBackendUrl(SERVER_B) }
            assertFailsWith<IllegalStateException> { f.repository().getAllDraftsForSync() }
            f.storage.afterWrite = {}
            assertTrue(f.repository().hasUnclaimedDraft(draft.id))
            assertTrue(f.repository().getAllDraftsForSync().isEmpty())
            f.config.updateBackendUrl(SERVER_A)
            assertEquals(listOf(draft), f.repository().getAllDraftsForSync())
            assertFalse(f.repository().hasUnclaimedDraft(draft.id))
        }

    @Test
    fun `anonymous changes are observed while already authenticated`() =
        runTest {
            val f = CustodyFixture()
            f.signIn()
            val seen = mutableListOf<List<app.logdate.shared.model.EditorDraft>>()
            val observation = backgroundScope.launch { f.repository().allDrafts.collect { seen += it } }
            runCurrent()
            val draft = custodyDraft("Arrived after the first observation")
            f.seedAnonymous(draft)
            runCurrent()
            assertEquals(listOf(draft), seen.last())
            f.repository().deleteDraft(draft.id)
            runCurrent()
            assertTrue(seen.last().isEmpty())
            observation.cancel()
        }

    @Test
    fun `legacy first claim marker is not historical server provenance`() =
        runTest {
            val f = CustodyFixture()
            val draft = custodyDraft("Legacy retained without known origin")
            f.storage.putStringOrThrow("editor_drafts", Json.encodeToString(listOf(draft)))
            f.storage.putStringOrThrow("editor_drafts_legacy_owner", "7:owner-a:24:$SERVER_A")
            f.signIn()
            assertTrue(f.repository().getAllDraftsForSync().isEmpty())
            assertEquals(listOf(draft), f.repository().getAllDrafts())
            assertTrue(f.repository().hasUnclaimedDraft(draft.id))
        }

    @Test
    fun `unknown conflicting snapshots have stable local aliases and only explicit copy becomes scoped`() =
        runTest {
            val f = CustodyFixture()
            val scoped = custodyDraft("Scoped accepted version")
            f.signIn()
            f.repository().saveDraftFromSync(scoped)
            val unclaimed = scoped.copy(blocks = listOf(custodyBlock("Unclaimed accepted version")))
            f.seedAnonymous(unclaimed)
            val visible = f.repository().getAllDrafts()
            assertEquals(2, visible.size)
            val local = visible.single { it.id != scoped.id }
            assertEquals(unclaimed.blocks, local.blocks)
            assertEquals(visible, f.repository().getAllDrafts())
            assertEquals(listOf(scoped), f.repository().getAllDraftsForSync())
            f.repository().saveDraft(local)
            assertEquals(visible.toSet(), f.repository().getAllDraftsForSync().toSet())
            assertFalse(f.repository().hasUnclaimedDraft(local.id))
        }

    @Test
    fun `deleting local fallback and clearing selected scope preserve another server drafts`() =
        runTest {
            val f = CustodyFixture()
            f.signIn()
            f.config.updateBackendUrl(SERVER_B)
            val other = custodyDraft("Another server")
            f.repository().saveDraftFromSync(other)
            f.config.updateBackendUrl(SERVER_A)
            val draft = custodyDraft("Unclaimed locally visible")
            f.seedAnonymous(draft)
            f.repository().deleteDraft(draft.id)
            assertTrue(f.repository().getAllDrafts().isEmpty())
            f.seedAnonymous(draft)
            f.repository().clearAllDrafts()
            assertTrue(f.repository().getAllDrafts().isEmpty())
            f.config.updateBackendUrl(SERVER_B)
            assertEquals(listOf(other), f.repository().getAllDraftsForSync())
        }

    @Test
    fun `an absent receipt destination never removes the sole remaining source snapshot`() =
        runTest {
            val f = CustodyFixture()
            f.signIn()
            val original = custodyDraft("Existing scoped version")
            f.repository().saveDraftFromSync(original)
            val source = original.copy(blocks = listOf(custodyBlock("Only surviving copy after interrupted cleanup")))
            f.seedAnonymous(source)
            f.metadata.pending += PendingUpload(source.id.toString(), PendingOperation.UPDATE, scope = f.scope)
            f.storage.failPrefix = "editor_drafts_anonymous"
            assertEquals(2, f.repository().getAllDraftsForSync().size)
            val key = "editor_drafts_scope_7:owner-a:${SERVER_A.length}:$SERVER_A"
            val copied = ArchiveDraftStorageState.decode(Json, f.storage.getStringOrThrow(key))
            f.storage.putStringOrThrow(key, copied.copy(drafts = listOf(original)).encode(Json))
            val retained = f.storage.getStringOrThrow("editor_drafts_anonymous")
            assertFailsWith<IllegalStateException> { f.repository().getAllDraftsForSync() }
            assertEquals(retained, f.storage.getStringOrThrow("editor_drafts_anonymous"))
        }

    @Test
    fun `saving one visible version retains the other same-ID anonymous and legacy versions`() =
        runTest {
            val f = CustodyFixture()
            f.signIn()
            val scoped = custodyDraft("Scoped version")
            f.repository().saveDraftFromSync(scoped)
            val anonymous = scoped.copy(blocks = listOf(custodyBlock("Anonymous version")))
            val legacy = scoped.copy(blocks = listOf(custodyBlock("Legacy version")))
            f.seedAnonymous(anonymous)
            f.storage.putStringOrThrow("editor_drafts", Json.encodeToString(listOf(legacy)))
            val visible = f.repository().getAllDrafts()
            assertEquals(3, visible.size)
            val chosen = visible.single { it.blocks == anonymous.blocks }
            f.repository().saveDraft(chosen)
            val afterSave = f.repository().getAllDrafts()
            assertEquals(visible.toSet(), afterSave.toSet())
            assertEquals(setOf(scoped, chosen), f.repository().getAllDraftsForSync().toSet())
            val remaining = afterSave.single { it.blocks == legacy.blocks }
            assertTrue(f.repository().hasUnclaimedDraft(remaining.id))
            f.repository().deleteDraft(chosen.id)
            assertEquals(setOf(scoped, remaining), f.repository().getAllDrafts().toSet())
        }

    @Test
    fun `cancellation after copy retains source and resumes from durable receipt`() =
        runTest {
            val f = CustodyFixture()
            f.signIn()
            val draft = custodyDraft("Canceled persistence boundary")
            f.seedAnonymous(draft)
            f.metadata.pending += PendingUpload(draft.id.toString(), PendingOperation.UPDATE, scope = f.scope)
            f.storage.afterWrite = { key ->
                if (key.startsWith("editor_drafts_scope_")) throw kotlinx.coroutines.CancellationException("Synthetic cancellation")
            }
            assertFailsWith<kotlinx.coroutines.CancellationException> { f.repository().getAllDraftsForSync() }
            f.storage.afterWrite = {}
            assertTrue(f.repository().hasUnclaimedDraft(draft.id))
            assertEquals(listOf(draft), f.repository().getAllDraftsForSync())
            assertFalse(f.repository().hasUnclaimedDraft(draft.id))
        }

    @Test
    fun `destination loss after SQL commit cannot consume the sole remaining source`() =
        runTest {
            val f = CustodyFixture()
            f.signIn()
            val draft = custodyDraft("Only remaining accepted source")
            f.seedAnonymous(draft)
            f.metadata.pending += PendingUpload(draft.id.toString(), PendingOperation.UPDATE, scope = f.scope)
            val transactions = DirectArchiveRecoveryTransactions
            val key = "editor_drafts_scope_7:owner-a:${SERVER_A.length}:$SERVER_A"
            transactions.withTransactionCompletion {
                transactions.afterCommit {
                    LocalFirstDraftRepository.storageMutex.withLock {
                        val state = ArchiveDraftStorageState.decode(Json, f.storage.getStringOrThrow(key))
                        f.storage.putStringOrThrow(key, state.copy(drafts = emptyList()).encode(Json))
                    }
                }
                assertEquals(listOf(draft), f.repository(transactions = transactions).getAllDraftsForSync())
            }
            assertTrue(
                f.storage
                    .getStringOrThrow("editor_drafts_anonymous")
                    .orEmpty()
                    .contains(draft.id.toString()),
            )
        }

    private companion object {
        const val SERVER_A = "https://server-a.example"
        const val SERVER_B = "https://server-b.example"
    }
}
