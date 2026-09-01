package app.logdate.feature.editor.ui.editor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * Tests for the EditorState class, focusing on state management and modifications.
 */
class EditorStateTest {
    @Test
    fun `empty state`() {
        val state = EditorState()

        // Initial state should be empty
        assertTrue(state.blocks.isEmpty())
        assertTrue(state.isEmpty())
        assertFalse(state.hasContent())
        assertFalse(state.isDirty)
        assertTrue(state.canExitWithoutSaving)
        assertNull(state.expandedBlockId)
    }

    @Test
    fun `copy with new blocks`() {
        val initialState = EditorState()
        val block = TextBlockUiState(content = "Test content")

        // Add a block via copy constructor
        val stateWithBlock =
            initialState.copy(
                blocks = listOf(block),
                isModified = true,
            )

        // State should now have content
        assertFalse(stateWithBlock.isEmpty())
        assertTrue(stateWithBlock.hasContent())
        assertEquals(1, stateWithBlock.blocks.size)
        assertEquals(block.id, stateWithBlock.blocks[0].id)

        // State should be marked as dirty
        assertTrue(stateWithBlock.isDirty)
        assertFalse(stateWithBlock.canExitWithoutSaving)
    }

    @Test
    fun `read only blocks`() {
        val block = TextBlockUiState(content = "Read-only content")
        val readOnlyMap = mapOf(block.id to true)

        // Create state with a read-only block
        val state =
            EditorState(
                blocks = listOf(block),
                readOnlyBlocks = readOnlyMap,
            )

        // Verify the block is read-only
        assertTrue(state.isReadOnly(block.id))

        // Another block should not be read-only
        val anotherBlock = TextBlockUiState()
        assertFalse(state.isReadOnly(anotherBlock.id))
    }

    @Test
    fun `expanded block state`() {
        val block = TextBlockUiState(content = "Test content")

        // Create state with an expanded block
        val state =
            EditorState(
                blocks = listOf(block),
                expandedBlockId = block.id,
            )

        // Verify the expanded block ID is set
        assertNotNull(state.expandedBlockId)
        assertEquals(block.id, state.expandedBlockId)
    }

    @Test
    fun `is modified flag`() {
        val block = TextBlockUiState(content = "Test content")

        // Create initial state with content but not marked as modified
        val initialState =
            EditorState(
                blocks = listOf(block),
                isModified = false,
            )

        // Should have content but not be dirty
        assertTrue(initialState.hasContent())
        assertFalse(initialState.isDirty)
        assertTrue(initialState.canExitWithoutSaving)

        // Create modified state
        val modifiedState = initialState.copy(isModified = true)

        // Should now be dirty and can't exit without saving
        assertTrue(modifiedState.isDirty)
        assertFalse(modifiedState.canExitWithoutSaving)
    }

    @Test
    fun `equality`() {
        val block = TextBlockUiState(content = "Test content")
        val state1 = EditorState(blocks = listOf(block))
        val state2 = EditorState(blocks = listOf(block))
        val state3 = EditorState(blocks = listOf(TextBlockUiState(content = "Different")))

        // Same content should be equal
        assertEquals(state1, state2)

        // Different content should not be equal
        assertFalse(state1 == state3)
    }

    @Test
    fun `has content with empty blocks`() {
        // Block with no content
        val emptyBlock = TextBlockUiState(content = "")

        // State with an empty block
        val stateWithEmptyBlock = EditorState(blocks = listOf(emptyBlock))

        // Should have a block but no content
        assertFalse(stateWithEmptyBlock.isEmpty())
        assertFalse(stateWithEmptyBlock.hasContent())
    }

    @Test
    fun `modified empty active draft cannot exit before clearing persistence`() {
        val state =
            EditorState(
                blocks = emptyList(),
                draftState = DraftState.Active(Uuid.random()),
                isModified = true,
            )

        assertTrue(state.isDirty)
        assertFalse(state.canExitWithoutSaving)
    }

    @Test
    fun `modified journal selection is dirty without blocks`() {
        val state =
            EditorState(
                selectedJournalIds = listOf(Uuid.random()),
                hasJournalSelectionChanges = true,
                isModified = true,
            )

        assertTrue(state.isDirty)
        assertFalse(state.canExitWithoutSaving)
    }

    @Test
    fun `single empty block returns to picker on back`() {
        val emptyBlock = TextBlockUiState(content = "")
        val state = EditorState(blocks = listOf(emptyBlock))

        assertTrue(state.shouldReturnToPickerOnBack())
    }

    @Test
    fun `single block with content does not return to picker on back`() {
        val contentBlock = TextBlockUiState(content = "Has content")
        val state = EditorState(blocks = listOf(contentBlock))

        assertFalse(state.shouldReturnToPickerOnBack())
    }

    @Test
    fun `single empty video block does not return to picker on back`() {
        val emptyBlock = VideoBlockUiState()
        val state = EditorState(blocks = listOf(emptyBlock))

        assertFalse(state.shouldReturnToPickerOnBack())
    }

    @Test
    fun `expanded image block does not activate immersive layout`() {
        val block = ImageBlockUiState(uri = "content://images/1")
        val state =
            EditorState(
                blocks = listOf(block),
                expandedBlockId = block.id,
            )

        assertFalse(state.isImmersiveBlockActive())
    }

    @Test
    fun `unexpanded camera block does not activate immersive layout`() {
        val block = CameraBlockUiState()
        val state = EditorState(blocks = listOf(block))

        assertFalse(state.isImmersiveBlockActive())
    }

    @Test
    fun `expanded camera block activates immersive layout`() {
        val block = CameraBlockUiState()
        val state =
            EditorState(
                blocks = listOf(block),
                expandedBlockId = block.id,
            )

        assertTrue(state.isImmersiveBlockActive())
    }

    @Test
    fun `mix of empty and content blocks`() {
        // Create blocks with and without content
        val emptyBlock = TextBlockUiState(content = "")
        val contentBlock = TextBlockUiState(content = "Has content")

        // State with both types of blocks
        val mixedState = EditorState(blocks = listOf(emptyBlock, contentBlock))

        // Should have content because at least one block has content
        assertFalse(mixedState.isEmpty())
        assertTrue(mixedState.hasContent())
    }
}
