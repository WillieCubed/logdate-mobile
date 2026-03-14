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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for the text editing functionality in the editor.
 * These tests verify block creation and updates in the editor state.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TextEditingTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var testScope: TestScope
    private lateinit var viewModel: EntryEditorViewModel

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        testScope = TestScope(testDispatcher)

        val journalNotesRepository = FakeJournalNotesRepository()
        val journalContentRepository = FakeJournalContentRepository()
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
        val deleteEntryDraft = DeleteEntryDraftUseCase(entryDraftRepository)

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

        viewModel =
            EntryEditorViewModel(
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
    fun testCreateNewTextBlock() =
        testScope.runTest {
            val block = viewModel.createNewBlock(BlockType.TEXT) as TextBlockUiState
            advanceUntilIdle()

            assertEquals("", block.content)

            val state = viewModel.editorState.value
            assertTrue(state.blocks.any { it.id == block.id })
            assertFalse(state.isReadOnly(block.id))
        }

    @Test
    fun testUpdateTextBlockContent() =
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
    fun testMultipleBlockEditing() =
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
    fun testEmptyBlockCreation() =
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
    fun testClearSingleEmptyBlockReturnsEditorToPicker() =
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
    fun testDismissExpandedBlockOrClearSingleEmptyPreservesContent() =
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
    fun testSingleEmptyVideoBlockDoesNotClearToPicker() =
        testScope.runTest {
            viewModel.createNewBlock(BlockType.VIDEO)
            advanceUntilIdle()

            val cleared = viewModel.clearSingleEmptyBlock()
            advanceUntilIdle()

            assertFalse(cleared)
            assertEquals(1, viewModel.editorState.value.blocks.size)
        }

    @Test
    fun testAppendTextBlockAddsNewBlock() =
        testScope.runTest {
            viewModel.appendTextBlock("Hello from drag-and-drop")
            advanceUntilIdle()

            val state = viewModel.editorState.value
            assertEquals(1, state.blocks.size)
            val block = state.blocks.first() as TextBlockUiState
            assertEquals("Hello from drag-and-drop", block.content)
        }

    @Test
    fun testAppendTextBlockWithBlankTextIsIgnored() =
        testScope.runTest {
            viewModel.appendTextBlock("   ")
            advanceUntilIdle()

            assertTrue(
                viewModel.editorState.value.blocks
                    .isEmpty(),
            )
        }

    @Test
    fun testAppendTextBlockWithEmptyStringIsIgnored() =
        testScope.runTest {
            viewModel.appendTextBlock("")
            advanceUntilIdle()

            assertTrue(
                viewModel.editorState.value.blocks
                    .isEmpty(),
            )
        }

    @Test
    fun testAppendTextBlockSetsIsModified() =
        testScope.runTest {
            viewModel.appendTextBlock("Some dropped text")
            advanceUntilIdle()

            assertTrue(viewModel.editorState.value.isDirty)
        }

    @Test
    fun testAppendTextBlockOnPopulatedEditorAppendsToEnd() =
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
    fun testAppendTextBlockMultipleDropsAppendInOrder() =
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
}
