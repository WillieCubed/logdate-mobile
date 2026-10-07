package app.logdate.feature.editor.ui.editor

import app.logdate.client.domain.editor.ObserveEditorDataUseCase
import app.logdate.client.domain.editor.SaveEntryUseCase
import app.logdate.client.domain.journals.GetCurrentUserJournalsUseCase
import app.logdate.client.domain.journals.GetDefaultSelectedJournalsUseCase
import app.logdate.client.domain.location.LocationRetryWorker
import app.logdate.client.domain.location.LogCurrentLocationUseCase
import app.logdate.client.domain.notes.AddNoteUseCase
import app.logdate.client.domain.notes.FetchEntryUseCase
import app.logdate.client.domain.notes.FetchTodayNotesUseCase
import app.logdate.client.domain.notes.drafts.CleanupExpiredDraftsUseCase
import app.logdate.client.domain.notes.drafts.CreateEntryDraftUseCase
import app.logdate.client.domain.notes.drafts.DeleteAllDraftsUseCase
import app.logdate.client.domain.notes.drafts.DeleteEntryDraftUseCase
import app.logdate.client.domain.notes.drafts.FetchEntryDraftUseCase
import app.logdate.client.domain.notes.drafts.FetchMostRecentDraftUseCase
import app.logdate.client.domain.notes.drafts.GetAllDraftsUseCase
import app.logdate.client.domain.notes.drafts.UpdateEntryDraftUseCase
import app.logdate.client.domain.world.LogLocationUseCase
import app.logdate.client.repository.journals.JournalNote
import app.logdate.feature.editor.ui.editor.delegate.ContentLoader
import app.logdate.feature.editor.ui.editor.delegate.DraftManager
import app.logdate.feature.editor.ui.editor.fakes.FakeActivityTimelineRepository
import app.logdate.feature.editor.ui.editor.fakes.FakeClientLocationProvider
import app.logdate.feature.editor.ui.editor.fakes.FakeEntryDraftRepository
import app.logdate.feature.editor.ui.editor.fakes.FakeJournalContentRepository
import app.logdate.feature.editor.ui.editor.fakes.FakeJournalNotesRepository
import app.logdate.feature.editor.ui.editor.fakes.FakeJournalRepository
import app.logdate.feature.editor.ui.editor.fakes.FakeLocationHistoryRepository
import app.logdate.feature.editor.ui.editor.fakes.FakeLocationTrackingSettingsRepository
import app.logdate.feature.editor.ui.editor.fakes.FakeMediaManager
import app.logdate.shared.model.Journal
import app.logdate.shared.model.PhotoPresentation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Tests for the text editing functionality in the editor.
 * These tests verify block creation and updates in the editor state.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TextEditingTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var testScope: TestScope
    private lateinit var viewModel: EntryEditorViewModel
    private lateinit var journalNotesRepository: FakeJournalNotesRepository
    private lateinit var journalContentRepository: FakeJournalContentRepository

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        testScope = TestScope(testDispatcher)

        journalNotesRepository = FakeJournalNotesRepository()
        journalContentRepository = FakeJournalContentRepository()
        viewModel = createViewModel()
    }

    private fun createViewModel(): EntryEditorViewModel {
        val journalRepository = FakeJournalRepository()
        val entryDraftRepository = FakeEntryDraftRepository()

        val locationProvider = FakeClientLocationProvider()
        val activityTimelineRepository = FakeActivityTimelineRepository()
        val locationHistoryRepository = FakeLocationHistoryRepository()
        val locationRetryWorker =
            LocationRetryWorker(
                locationProvider = locationProvider,
                locationHistoryRepository = locationHistoryRepository,
                coroutineScope = testScope.backgroundScope,
            )
        val logLocationUseCase = LogLocationUseCase(locationProvider, activityTimelineRepository)
        val logCurrentLocationUseCase =
            LogCurrentLocationUseCase(
                locationProvider = locationProvider,
                locationHistoryRepository = locationHistoryRepository,
                locationRetryWorker = locationRetryWorker,
                canonicalOwnerProvider = TestCanonicalOwnerProvider(),
                deviceIdProvider = TestDeviceIdProvider(),
            )
        val mediaManager = FakeMediaManager()

        val addNoteUseCase =
            AddNoteUseCase(
                repository = journalNotesRepository,
                journalContentRepository = journalContentRepository,
                logLocationUseCase = logLocationUseCase,
                logCurrentLocationUseCase = logCurrentLocationUseCase,
                settingsRepository = FakeLocationTrackingSettingsRepository(),
                mediaManager = mediaManager,
            )
        val deleteEntryDraft = DeleteEntryDraftUseCase(entryDraftRepository, journalNotesRepository)

        val observeEditorData =
            ObserveEditorDataUseCase(
                fetchTodayNotes = FetchTodayNotesUseCase(journalNotesRepository),
                getCurrentUserJournals = GetCurrentUserJournalsUseCase(journalRepository),
                fetchMostRecentDraft = FetchMostRecentDraftUseCase(entryDraftRepository),
                getAllDrafts = GetAllDraftsUseCase(entryDraftRepository),
            )
        val saveEntryUseCase =
            SaveEntryUseCase(
                addNoteUseCase = addNoteUseCase,
                deleteEntryDraft = deleteEntryDraft,
            )
        val draftManager =
            DraftManager(
                updateEntryDraft = UpdateEntryDraftUseCase(entryDraftRepository),
                createEntryDraft = CreateEntryDraftUseCase(entryDraftRepository),
                fetchEntryDraft = FetchEntryDraftUseCase(entryDraftRepository),
                deleteEntryDraft = deleteEntryDraft,
                deleteAllDraftsUseCase = DeleteAllDraftsUseCase(entryDraftRepository),
                cleanupExpiredDraftsUseCase = CleanupExpiredDraftsUseCase(entryDraftRepository),
            )
        val contentLoader =
            ContentLoader(
                fetchEntryUseCase = FetchEntryUseCase(journalNotesRepository),
                getDefaultSelectedJournals =
                    GetDefaultSelectedJournalsUseCase(
                        journalNotesRepository,
                        journalContentRepository,
                    ),
            )

        return EntryEditorViewModel(
            observeEditorData = observeEditorData,
            saveEntryUseCase = saveEntryUseCase,
            draftManager = draftManager,
            contentLoader = contentLoader,
        )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `default journals completing during an entry fetch do not discard the entry`() =
        testScope.runTest {
            val noteId = Uuid.random()
            val timestamp = Instant.parse("2020-01-01T00:00:00Z")
            journalNotesRepository.seedExternally(
                JournalNote.Text(uid = noteId, content = "Stored memory", creationTimestamp = timestamp, lastUpdated = timestamp),
            )
            val defaultJournal = Journal(id = Uuid.random(), title = "Recent journal")
            journalContentRepository.journalsByContent[noteId] = listOf(defaultJournal)
            val defaultsGate = CompletableDeferred<Unit>()
            journalContentRepository.journalLoadGates[noteId] = defaultsGate
            val entryGate = CompletableDeferred<Unit>()
            journalNotesRepository.getNoteGates[noteId] = entryGate
            viewModel = createViewModel()
            viewModel.loadExistingEntry(noteId)
            runCurrent()
            defaultsGate.complete(Unit)
            runCurrent()
            assertEquals(listOf(defaultJournal.id), viewModel.editorState.value.selectedJournalIds)
            entryGate.complete(Unit)
            advanceUntilIdle()
            assertEquals(
                listOf(noteId),
                viewModel.editorState.value.blocks
                    .map { it.id },
            )
            val block =
                viewModel.editorState.value.blocks
                    .single() as TextBlockUiState
            assertEquals(noteId, block.id)
            assertEquals("Stored memory", block.content)
            assertFalse(viewModel.editorState.value.isModified)
        }

    @Test
    fun `delayed entry loading preserves edits made during the fetch`() =
        testScope.runTest {
            advanceUntilIdle()
            val noteId = Uuid.random()
            val timestamp = Instant.parse("2020-01-01T00:00:00Z")
            journalNotesRepository.create(
                JournalNote.Text(uid = noteId, content = "Stored", creationTimestamp = timestamp, lastUpdated = timestamp),
            )
            val gate = CompletableDeferred<Unit>()
            journalNotesRepository.getNoteGates[noteId] = gate
            viewModel.loadExistingEntry(noteId)
            runCurrent()
            val newBlock = viewModel.createNewBlock(BlockType.TEXT) as TextBlockUiState
            viewModel.updateBlock(newBlock.copy(content = "Keep my new work"))
            gate.complete(Unit)
            advanceUntilIdle()
            assertEquals(
                "Keep my new work",
                (
                    viewModel.editorState.value.blocks
                        .single() as TextBlockUiState
                ).content,
            )
            assertTrue(viewModel.editorState.value.isDirty)
        }

    @Test
    fun `an older fetch cannot replace a newer requested entry`() =
        testScope.runTest {
            advanceUntilIdle()
            val timestamp = Instant.parse("2020-01-01T00:00:00Z")
            val first =
                JournalNote.Text(
                    uid = Uuid.random(),
                    content = "Old request",
                    creationTimestamp = timestamp,
                    lastUpdated = timestamp,
                )
            val second = first.copy(uid = Uuid.random(), content = "Latest request")
            journalNotesRepository.create(first)
            journalNotesRepository.create(second)
            val gate = CompletableDeferred<Unit>()
            journalNotesRepository.getNoteGates[first.uid] = gate
            viewModel.loadExistingEntry(first.uid)
            runCurrent()
            viewModel.loadExistingEntry(second.uid)
            runCurrent()
            gate.complete(Unit)
            advanceUntilIdle()
            assertEquals(
                second.uid,
                viewModel.editorState.value.blocks
                    .single()
                    .id,
            )
        }

    @Test
    fun `an externally published block refuses edits that the save pipeline would omit`() =
        testScope.runTest {
            val block = viewModel.createNewBlock(BlockType.TEXT) as TextBlockUiState
            viewModel.updateBlock(block.copy(content = "Original"))
            advanceUntilIdle()
            journalNotesRepository.create(
                JournalNote.Text(
                    uid = block.id,
                    content = "Original",
                    creationTimestamp = block.timestamp,
                    lastUpdated = block.timestamp,
                ),
            )
            advanceUntilIdle()
            assertTrue(viewModel.editorState.value.isReadOnly(block.id))
            val revision = viewModel.editorState.value.contentRevision
            viewModel.updateBlock(block.copy(content = "Cannot silently discard this edit"))
            advanceUntilIdle()
            assertEquals(
                "Original",
                (
                    viewModel.editorState.value.blocks
                        .single() as TextBlockUiState
                ).content,
            )
            assertEquals(revision, viewModel.editorState.value.contentRevision)
        }

    @Test
    fun `removing an externally published block cannot silently hide a stored memory`() =
        testScope.runTest {
            val block = viewModel.createNewBlock(BlockType.TEXT) as TextBlockUiState
            viewModel.updateBlock(block.copy(content = "Original"))
            journalNotesRepository.create(
                JournalNote.Text(
                    uid = block.id,
                    content = "Original",
                    creationTimestamp = block.timestamp,
                    lastUpdated = block.timestamp,
                ),
            )
            advanceUntilIdle()
            assertTrue(viewModel.editorState.value.isReadOnly(block.id))
            viewModel.removeBlock(block.id)
            advanceUntilIdle()
            assertEquals(
                block.id,
                viewModel.editorState.value.blocks
                    .single()
                    .id,
            )
        }

    @Test
    fun `callbacks for absent blocks do not modify the entry`() =
        testScope.runTest {
            advanceUntilIdle()
            val revision = viewModel.editorState.value.contentRevision
            viewModel.updateBlock(TextBlockUiState(content = "A disposed block"))
            viewModel.removeBlock(Uuid.random())
            advanceUntilIdle()
            assertEquals(revision, viewModel.editorState.value.contentRevision)
            assertFalse(viewModel.editorState.value.isModified)
        }

    @Test
    fun `removing live audio preserves the recording block`() =
        testScope.runTest {
            val block = viewModel.createNewBlock(BlockType.AUDIO) as AudioBlockUiState
            viewModel.updateBlock(block.copy(captureState = AudioCaptureState.Recording("/recording.m4a")))
            advanceUntilIdle()
            viewModel.removeBlock(block.id)
            advanceUntilIdle()
            assertEquals(
                block.id,
                viewModel.editorState.value.blocks
                    .single()
                    .id,
            )
            assertNotNull(viewModel.editorState.value.errorMessage)
        }

    @Test
    fun `a callback cannot change an existing blocks type`() =
        testScope.runTest {
            val block = viewModel.createNewBlock(BlockType.TEXT) as TextBlockUiState
            viewModel.updateBlock(block.copy(content = "Keep this text"))
            viewModel.updateBlock(AudioBlockUiState(id = block.id))
            advanceUntilIdle()
            assertEquals(
                "Keep this text",
                (
                    viewModel.editorState.value.blocks
                        .single() as TextBlockUiState
                ).content,
            )
        }

    @Test
    fun `a delayed recording callback cannot turn saved audio back into a live recording`() =
        testScope.runTest {
            val block = viewModel.createNewBlock(BlockType.AUDIO) as AudioBlockUiState
            val live = block.copy(captureState = AudioCaptureState.Recording("/recording.m4a"))
            viewModel.updateBlock(live)
            viewModel.updateBlock(live.copy(captureState = AudioCaptureState.Ready("file:///recording.m4a", 1000L)))
            viewModel.updateBlock(live)
            advanceUntilIdle()
            assertTrue(
                (
                    viewModel.editorState.value.blocks
                        .single() as AudioBlockUiState
                ).captureState is AudioCaptureState.Ready,
            )
        }

    @Test
    fun `a caption remains editable while the recording is finishing`() =
        testScope.runTest {
            val block = viewModel.createNewBlock(BlockType.AUDIO) as AudioBlockUiState
            val stopping = block.copy(captureState = AudioCaptureState.Stopping("/recording.m4a"))
            viewModel.updateBlock(stopping)
            viewModel.updateBlock(stopping.copy(caption = "Written during Finish"))
            advanceUntilIdle()
            assertEquals(
                "Written during Finish",
                (
                    viewModel.editorState.value.blocks
                        .single() as AudioBlockUiState
                ).caption,
            )
        }

    @Test
    fun `finishing a recording preserves a caption edited after capture started`() =
        testScope.runTest {
            val block = viewModel.createNewBlock(BlockType.AUDIO) as AudioBlockUiState
            val live = block.copy(captureState = AudioCaptureState.Recording("/recording.m4a"))
            viewModel.updateBlock(live)
            viewModel.updateBlock(live.copy(caption = "My current caption"))
            viewModel.updateBlock(live.copy(captureState = AudioCaptureState.Ready("file:///recording.m4a", 1000L)))
            advanceUntilIdle()
            assertEquals(
                "My current caption",
                (
                    viewModel.editorState.value.blocks
                        .single() as AudioBlockUiState
                ).caption,
            )
        }

    @Test
    fun `finishing image selection preserves a caption edited while selection was pending`() =
        testScope.runTest {
            val block = viewModel.createNewBlock(BlockType.IMAGE) as ImageBlockUiState
            viewModel.updateBlock(block.copy(caption = "My photo caption"))
            viewModel.updateBlock(block.copy(uri = "file:///photo.jpg"))
            advanceUntilIdle()
            assertEquals(
                "My photo caption",
                (
                    viewModel.editorState.value.blocks
                        .single() as ImageBlockUiState
                ).caption,
            )
        }

    @Test
    fun `create new text block`() =
        testScope.runTest {
            val block = viewModel.createNewBlock(BlockType.TEXT) as TextBlockUiState
            advanceUntilIdle()

            assertEquals("", block.content)

            val state = viewModel.editorState.value
            assertTrue(state.blocks.any { it.id == block.id })
            assertFalse(state.isReadOnly(block.id))
        }

    @Test
    fun `creating a block with an id that already exists reuses it instead of duplicating`() =
        testScope.runTest {
            val first = viewModel.createNewBlock(BlockType.AUDIO)
            advanceUntilIdle()

            // A picker tile fires with the same pre-generated id when it is tapped again
            // while its morph into the block is still animating.
            val second = viewModel.createNewBlock(BlockType.AUDIO, id = first.id)
            advanceUntilIdle()

            val state = viewModel.editorState.value
            assertEquals(1, state.blocks.count { it.id == first.id })
            assertEquals(first.id, second.id)
            assertEquals(first.id, state.expandedBlockId)
        }

    @Test
    fun `creating a block with an existing id keeps the existing block content`() =
        testScope.runTest {
            val block = viewModel.createNewBlock(BlockType.TEXT) as TextBlockUiState
            viewModel.updateBlock(block.copy(content = "Keep me"))
            advanceUntilIdle()

            val reused = viewModel.createNewBlock(BlockType.TEXT, id = block.id) as TextBlockUiState
            advanceUntilIdle()

            assertEquals("Keep me", reused.content)
            val state = viewModel.editorState.value
            assertEquals(1, state.blocks.size)
            assertEquals("Keep me", (state.blocks.single() as TextBlockUiState).content)
        }

    @Test
    fun `update text block content`() =
        testScope.runTest {
            val block = viewModel.createNewBlock(BlockType.TEXT) as TextBlockUiState
            advanceUntilIdle()

            viewModel.updateBlock(block.copy(content = "Hello, world!"))
            advanceUntilIdle()

            val updatedBlock =
                viewModel.editorState.value.blocks
                    .first { it.id == block.id } as TextBlockUiState

            assertEquals("Hello, world!", updatedBlock.content)
            assertTrue(viewModel.editorState.value.isDirty)
        }

    @Test
    fun `multiple block editing`() =
        testScope.runTest {
            val block1 = viewModel.createNewBlock(BlockType.TEXT) as TextBlockUiState
            val block2 = viewModel.createNewBlock(BlockType.TEXT) as TextBlockUiState
            advanceUntilIdle()

            viewModel.updateBlock(block1.copy(content = "First block content"))
            viewModel.updateBlock(block2.copy(content = "Second block content"))
            advanceUntilIdle()

            val currentState = viewModel.editorState.value
            val updatedBlock1 = currentState.blocks.find { it.id == block1.id } as? TextBlockUiState
            val updatedBlock2 = currentState.blocks.find { it.id == block2.id } as? TextBlockUiState

            assertNotNull(updatedBlock1)
            assertNotNull(updatedBlock2)
            assertEquals("First block content", updatedBlock1.content)
            assertEquals("Second block content", updatedBlock2.content)
            assertTrue(currentState.isDirty)
        }

    @Test
    fun `empty block creation`() =
        testScope.runTest {
            assertTrue(
                viewModel.editorState.value.blocks
                    .isEmpty(),
            )

            val block = viewModel.createNewBlock(BlockType.TEXT) as TextBlockUiState
            advanceUntilIdle()

            val updatedState = viewModel.editorState.value
            assertEquals(1, updatedState.blocks.size)
            assertEquals(block.id, updatedState.blocks.first().id)
        }

    @Test
    fun `initial attachments are not duplicated when editor is recreated`() =
        testScope.runTest {
            val attachments = listOf("content://image/one", "content://video/two")

            // NoteEditorScreen replays its initial-input effect when the Activity is recreated.
            // Re-applying the same intent inputs must not add a second copy of each media block.
            viewModel.setInitialAttachments(attachments)
            advanceUntilIdle()
            viewModel.setInitialAttachments(attachments)
            advanceUntilIdle()

            val blocks = viewModel.editorState.value.blocks
            assertEquals(2, blocks.size)
            assertEquals(
                attachments.toSet(),
                blocks.mapNotNull { (it as? MediaBlockUiState)?.uri }.toSet(),
            )
        }

    @Test
    fun `widget prompt places photo before text input without duplicating blocks`() =
        testScope.runTest {
            val photo = "content://media/external/images/media/42"
            viewModel.initializePhotoPrompt(photo, record = false)
            advanceUntilIdle()
            assertTrue(viewModel.editorState.value.blocks[0] is ImageBlockUiState)
            assertTrue(viewModel.editorState.value.blocks[1] is TextBlockUiState)
            assertEquals(
                photo,
                (
                    viewModel.editorState.value.blocks
                        .first() as ImageBlockUiState
                ).uri,
            )
            viewModel.initializePhotoPrompt(photo, record = false)
            advanceUntilIdle()
            assertEquals(2, viewModel.editorState.value.blocks.size)
        }

    @Test
    fun `widget record prompt places photo before audio input`() =
        testScope.runTest {
            val photo = "content://media/external/images/media/42"
            viewModel.initializePhotoPrompt(photo, record = true)
            advanceUntilIdle()
            assertTrue(viewModel.editorState.value.blocks[0] is ImageBlockUiState)
            assertTrue(viewModel.editorState.value.blocks[1] is AudioBlockUiState)
        }

    @Test
    fun `widget audio action starts one recording block without a photo`() =
        testScope.runTest {
            viewModel.initializeWidgetAudio()
            viewModel.initializeWidgetAudio()
            advanceUntilIdle()
            assertEquals(1, viewModel.editorState.value.blocks.size)
            assertTrue(
                viewModel.editorState.value.blocks
                    .single() is AudioBlockUiState,
            )
        }

    @Test
    fun `widget camera action opens one camera block`() =
        testScope.runTest {
            viewModel.initializeWidgetCamera()
            viewModel.initializeWidgetCamera()
            advanceUntilIdle()
            val state = viewModel.editorState.value
            assertEquals(1, state.blocks.size)
            assertTrue(state.blocks.single() is CameraBlockUiState)
            assertEquals(state.blocks.single().id, state.expandedBlockId)
        }

    @Test
    fun `existing entry edits are not overwritten when load effect replays`() =
        testScope.runTest {
            val noteId = Uuid.random()
            val timestamp = Instant.parse("2020-01-01T00:00:00Z")
            journalNotesRepository.create(
                JournalNote.Text(
                    uid = noteId,
                    content = "Original content",
                    creationTimestamp = timestamp,
                    lastUpdated = timestamp,
                ),
            )

            viewModel.loadExistingEntry(noteId)
            advanceUntilIdle()
            val loaded =
                viewModel.editorState.value.blocks
                    .single() as TextBlockUiState
            viewModel.updateBlock(loaded.copy(content = "Unsaved edit"))
            advanceUntilIdle()

            // NoteEditorScreen's load effect runs again after Activity recreation. It must not
            // replace the surviving ViewModel state with the original repository value.
            viewModel.loadExistingEntry(noteId)
            advanceUntilIdle()

            assertEquals(
                "Unsaved edit",
                (
                    viewModel.editorState.value.blocks
                        .single() as TextBlockUiState
                ).content,
            )
        }

    @Test
    fun `clear single empty block returns editor to picker`() =
        testScope.runTest {
            val emptyTypes =
                listOf(
                    BlockType.TEXT,
                    BlockType.AUDIO,
                    BlockType.IMAGE,
                    BlockType.CAMERA,
                )

            emptyTypes.forEach { type ->
                viewModel.createNewBlock(type)
                advanceUntilIdle()

                val cleared = viewModel.clearSingleEmptyBlock()
                advanceUntilIdle()

                assertTrue(cleared, "Expected $type to clear back to the picker")
                assertTrue(
                    viewModel.editorState.value.blocks
                        .isEmpty(),
                    "Expected $type block to be removed",
                )
                assertFalse(viewModel.editorState.value.isModified, "Expected $type clear to restore pristine state")
            }
        }

    @Test
    fun `dismiss expanded block or clear single empty preserves content`() =
        testScope.runTest {
            val block = viewModel.createNewBlock(BlockType.TEXT) as TextBlockUiState
            advanceUntilIdle()

            viewModel.updateBlock(block.copy(content = "Hello, world!"))
            val updatedBlock =
                viewModel.editorState.value.blocks
                    .first { it.id == block.id } as TextBlockUiState

            viewModel.setExpandedBlockId(updatedBlock.id)
            val dismissed = viewModel.dismissExpandedBlockOrClearSingleEmpty()
            advanceUntilIdle()

            assertTrue(dismissed)
            assertEquals(1, viewModel.editorState.value.blocks.size)
            assertTrue(
                viewModel.editorState.value.blocks
                    .first()
                    .hasContent(),
            )
            assertFalse(viewModel.editorState.value.shouldReturnToPickerOnBack())
            assertEquals(null, viewModel.editorState.value.expandedBlockId)
        }

    @Test
    fun `single empty video block does not clear to picker`() =
        testScope.runTest {
            viewModel.createNewBlock(BlockType.VIDEO)
            advanceUntilIdle()

            val cleared = viewModel.clearSingleEmptyBlock()
            advanceUntilIdle()

            assertFalse(cleared)
            assertEquals(1, viewModel.editorState.value.blocks.size)
        }

    @Test
    fun `append text block adds new block`() =
        testScope.runTest {
            viewModel.appendTextBlock("Hello from drag-and-drop")
            advanceUntilIdle()

            val state = viewModel.editorState.value
            assertEquals(1, state.blocks.size)
            val block = state.blocks.first() as TextBlockUiState
            assertEquals("Hello from drag-and-drop", block.content)
        }

    @Test
    fun `append text block with blank text is ignored`() =
        testScope.runTest {
            viewModel.appendTextBlock("   ")
            advanceUntilIdle()

            assertTrue(
                viewModel.editorState.value.blocks
                    .isEmpty(),
            )
        }

    @Test
    fun `append text block with empty string is ignored`() =
        testScope.runTest {
            viewModel.appendTextBlock("")
            advanceUntilIdle()

            assertTrue(
                viewModel.editorState.value.blocks
                    .isEmpty(),
            )
        }

    @Test
    fun `append text block sets is modified`() =
        testScope.runTest {
            viewModel.appendTextBlock("Some dropped text")
            advanceUntilIdle()

            assertTrue(viewModel.editorState.value.isDirty)
        }

    @Test
    fun `append text block on populated editor appends to end`() =
        testScope.runTest {
            val existing = viewModel.createNewBlock(BlockType.TEXT) as TextBlockUiState
            viewModel.updateBlock(existing.copy(content = "First block"))
            advanceUntilIdle()

            viewModel.appendTextBlock("Dropped text")
            advanceUntilIdle()

            val state = viewModel.editorState.value
            assertEquals(2, state.blocks.size)
            val appended = state.blocks.last() as TextBlockUiState
            assertEquals("Dropped text", appended.content)
        }

    @Test
    fun `append text block multiple drops append in order`() =
        testScope.runTest {
            viewModel.appendTextBlock("First drop")
            viewModel.appendTextBlock("Second drop")
            viewModel.appendTextBlock("Third drop")
            advanceUntilIdle()

            val blocks = viewModel.editorState.value.blocks
            assertEquals(3, blocks.size)
            assertEquals("First drop", (blocks[0] as TextBlockUiState).content)
            assertEquals("Second drop", (blocks[1] as TextBlockUiState).content)
            assertEquals("Third drop", (blocks[2] as TextBlockUiState).content)
        }

    @Test
    fun `stale ready audio transcript update preserves newer caption`() =
        testScope.runTest {
            val initial = viewModel.createNewBlock(BlockType.AUDIO) as AudioBlockUiState
            val base =
                initial.copy(
                    captureState = AudioCaptureState.Ready("file:///recording.m4a", 1000L),
                    caption = "Original",
                    transcription = "Draft",
                )
            viewModel.updateBlock(base)
            viewModel.updateBlock(base.copy(caption = "Edited caption"), base)
            viewModel.updateBlock(base.copy(transcription = "Final transcript"), base)
            advanceUntilIdle()
            val actual =
                viewModel.editorState.value.blocks
                    .single() as AudioBlockUiState
            assertEquals("Edited caption", actual.caption)
            assertEquals("Final transcript", actual.transcription)
        }

    @Test
    fun `stale ready audio caption update preserves newer transcript`() =
        testScope.runTest {
            val initial = viewModel.createNewBlock(BlockType.AUDIO) as AudioBlockUiState
            val base = initial.copy(captureState = AudioCaptureState.Ready("file:///recording.m4a", 1000L), transcription = "Draft")
            viewModel.updateBlock(base)
            viewModel.updateBlock(base.copy(transcription = "Final transcript"), base)
            viewModel.updateBlock(base.copy(caption = "Edited caption"), base)
            advanceUntilIdle()
            val actual =
                viewModel.editorState.value.blocks
                    .single() as AudioBlockUiState
            assertEquals("Edited caption", actual.caption)
            assertEquals("Final transcript", actual.transcription)
        }

    @Test
    fun `stale image caption update preserves selected photo`() =
        testScope.runTest {
            val base = viewModel.createNewBlock(BlockType.IMAGE) as ImageBlockUiState
            viewModel.updateBlock(base.copy(uri = "content://photo"), base)
            viewModel.updateBlock(base.copy(caption = "Edited caption"), base)
            advanceUntilIdle()
            val actual =
                viewModel.editorState.value.blocks
                    .single() as ImageBlockUiState
            assertEquals("content://photo", actual.uri)
            assertEquals("Edited caption", actual.caption)
        }

    @Test
    fun `stale video caption update preserves selected video duration`() =
        testScope.runTest {
            val base = viewModel.createNewBlock(BlockType.VIDEO) as VideoBlockUiState
            viewModel.updateBlock(base.copy(uri = "content://video", durationMs = 1234L), base)
            viewModel.updateBlock(base.copy(caption = "Edited caption"), base)
            advanceUntilIdle()
            val actual =
                viewModel.editorState.value.blocks
                    .single() as VideoBlockUiState
            assertEquals("content://video", actual.uri)
            assertEquals(1234L, actual.durationMs)
            assertEquals("Edited caption", actual.caption)
        }

    @Test
    fun `stale photo selection preserves newer caption`() =
        testScope.runTest {
            val base = viewModel.createNewBlock(BlockType.IMAGE) as ImageBlockUiState
            viewModel.updateBlock(base.copy(caption = "Edited caption"), base)
            viewModel.updateBlock(base.copy(uri = "content://photo"), base)
            advanceUntilIdle()
            val actual =
                viewModel.editorState.value.blocks
                    .single() as ImageBlockUiState
            assertEquals("content://photo", actual.uri)
            assertEquals("Edited caption", actual.caption)
        }

    @Test
    fun `explicit caption clear preserves newer audio transcript`() =
        testScope.runTest {
            val initial = viewModel.createNewBlock(BlockType.AUDIO) as AudioBlockUiState
            viewModel.updateBlock(initial.copy(caption = "Original"))
            val base =
                initial.copy(
                    captureState = AudioCaptureState.Ready("file:///recording.m4a", 1000L),
                    caption = "Original",
                    transcription = "Draft",
                )
            viewModel.updateBlock(base)
            viewModel.updateBlock(base.copy(transcription = "Final transcript"), base)
            viewModel.updateBlock(base.copy(caption = ""), base)
            advanceUntilIdle()
            val actual =
                viewModel.editorState.value.blocks
                    .single() as AudioBlockUiState
            assertEquals("", actual.caption)
            assertEquals("Final transcript", actual.transcription)
        }

    @Test
    fun `stale photo presentation update preserves newer caption`() =
        testScope.runTest {
            val base = viewModel.createNewBlock(BlockType.IMAGE) as ImageBlockUiState
            viewModel.updateBlock(base.copy(caption = "Edited caption"), base)
            viewModel.updateBlock(base.copy(presentation = PhotoPresentation.Framed), base)
            advanceUntilIdle()
            val actual =
                viewModel.editorState.value.blocks
                    .single() as ImageBlockUiState
            assertEquals("Edited caption", actual.caption)
            assertEquals(PhotoPresentation.Framed, actual.presentation)
        }

    @Test
    fun `caption callback from recording snapshot preserves completed audio`() =
        testScope.runTest {
            val initial = viewModel.createNewBlock(BlockType.AUDIO) as AudioBlockUiState
            val base = initial.copy(captureState = AudioCaptureState.Recording("/recording.m4a"))
            val ready = AudioCaptureState.Ready("file:///recording.m4a", 1000L)
            viewModel.updateBlock(base)
            viewModel.updateBlock(base.copy(captureState = ready), base)
            viewModel.updateBlock(base.copy(caption = "Edited caption"), base)
            advanceUntilIdle()
            val actual =
                viewModel.editorState.value.blocks
                    .single() as AudioBlockUiState
            assertEquals(ready, actual.captureState)
            assertEquals("Edited caption", actual.caption)
        }
}
