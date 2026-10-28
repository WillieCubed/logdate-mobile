package app.logdate.client.e2e

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.logdate.client.domain.editor.SaveEntryUseCase
import app.logdate.client.domain.notes.AddNoteUseCase
import app.logdate.client.domain.notes.EntryEditConflictException
import app.logdate.client.domain.notes.drafts.CleanupExpiredDraftsUseCase
import app.logdate.client.domain.notes.drafts.CreateEntryDraftUseCase
import app.logdate.client.domain.notes.drafts.DeleteAllDraftsUseCase
import app.logdate.client.domain.notes.drafts.DeleteEntryDraftUseCase
import app.logdate.client.domain.notes.drafts.FetchEntryDraftUseCase
import app.logdate.client.domain.notes.drafts.UpdateEntryDraftUseCase
import app.logdate.client.repository.journals.CaptureActivity
import app.logdate.client.repository.journals.CaptureDevice
import app.logdate.client.repository.journals.EntryDraft
import app.logdate.client.repository.journals.EntryDraftRepository
import app.logdate.client.repository.journals.EntryEditBase
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.feature.editor.ui.editor.DraftState
import app.logdate.feature.editor.ui.editor.EntryEditorViewModel
import app.logdate.feature.editor.ui.editor.TextBlockUiState
import app.logdate.feature.editor.ui.editor.delegate.DraftManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

@RunWith(AndroidJUnit4::class)
class NativeRestoredEntryEditingTest {
    @Test
    fun explicitlyOpenedTodaysEntryRemainsEditable() =
        runBlocking {
            requireEmulatorFixture()
            val koin = GlobalContext.get()
            val repository = koin.get<JournalNotesRepository>()
            val now = kotlin.time.Instant.fromEpochMilliseconds(Clock.System.now().toEpochMilliseconds())
            val original = JournalNote.Text(content = "Today's retained entry ".repeat(6_000), creationTimestamp = now, lastUpdated = now)
            repository.create(original)
            val owner = withContext(Dispatchers.Main) { RestoredEditorTestOwner() }
            val model = withContext(Dispatchers.Main) { owner.editor() }
            val collection = launch { model.editorState.collect { } }
            try {
                withContext(Dispatchers.Main) { model.loadExistingEntry(original.uid) }
                val loaded = withTimeout(15_000) { model.editorState.first { state -> state.blocks.any { it.id == original.uid } } }
                assertFalse(loaded.isReadOnly(original.uid))
                val block = loaded.blocks.single() as TextBlockUiState
                val expected = original.content + "Edited today"
                withContext(Dispatchers.Main) { model.updateBlock(block.copy(content = expected)) }
                val edited =
                    withTimeout(15_000) {
                        model.editorState.first {
                            (it.blocks.singleOrNull() as? TextBlockUiState)?.content ==
                                expected
                        }
                    }
                withContext(Dispatchers.Main) { model.saveEntry(edited) }
                withTimeout(15_000) { model.editorState.first { it.shouldExit } }
                assertEquals(expected, (repository.getNoteById(original.uid) as JournalNote.Text).content)
            } finally {
                collection.cancel()
                withContext(Dispatchers.Main) { owner.close() }
            }
        }

    @Test
    fun staleEditIsDurableAndCanBeSavedSeparately() =
        runBlocking {
            requireEmulatorFixture()
            val repository = GlobalContext.get().get<JournalNotesRepository>()
            val save = GlobalContext.get().get<SaveEntryUseCase>()
            val now = kotlin.time.Instant.fromEpochMilliseconds(Clock.System.now().toEpochMilliseconds())
            val original = JournalNote.Text(content = "Before the other edit", creationTimestamp = now, lastUpdated = now)
            val myEdit = "My retained edit ${original.uid}"
            repository.create(original)
            withEditor { model ->
                withContext(Dispatchers.Main) { model.loadExistingEntry(original.uid) }
                val loaded = withTimeout(15_000) { model.editorState.first { it.blocks.any { block -> block.id == original.uid } } }
                val block = loaded.blocks.single() as TextBlockUiState
                withContext(Dispatchers.Main) { model.updateBlock(block.copy(content = myEdit)) }
                save(
                    listOf(original.copy(content = "Newer edit on another window")),
                    emptyList(),
                    null,
                    editBase = EntryEditBase(original, repository.getNoteRecoveryScope()),
                )
                val edited =
                    withTimeout(15_000) {
                        model.editorState.first {
                            (it.blocks.singleOrNull() as? TextBlockUiState)?.content ==
                                myEdit
                        }
                    }
                withContext(Dispatchers.Main) { model.saveEntry(edited) }
                val conflict = withTimeout(15_000) { model.editorState.first { it.entryEditConflict && !it.isSaving } }
                val draftId = (conflict.draftState as DraftState.Active).id
                val retained =
                    GlobalContext
                        .get()
                        .get<EntryDraftRepository>()
                        .getDraft(draftId)
                        .first()
                        .getOrThrow()
                assertEquals(original, retained.editBase?.original)
                assertEquals(myEdit, (retained.notes.single() as JournalNote.Text).content)
                assertEquals("Newer edit on another window", (repository.getNoteById(original.uid) as JournalNote.Text).content)
                withContext(Dispatchers.Main) { model.saveConflictingVersionSeparately() }
                val finished = withTimeout(15_000) { model.editorState.first { it.shouldExit } }
                val copyId = assertNotNull(finished.entryEditBase?.copyNoteId)
                assertEquals(myEdit, (repository.getNoteById(copyId) as JournalNote.Text).content)
                assertEquals("Newer edit on another window", (repository.getNoteById(original.uid) as JournalNote.Text).content)
                assertEquals(1, repository.allNotesObserved.first().count { (it as? JournalNote.Text)?.content == myEdit })
            }
        }

    @Test
    fun deletedEntryCannotBeResurrectedByItsStaleEditor() =
        runBlocking {
            requireEmulatorFixture()
            val repository = GlobalContext.get().get<JournalNotesRepository>()
            val save = GlobalContext.get().get<SaveEntryUseCase>()
            val now = kotlin.time.Instant.fromEpochMilliseconds(Clock.System.now().toEpochMilliseconds())
            val original = JournalNote.Text(content = "Delete this original", creationTimestamp = now, lastUpdated = now)
            repository.create(original)
            val base = EntryEditBase(original, repository.getNoteRecoveryScope())
            repository.removeById(original.uid)
            assertFailsWith<EntryEditConflictException> {
                save(
                    listOf(original.copy(content = "Unfinished edit")),
                    emptyList(),
                    null,
                    editBase = base,
                )
            }
            assertNull(repository.getNoteById(original.uid))
        }

    @Test
    fun reopenedDraftPreservesItsOriginalEditVersion() =
        runBlocking {
            requireEmulatorFixture()
            val repository = GlobalContext.get().get<JournalNotesRepository>()
            val now = kotlin.time.Instant.fromEpochMilliseconds(Clock.System.now().toEpochMilliseconds())
            val original = JournalNote.Text(content = "Before draft restart", creationTimestamp = now, lastUpdated = now)
            repository.create(original)
            var draftId: kotlin.uuid.Uuid? = null
            withEditor { model ->
                withContext(Dispatchers.Main) { model.loadExistingEntry(original.uid) }
                val loaded = withTimeout(15_000) { model.editorState.first { it.blocks.any { block -> block.id == original.uid } } }
                withContext(Dispatchers.Main) {
                    model.updateBlock((loaded.blocks.single() as TextBlockUiState).copy(content = "Edited in a durable draft"))
                }
                val edited =
                    withTimeout(15_000) {
                        model.editorState.first {
                            (it.blocks.singleOrNull() as? TextBlockUiState)?.content ==
                                "Edited in a durable draft"
                        }
                    }
                assertTrue(withContext(Dispatchers.Main) { model.saveAsDraft(edited) })
                draftId = (model.editorState.value.draftState as DraftState.Active).id
            }
            withEditor { model ->
                withContext(Dispatchers.Main) { model.loadDraft(assertNotNull(draftId)) }
                val loaded = withTimeout(15_000) { model.editorState.first { it.draftState is DraftState.Active } }
                assertEquals(original, loaded.entryEditBase?.original)
                assertFalse(loaded.isReadOnly(original.uid))
                withContext(Dispatchers.Main) { model.saveEntry(loaded) }
                withTimeout(15_000) { model.editorState.first { it.shouldExit } }
                assertEquals("Edited in a durable draft", (repository.getNoteById(original.uid) as JournalNote.Text).content)
            }
        }

    @Test
    fun copyRetryMustConfirmTheStoredCopyStillExists() =
        runBlocking {
            requireEmulatorFixture()
            val koin = GlobalContext.get()
            val repository = koin.get<JournalNotesRepository>()
            val now = kotlin.time.Instant.fromEpochMilliseconds(Clock.System.now().toEpochMilliseconds())
            val original = JournalNote.Text(content = "Original beside copy", creationTimestamp = now, lastUpdated = now)
            val copy = original.copy(uid = kotlin.uuid.Uuid.random(), content = "My separate copy")
            repository.create(original)
            repository.create(copy)
            val base = EntryEditBase(original, repository.getNoteRecoveryScope(), copy.uid)
            val racing =
                object : JournalNotesRepository by repository {
                    override suspend fun getNoteById(noteId: kotlin.uuid.Uuid): JournalNote? {
                        val captured = repository.getNoteById(noteId)
                        if (noteId == copy.uid) repository.removeById(noteId)
                        return captured
                    }
                }
            val add = AddNoteUseCase(racing, koin.get(), koin.get(), koin.get(), koin.get(), koin.get())
            val save = SaveEntryUseCase(add, koin.get<DeleteEntryDraftUseCase>())
            assertFailsWith<EntryEditConflictException> { save(listOf(copy), emptyList(), null, editBase = base) }
            assertNull(repository.getNoteById(copy.uid))
            assertEquals(original, repository.getNoteById(original.uid))
        }

    @Test
    fun failedCopyDraftWritesCannotBeBypassedByOrdinarySave() =
        runBlocking {
            requireEmulatorFixture()
            val koin = GlobalContext.get()
            val repository = koin.get<JournalNotesRepository>()
            val drafts = koin.get<EntryDraftRepository>()
            var rejectCopies = true
            val copyFailures = Channel<Unit>(Channel.UNLIMITED)
            val failing =
                object : EntryDraftRepository by drafts {
                    override suspend fun saveDraftSnapshot(draft: EntryDraft): kotlin.uuid.Uuid {
                        if (rejectCopies && draft.editBase?.copyNoteId != null) {
                            copyFailures.trySend(Unit)
                            throw java.io.IOException("Synthetic full draft storage")
                        }
                        return drafts.saveDraftSnapshot(draft)
                    }
                }
            val now = kotlin.time.Instant.fromEpochMilliseconds(Clock.System.now().toEpochMilliseconds())
            val original = JournalNote.Text(content = "Original for interrupted copy", creationTimestamp = now, lastUpdated = now)
            repository.create(original)
            withEditor(failing) { model ->
                withContext(Dispatchers.Main) { model.loadExistingEntry(original.uid) }
                val loaded = withTimeout(15_000) { model.editorState.first { it.blocks.any { block -> block.id == original.uid } } }
                withContext(Dispatchers.Main) {
                    model.updateBlock((loaded.blocks.single() as TextBlockUiState).copy(content = "Retained despite storage failure"))
                }
                koin.get<SaveEntryUseCase>()(
                    listOf(original.copy(content = "Other saved edit")),
                    emptyList(),
                    null,
                    editBase = EntryEditBase(original, repository.getNoteRecoveryScope()),
                )
                val edited =
                    withTimeout(15_000) {
                        model.editorState.first {
                            (it.blocks.singleOrNull() as? TextBlockUiState)?.content ==
                                "Retained despite storage failure"
                        }
                    }
                withContext(Dispatchers.Main) { model.saveEntry(edited) }
                withTimeout(15_000) { model.editorState.first { it.entryEditConflict && !it.isSaving } }
                withContext(Dispatchers.Main) { model.saveConflictingVersionSeparately() }
                withTimeout(15_000) { copyFailures.receive() }
                val failed = withTimeout(15_000) { model.editorState.first { !it.isSaving && it.errorMessage != null } }
                val copyId = assertNotNull(failed.entryEditBase?.copyNoteId)
                assertNull(repository.getNoteById(copyId))
                withContext(Dispatchers.Main) { model.saveEntry(failed) }
                withTimeout(15_000) { copyFailures.receive() }
                withTimeout(15_000) { model.editorState.first { !it.isSaving && it.errorMessage != null } }
                assertNull(repository.getNoteById(copyId), "Ordinary Save must also wait for durable copy identity")
                withContext(Dispatchers.Main) {
                    rejectCopies = false
                    model.saveEntry(model.editorState.value)
                }
                withTimeout(15_000) { model.editorState.first { it.shouldExit } }
                assertEquals("Retained despite storage failure", (repository.getNoteById(copyId) as JournalNote.Text).content)
                assertEquals("Other saved edit", (repository.getNoteById(original.uid) as JournalNote.Text).content)
            }
        }

    private suspend fun withEditor(
        drafts: EntryDraftRepository? = null,
        block: suspend (EntryEditorViewModel) -> Unit,
    ) = coroutineScope {
        val owner = withContext(Dispatchers.Main) { RestoredEditorTestOwner() }
        val model =
            withContext(Dispatchers.Main) {
                val koin = GlobalContext.get()
                owner.editor(
                    draftManager =
                        drafts?.let {
                            DraftManager(
                                UpdateEntryDraftUseCase(drafts),
                                CreateEntryDraftUseCase(drafts),
                                FetchEntryDraftUseCase(drafts),
                                DeleteEntryDraftUseCase(drafts, koin.get()),
                                DeleteAllDraftsUseCase(drafts),
                                CleanupExpiredDraftsUseCase(drafts),
                            )
                        },
                )
            }
        val collection = launch { model.editorState.collect { } }
        try {
            block(model)
        } finally {
            collection.cancel()
            withContext(Dispatchers.Main) { owner.close() }
        }
    }

    @Test
    fun restoredOwnerRetainsDraftIdentityAndFreshEditorsHaveIndependentHandles() =
        runBlocking {
            requireEmulatorFixture()
            val owners = mutableListOf<RestoredEditorTestOwner>()

            suspend fun owner() = withContext(Dispatchers.Main) { RestoredEditorTestOwner().also(owners::add) }
            val firstOwner = owner()
            val otherOwner = owner()
            val first = withContext(Dispatchers.Main) { firstOwner.editor() }
            val other = withContext(Dispatchers.Main) { otherOwner.editor() }
            val firstCollection = launch { first.editorState.collect { } }
            var restoredCollection: kotlinx.coroutines.Job? = null
            try {
                withContext(Dispatchers.Main) {
                    assertSame(first, firstOwner.editor(), "One visit must retain its ViewModel")
                    assertNotSame(first, other)
                    first.appendTextBlock("Retained owner draft")
                }
                val edited = withTimeout(15_000) { first.editorState.first { it.blocks.isNotEmpty() } }
                assertTrue(withContext(Dispatchers.Main) { first.saveAsDraft(edited) })
                val draftId = (first.editorState.value.draftState as DraftState.Active).id
                assertEquals(draftId, first.recoverableDraftId)
                assertNull(other.recoverableDraftId, "Another editor must not inherit the recording/draft handle")
                val snapshot = withContext(Dispatchers.Main) { firstOwner.save().also { firstOwner.close() } }
                owners.remove(firstOwner)
                val restoredOwner = withContext(Dispatchers.Main) { RestoredEditorTestOwner(snapshot).also(owners::add) }
                val restored = withContext(Dispatchers.Main) { restoredOwner.editor() }
                assertNotSame(first, restored, "Restoration must construct a new ViewModel")
                assertEquals(draftId, restored.recoverableDraftId, "SavedStateRegistry must restore the durable draft destination")
                restoredCollection = launch { restored.editorState.collect { } }
                withContext(Dispatchers.Main) { restored.loadDraft(draftId) }
                val loaded = withTimeout(15_000) { restored.editorState.first { it.draftState == DraftState.Active(draftId) } }
                assertEquals("Retained owner draft", (loaded.blocks.single() as TextBlockUiState).content)
                val fresh = withContext(Dispatchers.Main) { owner().editor() }
                assertNull(fresh.recoverableDraftId)
                assertFalse(fresh.editorState.value.shouldExit)
            } finally {
                firstCollection.cancel()
                restoredCollection?.cancel()
                withContext(Dispatchers.Main) { owners.forEach(RestoredEditorTestOwner::close) }
            }
        }

    private fun requireEmulatorFixture() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("restoredEntryEditing") == "true")
        require(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
        require(ApplicationProvider.getApplicationContext<Context>().packageName.endsWith(".debug"))
    }

    @Test
    fun retainedEntryEditsKeepTheirIdentityAndFullContent() =
        runBlocking {
            requireEmulatorFixture()
            val koin = GlobalContext.get()
            val repository = koin.get<JournalNotesRepository>()
            val save = koin.get<SaveEntryUseCase>()
            val now = kotlin.time.Instant.fromEpochMilliseconds(Clock.System.now().toEpochMilliseconds())
            val original =
                JournalNote.Text(
                    content = "Retained native entry ".repeat(6_000),
                    creationTimestamp = now - 2.days,
                    lastUpdated = now - 1.days,
                    syncVersion = 7,
                    timeZoneId = "America/Los_Angeles",
                    capturedOn = CaptureDevice.WATCH,
                    capturedDuring = CaptureActivity.MOVING,
                )
            repository.create(original)
            val count = repository.allNotesObserved.first().size
            val edited = original.copy(content = original.content + "Edited after restore", lastUpdated = now)
            save(listOf(edited), emptyList(), activeDraftId = null, editBase = EntryEditBase(original, repository.getNoteRecoveryScope()))
            val stored = assertNotNull(repository.getNoteById(original.uid)) as JournalNote.Text
            assertTrue(edited == stored, "Editing must preserve every field and the complete entry")
            assertEquals(count, repository.allNotesObserved.first().size)
            save(listOf(edited), emptyList(), activeDraftId = null, editBase = EntryEditBase(original, repository.getNoteRecoveryScope()))
            assertEquals(stored, repository.getNoteById(original.uid))
            assertEquals(count, repository.allNotesObserved.first().size)
            assertFailsWith<IllegalStateException> { repository.create(original.copy(content = "Conflicting import")) }
            assertEquals(stored, repository.getNoteById(original.uid))
        }
}
