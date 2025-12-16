package app.logdate.feature.editor.ui.editor

import app.logdate.client.domain.journals.GetDefaultSelectedJournalsUseCase
import app.logdate.client.domain.notes.AddNoteUseCase
import app.logdate.client.domain.notes.drafts.CreateEntryDraftUseCase
import app.logdate.client.domain.notes.drafts.DeleteEntryDraftUseCase
import app.logdate.client.domain.notes.drafts.FetchEntryDraftUseCase
import app.logdate.client.domain.notes.drafts.UpdateEntryDraftUseCase
import app.logdate.client.repository.journals.JournalContentRepository
import app.logdate.feature.editor.ui.editor.mediator.EditorMediator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * Tests for the text editing functionality in the editor.
 * This test verifies that:
 * 1. Creating a new text block works
 * 2. Updating a text block properly sets the content and marks it as modified
 * 3. The autosave functionality triggers when state changes
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TextEditingTest {
    
    // Test dependencies
    private lateinit var viewModel: EntryEditorViewModel
    private lateinit var testScope: TestScope
    private lateinit var autoSaveDelegate: FakeAutoSaveDelegate
    private lateinit var journalSelectionDelegate: FakeJournalSelectionDelegate
    private lateinit var editorMediator: FakeEditorMediator
    
    // Track state updates
    private var blockUpdateCount = 0
    private var lastUpdatedBlock: EntryBlockUiState? = null
    private var autoSaveCount = 0
    
    @BeforeTest
    fun setup() {
        // Create test scope with immediate execution
        testScope = TestScope(UnconfinedTestDispatcher())
        
        // Create fake delegates
        autoSaveDelegate = FakeAutoSaveDelegate(testScope.backgroundScope) { 
            autoSaveCount++
        }
        journalSelectionDelegate = FakeJournalSelectionDelegate()
        editorMediator = FakeEditorMediator()
        
        // Create the view model with mocked dependencies
        viewModel = EntryEditorViewModel(
            fetchTodayNotes = { emptyFlow() },
            getCurrentUserJournals = { emptyFlow() },
            getDefaultSelectedJournals = FakeGetDefaultSelectedJournalsUseCase(),
            addNoteUseCase = FakeAddNoteUseCase(),
            journalContentRepository = FakeJournalContentRepository(),
            updateEntryDraft = FakeUpdateEntryDraftUseCase(),
            createEntryDraft = FakeCreateEntryDraftUseCase(),
            deleteEntryDraft = FakeDeleteEntryDraftUseCase(),
            fetchEntryDraft = FakeFetchEntryDraftUseCase(),
            fetchMostRecentDraft = { emptyFlow() },
            getAllDrafts = { emptyFlow() },
            mediator = editorMediator,
            autoSaveDelegate = autoSaveDelegate,
            journalSelectionDelegate = journalSelectionDelegate
        )
        
        // Reset counters
        blockUpdateCount = 0
        autoSaveCount = 0
        lastUpdatedBlock = null
        
        // Add listener to track block updates
        val originalUpdateBlock = viewModel::updateBlock
        viewModel::updateBlock.apply {
            viewModel::updateBlock = { block ->
                blockUpdateCount++
                lastUpdatedBlock = block
                originalUpdateBlock(block)
            }
        }
    }
    
    @Test
    fun testCreateNewTextBlock() = testScope.runTest {
        // Create a new text block
        val block = viewModel.createNewBlock(BlockType.TEXT)
        
        // Verify the block was created with expected values
        assertTrue(block is TextBlockUiState)
        assertEquals("", (block as TextBlockUiState).content)
        assertFalse(viewModel.editorState.value.isReadOnly(block.id))
    }
    
    @Test
    fun testUpdateTextBlockContent() = testScope.runTest {
        // Create a block to update
        val block = viewModel.createNewBlock(BlockType.TEXT) as TextBlockUiState
        
        // Reset counters after setup
        blockUpdateCount = 0
        autoSaveCount = 0
        
        // Update the block with new content
        val updatedBlock = block.copy(content = "Hello, world!")
        viewModel.updateBlock(updatedBlock)
        
        // Verify the block was updated
        assertEquals(1, blockUpdateCount)
        assertEquals("Hello, world!", (lastUpdatedBlock as? TextBlockUiState)?.content)
        
        // The update should have marked the state as modified to trigger autosave
        assertTrue(viewModel.editorState.value.isDirty)
    }
    
    @Test
    fun testMultipleUpdatesCreateSingleAutoSave() = testScope.runTest {
        // Create a block to update
        val block = viewModel.createNewBlock(BlockType.TEXT) as TextBlockUiState
        
        // Reset counters after setup
        blockUpdateCount = 0
        autoSaveCount = 0
        
        // Update the block with new content multiple times in quick succession
        viewModel.updateBlock(block.copy(content = "H"))
        viewModel.updateBlock(block.copy(content = "He"))
        viewModel.updateBlock(block.copy(content = "Hel"))
        viewModel.updateBlock(block.copy(content = "Hell"))
        viewModel.updateBlock(block.copy(content = "Hello"))
        
        // Verify that we had multiple updates
        assertEquals(5, blockUpdateCount)
        
        // Verify the autosave count is exactly 1 (debouncing should group these)
        assertEquals(1, autoSaveCount)
        
        // Last content should be the final update
        assertEquals("Hello", (lastUpdatedBlock as? TextBlockUiState)?.content)
    }
    
    @Test
    fun testEmptyBlockCreation() = testScope.runTest {
        // Trigger the initial state setup
        val initialState = viewModel.editorState.value
        
        // There should be no blocks to start with
        assertTrue(initialState.blocks.isEmpty())
        
        // Create a new text block 
        val block = viewModel.createNewBlock(BlockType.TEXT) as TextBlockUiState
        
        // Check the block is now in state
        val updatedState = viewModel.editorState.value
        assertEquals(1, updatedState.blocks.size)
        assertEquals(block.id, updatedState.blocks.first().id)
    }
    
    @Test
    fun testMultipleBlockEditing() = testScope.runTest {
        // Create multiple blocks
        val block1 = viewModel.createNewBlock(BlockType.TEXT) as TextBlockUiState
        val block2 = viewModel.createNewBlock(BlockType.TEXT) as TextBlockUiState
        
        // Reset counters after setup
        blockUpdateCount = 0
        autoSaveCount = 0
        
        // Update each block
        viewModel.updateBlock(block1.copy(content = "First block content"))
        viewModel.updateBlock(block2.copy(content = "Second block content"))
        
        // Verify both blocks were updated
        assertEquals(2, blockUpdateCount)
        
        // Get the current state and verify block contents
        val currentState = viewModel.editorState.value
        assertEquals(2, currentState.blocks.size)
        
        // Verify the content of each block
        val updatedBlock1 = currentState.blocks.find { it.id == block1.id } as? TextBlockUiState
        val updatedBlock2 = currentState.blocks.find { it.id == block2.id } as? TextBlockUiState
        
        assertNotNull(updatedBlock1)
        assertNotNull(updatedBlock2)
        assertEquals("First block content", updatedBlock1?.content)
        assertEquals("Second block content", updatedBlock2?.content)
        
        // Verify autosave was triggered (we expect a single autosave due to debouncing)
        assertEquals(1, autoSaveCount)
        assertTrue(viewModel.editorState.value.isDirty)
    }
}

// Fake implementations for testing

class FakeAutoSaveDelegate(
    private val scope: kotlinx.coroutines.CoroutineScope,
    private val onAutoSave: () -> Unit
) : app.logdate.feature.editor.ui.editor.delegate.AutoSaveDelegate(
    scope = scope,
    updateEntryDraft = FakeUpdateEntryDraftUseCase(),
    createEntryDraft = FakeCreateEntryDraftUseCase()
) {
    override fun autoSaveEntry(
        state: EditorState,
        onDraftCreated: (Uuid) -> Unit
    ) {
        // Simply record that autosave was called
        onAutoSave()
        
        // Simulate creating a draft
        onDraftCreated(Uuid.fromString("00000000-0000-0000-0000-000000000001"))
    }
}

class FakeJournalSelectionDelegate : app.logdate.feature.editor.ui.editor.delegate.JournalSelectionDelegate(
    getDefaultSelectedJournals = FakeGetDefaultSelectedJournalsUseCase()
)

class FakeEditorMediator : EditorMediator {
    override val editorActions = MutableStateFlow(app.logdate.feature.editor.ui.editor.mediator.EditorActions())
}

class FakeUpdateEntryDraftUseCase : UpdateEntryDraftUseCase {
    override suspend fun invoke(draftId: Uuid, notes: List<app.logdate.client.repository.journals.JournalNote>) {}
}

class FakeCreateEntryDraftUseCase : CreateEntryDraftUseCase {
    override suspend fun invoke(notes: List<app.logdate.client.repository.journals.JournalNote>): Uuid {
        return Uuid.fromString("00000000-0000-0000-0000-000000000001")
    }
}

class FakeDeleteEntryDraftUseCase : DeleteEntryDraftUseCase {
    override suspend fun invoke(draftId: Uuid) {}
}

class FakeFetchEntryDraftUseCase : FetchEntryDraftUseCase {
    override fun invoke(draftId: Uuid) = emptyFlow<Result<app.logdate.client.repository.journals.EntryDraft>>()
}

class FakeGetDefaultSelectedJournalsUseCase : GetDefaultSelectedJournalsUseCase {
    override suspend fun invoke(): List<Uuid> = emptyList()
}

class FakeAddNoteUseCase : AddNoteUseCase {
    override suspend fun invoke(notes: List<app.logdate.client.repository.journals.JournalNote>) {}
}

class FakeJournalContentRepository : JournalContentRepository {
    override suspend fun addContentToJournal(contentId: Uuid, journalId: Uuid) {}
    override suspend fun getJournalContent(journalId: Uuid): List<Uuid> = emptyList()
    override suspend fun removeContentFromJournal(contentId: Uuid, journalId: Uuid) {}
}