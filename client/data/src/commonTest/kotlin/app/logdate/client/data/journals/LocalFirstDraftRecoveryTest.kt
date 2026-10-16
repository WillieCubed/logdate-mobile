package app.logdate.client.data.journals

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import app.logdate.client.datastore.DataStoreKeyValueStorage
import app.logdate.client.datastore.KeyValueStorage
import app.logdate.shared.model.EditorDraft
import app.logdate.shared.model.SerializableTextBlock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class LocalFirstDraftRecoveryTest {
    @Test
    fun `a skipped pending draft deletion remains skipped after settling`() =
        runTest {
            val drafts = LocalFirstDraftRepository(DataStoreKeyValueStorage(RecoveryPreferences()), Json)
            val metadata =
                app.logdate.client.data.fakes
                    .FakeSyncMetadataService()
            val draft = EditorDraft()
            metadata.enqueuePending(
                draft.id.toString(),
                app.logdate.client.sync.metadata.EntityType.DRAFT,
                app.logdate.client.sync.metadata.PendingOperation.DELETE,
            )
            val persistence = JournalDraftPersistence(drafts, metadata, { it }, {})
            val recovery = Uuid.random().toString()
            assertFalse(persistence.compareAndSet(draft.id, null, draft, drafts.getDraftRecoveryScope(), recoveryId = recovery))
            metadata.clearPending()
            assertFalse(persistence.compareAndSet(draft.id, null, draft, drafts.getDraftRecoveryScope(), recoveryId = recovery))
            assertEquals(null, drafts.getDraftForRestore(draft.id))
        }

    @Test
    fun `a concurrent draft decision survives subsequent deletion`() =
        runTest {
            val storage = DataStoreKeyValueStorage(RecoveryPreferences())
            val repository = LocalFirstDraftRepository(storage, Json)
            val draft = EditorDraft()
            repository.saveDraftFromSync(draft)
            val recovery = Uuid.random().toString()
            assertFalse(repository.compareAndSetDraftForRestore(draft.id, null, draft, repository.getDraftRecoveryScope(), recovery))
            repository.deleteDraft(draft.id)
            assertFalse(repository.compareAndSetDraftForRestore(draft.id, null, draft, repository.getDraftRecoveryScope(), recovery))
            assertEquals(null, repository.getDraftForRestore(draft.id))
        }

    @Test
    fun `an applied archive receipt survives deletion and a new repository instance`() =
        runTest {
            val preferences = RecoveryPreferences()
            val storage = DataStoreKeyValueStorage(preferences)
            val repository = LocalFirstDraftRepository(storage, Json)
            val draft = EditorDraft()
            val recoveryId = Uuid.random().toString()
            assertTrue(repository.compareAndSetDraftForRestore(draft.id, null, draft, repository.getDraftRecoveryScope(), recoveryId))
            repository.deleteDraft(draft.id)
            val restarted = LocalFirstDraftRepository(storage, Json)
            assertFalse(restarted.compareAndSetDraftForRestore(draft.id, null, draft, restarted.getDraftRecoveryScope(), recoveryId))
            assertEquals(null, restarted.getDraftForRestore(draft.id))
        }

    @Test
    fun `a no-progress media retry validates the snapshot without writing or reordering drafts`() =
        runTest {
            val preferences = RecoveryPreferences()
            val repository = LocalFirstDraftRepository(DataStoreKeyValueStorage(preferences), Json)
            val first = EditorDraft()
            val second = EditorDraft()
            repository.saveDraftFromSync(first)
            repository.saveDraftFromSync(second)
            val before = preferences.writes
            assertTrue(repository.compareAndSetDraftForRestore(first.id, first, first, repository.getDraftRecoveryScope()))
            assertEquals(before, preferences.writes)
            assertEquals(listOf(first.id, second.id), repository.getAllDrafts().map { it.id })
        }

    @Test
    fun `scope switching between validation and capture cannot redirect a recovery write`() =
        runTest {
            val first = DraftStorageScope("owner-a", "https://one.example")
            val second = DraftStorageScope("owner-b", "https://two.example")
            var switching = false
            var reads = 0
            val repository =
                LocalFirstDraftRepository(
                    DataStoreKeyValueStorage(RecoveryPreferences()),
                    Json,
                    currentScope = { if (switching && ++reads >= 2) second else first },
                )
            val captured = repository.getDraftRecoveryScope()
            switching = true
            val incoming = EditorDraft()
            assertFailsWith<IllegalStateException> { repository.compareAndSetDraftForRestore(incoming.id, null, incoming, captured) }
            assertEquals(null, repository.getDraftForRestore(incoming.id))
        }

    @Test
    fun `recovery cannot write a draft into a different account or server scope`() =
        runTest {
            var scope = DraftStorageScope("owner-a", "https://one.example")
            val repository = LocalFirstDraftRepository(DataStoreKeyValueStorage(RecoveryPreferences()), Json, currentScope = { scope })
            val captured = repository.getDraftRecoveryScope()
            val incoming = EditorDraft()
            for (replacement in listOf(
                DraftStorageScope("owner-b", "https://one.example"),
                DraftStorageScope("owner-a", "https://two.example"),
            )) {
                scope = replacement
                assertFailsWith<IllegalStateException> { repository.compareAndSetDraftForRestore(incoming.id, null, incoming, captured) }
                assertEquals(null, repository.getDraftForRestore(incoming.id))
            }
        }

    @Test
    fun `rollback cannot replace an identical snapshot held by another account`() =
        runTest {
            val first = DraftStorageScope("owner-a", "https://one.example")
            var scope = first
            val repository = LocalFirstDraftRepository(DataStoreKeyValueStorage(RecoveryPreferences()), Json, currentScope = { scope })
            val imported = EditorDraft()
            repository.saveDraftFromSync(imported)
            val captured = repository.getDraftRecoveryScope()
            scope = DraftStorageScope("owner-b", "https://one.example")
            repository.saveDraftFromSync(imported)
            assertFailsWith<IllegalStateException> { repository.compareAndSetDraftForRestore(imported.id, imported, null, captured) }
            assertEquals(imported, repository.getDraftForRestore(imported.id))
            scope = first
            assertEquals(imported, repository.getDraftForRestore(imported.id))
        }

    @Test
    fun `an editor save queued during recovery remains the final draft`() =
        runTest {
            val base = DataStoreKeyValueStorage(RecoveryPreferences())
            val captured = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            var pause = false
            val storage =
                object : KeyValueStorage by base {
                    override suspend fun getStringOrThrow(key: String): String? {
                        val value = base.getStringOrThrow(key)
                        if (pause && key == "editor_drafts") {
                            pause = false
                            captured.complete(Unit)
                            release.await()
                        }
                        return value
                    }
                }
            val repository = LocalFirstDraftRepository(storage, Json)
            val original = EditorDraft(lastModifiedAt = Instant.parse("2026-01-01T00:00:00Z"))
            repository.saveDraftFromSync(original)
            val imported = original.copy(lastModifiedAt = Instant.parse("2026-01-02T00:00:00Z"))
            val edit =
                imported.copy(
                    blocks = listOf(SerializableTextBlock(Uuid.random(), imported.lastModifiedAt, content = "Accepted editor save")),
                )
            pause = true
            val recovery = async { repository.compareAndSetDraftForRestore(original.id, original, imported) }
            captured.await()
            val editor = async { repository.saveDraft(edit) }
            runCurrent()
            release.complete(Unit)
            assertTrue(recovery.await())
            editor.await()
            assertEquals(edit.blocks, repository.getDraftForRestore(original.id)?.blocks)
        }

    @Test
    fun `recovery read propagates preferences failure and retains the stored draft`() =
        runTest {
            val preferences = RecoveryPreferences()
            val storage = DataStoreKeyValueStorage(preferences)
            val repository = LocalFirstDraftRepository(storage, Json)
            val original = EditorDraft()
            repository.saveDraftFromSync(original)
            preferences.failRead = true
            assertFailsWith<IllegalStateException> { repository.getDraftForRestore(original.id) }
            preferences.failRead = false
            assertEquals(original, repository.getDraftForRestore(original.id))
        }

    @Test
    fun `failed recovery persistence is reported without changing the previous draft`() =
        runTest {
            val preferences = RecoveryPreferences()
            val storage = DataStoreKeyValueStorage(preferences)
            val repository = LocalFirstDraftRepository(storage, Json)
            val original = EditorDraft()
            repository.saveDraftFromSync(original)
            preferences.failWrite = true
            assertFailsWith<IllegalStateException> {
                repository.compareAndSetDraftForRestore(original.id, original, original.copy(lastModifiedAt = Instant.DISTANT_PAST))
            }
            preferences.failWrite = false
            assertEquals(original, repository.getDraftForRestore(original.id))
        }

    @Test
    fun `recovery preserves exact timestamps and cannot overwrite a later editor save`() =
        runTest {
            val preferences = RecoveryPreferences()
            val storage = DataStoreKeyValueStorage(preferences)
            val repository = LocalFirstDraftRepository(storage, Json)
            val original = EditorDraft(lastModifiedAt = Instant.parse("2026-01-01T00:00:00Z"))
            repository.saveDraftFromSync(original)
            val imported = original.copy(lastModifiedAt = Instant.parse("2026-01-02T00:00:00Z"))
            assertTrue(repository.compareAndSetDraftForRestore(original.id, original, imported))
            assertEquals(imported, LocalFirstDraftRepository(storage, Json).getDraftForRestore(original.id))
            val edit = imported.copy(blocks = listOf(SerializableTextBlock(Uuid.random(), imported.lastModifiedAt, content = "Later edit")))
            repository.saveDraft(edit)
            val accepted = repository.getDraftForRestore(original.id)
            assertFalse(repository.compareAndSetDraftForRestore(original.id, imported, original))
            assertFalse(repository.compareAndSetDraftForRestore(original.id, imported, null))
            assertEquals(accepted, repository.getDraftForRestore(original.id))
        }
}

private class RecoveryPreferences : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    var writes = 0
    var failRead = false
    var failWrite = false
    override val data: Flow<Preferences> =
        flow {
            if (failRead) error("Unavailable draft preferences")
            emit(state.value)
        }

    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
        if (failWrite) error("Draft persistence failed")
        writes++
        return transform(state.value).also { state.value = it }
    }
}
