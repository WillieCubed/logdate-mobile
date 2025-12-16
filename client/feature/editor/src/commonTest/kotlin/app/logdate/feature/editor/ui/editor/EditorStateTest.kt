package app.logdate.feature.editor.ui.editor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertNull
import kotlin.test.assertNotNull

/**
 * Tests for the EditorState class, focusing on state management and modifications.
 */
class EditorStateTest {
    
    @Test
    fun testEmptyState() {
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
    fun testCopyWithNewBlocks() {
        val initialState = EditorState()
        val block = TextBlockUiState(content = "Test content")
        
        // Add a block via copy constructor
        val stateWithBlock = initialState.copy(
            blocks = listOf(block),
            isModified = true
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
    fun testReadOnlyBlocks() {
        val block = TextBlockUiState(content = "Read-only content")
        val readOnlyMap = mapOf(block.id to true)
        
        // Create state with a read-only block
        val state = EditorState(
            blocks = listOf(block),
            readOnlyBlocks = readOnlyMap
        )
        
        // Verify the block is read-only
        assertTrue(state.isReadOnly(block.id))
        
        // Another block should not be read-only
        val anotherBlock = TextBlockUiState()
        assertFalse(state.isReadOnly(anotherBlock.id))
    }
    
    @Test
    fun testExpandedBlockState() {
        val block = TextBlockUiState(content = "Test content")
        
        // Create state with an expanded block
        val state = EditorState(
            blocks = listOf(block),
            expandedBlockId = block.id
        )
        
        // Verify the expanded block ID is set
        assertNotNull(state.expandedBlockId)
        assertEquals(block.id, state.expandedBlockId)
    }
    
    @Test
    fun testIsModifiedFlag() {
        val block = TextBlockUiState(content = "Test content")
        
        // Create initial state with content but not marked as modified
        val initialState = EditorState(
            blocks = listOf(block),
            isModified = false
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
    fun testEquality() {
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
    fun testHasContentWithEmptyBlocks() {
        // Block with no content
        val emptyBlock = TextBlockUiState(content = "")
        
        // State with an empty block
        val stateWithEmptyBlock = EditorState(blocks = listOf(emptyBlock))
        
        // Should have a block but no content
        assertFalse(stateWithEmptyBlock.isEmpty())
        assertFalse(stateWithEmptyBlock.hasContent())
    }
    
    @Test
    fun testMixOfEmptyAndContentBlocks() {
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