package app.logdate.client.data.notes.drafts

import app.logdate.client.data.fakes.FakeJournalRepository
import app.logdate.client.data.fakes.FakeLocalEntryDraftStore
import app.logdate.client.repository.journals.EntryDraft
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ScopedEntryDraftRecoveryTest {
    @Test
    fun `competing save owner adoption and scoped recovery complete without reversed locks`() =
        runTest {
            val ownerMutex = Mutex()
            val preferencesMutex = Mutex()
            val ownerHeld = CompletableDeferred<Unit>()
            val releaseOwner = CompletableDeferred<Unit>()
            val saveResolving = CompletableDeferred<Unit>()
            val store = RecoveryStore()
            val journalId = Uuid.random()
            val expected = draft().copy(selectedJournalIds = listOf(journalId))
            store.saveDraft(expected)
            val journals =
                object : JournalRepository by FakeJournalRepository() {
                    override suspend fun resolveJournalId(journalId: Uuid): Uuid {
                        saveResolving.complete(Unit)
                        return ownerMutex.withLock { journalId }
                    }
                }
            val repository = OfflineFirstEntryDraftRepository(store, backgroundScope, journals)
            repository.getDraftSnapshotsForRecovery()
            val adoption =
                async {
                    ownerMutex.withLock {
                        ownerHeld.complete(Unit)
                        releaseOwner.await()
                        preferencesMutex.withLock { Unit }
                    }
                }
            ownerHeld.await()
            val saved =
                expected.copy(
                    notes =
                        listOf(
                            JournalNote.Text(
                                content = "new editor content",
                                creationTimestamp = expected.createdAt,
                                lastUpdated = expected.updatedAt,
                            ),
                        ),
                )
            val save = async { repository.saveDraftSnapshot(saved) }
            saveResolving.await()
            var fenceEntered = false
            val repair =
                async {
                    repository.compareAndSetDraftForScopedRecovery(expected, expected.copy(notes = emptyList()), { true }) { commit ->
                        preferencesMutex.withLock {
                            fenceEntered = true
                            commit()
                        }
                    }
                }
            runCurrent()
            assertFalse(fenceEntered, "Recovery must await the draft mutex before taking main preferences")
            releaseOwner.complete(Unit)
            withTimeout(1_000) {
                adoption.await()
                save.await()
                assertFalse(repair.await(), "A competing editor save wins the snapshot CAS")
            }
            assertEquals(saved.notes, repository.getDraftSnapshotsForRecovery().single().notes)
        }

    @Test
    fun `scope fence encloses durable CAS and refuses a revoked scope`() =
        runTest {
            val store = RecoveryStore()
            val expected = draft()
            store.saveDraft(expected)
            val repository = OfflineFirstEntryDraftRepository(store, backgroundScope)
            var insideFence = false
            store.beforeCommit = { assertTrue(insideFence) }
            val replacement = expected.copy(notes = emptyList())
            assertTrue(
                repository.compareAndSetDraftForScopedRecovery(expected, replacement, { true }) { commit ->
                    insideFence = true
                    try {
                        commit()
                    } finally {
                        insideFence = false
                    }
                },
            )
            assertEquals(replacement, store.getDraft(expected.id))
            assertFalse(repository.compareAndSetDraftForScopedRecovery(replacement, expected, { true }) { false })
            assertEquals(replacement, store.getDraft(expected.id))
        }

    private fun draft(): EntryDraft {
        val now = Instant.parse("2026-10-23T00:00:00Z")
        return EntryDraft(
            Uuid.random(),
            listOf(JournalNote.Text(content = "original", creationTimestamp = now, lastUpdated = now)),
            now,
            now,
        )
    }

    private class RecoveryStore :
        LocalEntryDraftStore by FakeLocalEntryDraftStore(),
        RecoveryEntryDraftStore {
        var beforeCommit: () -> Unit = {}

        override suspend fun compareAndSetForRecovery(
            expected: EntryDraft,
            replacement: EntryDraft,
            canCommit: () -> Boolean,
        ): Boolean {
            beforeCommit()
            if (!canCommit() || getDraft(expected.id) != expected) return false
            saveDraft(replacement)
            return true
        }
    }
}
