package app.logdate.feature.editor.ui.editor

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.createComposeRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import org.junit.Rule
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Tests for the rememberAutoSaveHandler and rememberEditorAutoSave functions.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AutoSaveHandlerTest {
    
    @get:Rule
    val composeTestRule = createComposeRule()
    
    // Test dependencies
    private lateinit var testScope: TestScope
    private lateinit var saveTracker: SaveTracker
    
    @BeforeTest
    fun setup() {
        // Create test scope with controllable time
        testScope = TestScope(UnconfinedTestDispatcher())
        saveTracker = SaveTracker()
    }
    
    @Test
    fun testAutoSaveStateHasCorrectInitialValues() {
        composeTestRule.setContent {
            val autoSaveState = rememberAutoSaveHandler(
                content = "Test content",
                onSave = { _: String -> Unit },
                hasContentChanged = { _, _ -> false }
            )
            
            LaunchedEffect(Unit) {
                // Verify initial state
                assertEquals(AutoSaveStatus.IDLE, autoSaveState.status)
                assertNull(autoSaveState.lastSavedTimestamp)
                assertNull(autoSaveState.error)
                assertEquals(0, autoSaveState.saveAttempts)
            }
        }
    }
    
    @Test
    fun testEditorAutoSaveDetectsChanges() {
        var saveCount = 0
        var lastSavedState: EditorState? = null
        
        composeTestRule.setContent {
            // Set up initial state
            val initialBlock = TextBlockUiState(content = "Initial content")
            val editorState = remember { 
                mutableStateOf(EditorState(
                    blocks = listOf(initialBlock),
                    isModified = false
                ))
            }
            
            // Create auto-save handler
            val autoSaveState = rememberEditorAutoSave(
                editorState = editorState.value,
                onAutoSave = { state ->
                    saveCount++
                    lastSavedState = state
                }
            )
            
            // Test behavior when state changes
            LaunchedEffect(Unit) {
                // First update - should trigger save because isModified = true
                val updatedBlock = initialBlock.copy(content = "Updated content")
                editorState.value = editorState.value.copy(
                    blocks = listOf(updatedBlock),
                    isModified = true
                )
            }
        }
        
        // Run the test and advance time to allow debounce to complete
        composeTestRule.mainClock.advanceTimeBy(3000) // 3 seconds
        
        // Verify save was triggered
        assertEquals(1, saveCount)
        assertNotNull(lastSavedState)
        val savedBlock = lastSavedState?.blocks?.firstOrNull() as? TextBlockUiState
        assertNotNull(savedBlock)
        assertEquals("Updated content", savedBlock.content)
    }
    
    @Test
    fun testContentChangeDetection() {
        var saveCount = 0
        
        composeTestRule.setContent {
            // Create two states with the same content but different modified flags
            val unmodifiedState = remember {
                mutableStateOf(EditorState(
                    blocks = listOf(TextBlockUiState(content = "Test content")),
                    isModified = false
                ))
            }
            
            val modifiedState = remember {
                mutableStateOf(EditorState(
                    blocks = listOf(TextBlockUiState(content = "Test content")),
                    isModified = true
                ))
            }
            
            // Test unmodified state
            rememberEditorAutoSave(
                editorState = unmodifiedState.value,
                onAutoSave = { saveCount++ }
            )
            
            // Test modified state (should trigger save)
            rememberEditorAutoSave(
                editorState = modifiedState.value,
                onAutoSave = { saveCount++ }
            )
        }
        
        // Run the test and advance time to allow debounce to complete
        composeTestRule.mainClock.advanceTimeBy(3000)
        
        // Only the modified state should trigger a save
        assertEquals(1, saveCount)
    }
    
    @Test
    fun testDisablingPreventsAutoSave() {
        var saveCount = 0
        
        composeTestRule.setContent {
            // Create a state that would normally trigger save
            val state = remember {
                mutableStateOf(EditorState(
                    blocks = listOf(TextBlockUiState(content = "Test content")),
                    isModified = true
                ))
            }
            
            // But disable auto-save
            rememberEditorAutoSave(
                editorState = state.value,
                onAutoSave = { saveCount++ },
                enabled = false
            )
        }
        
        // Run the test and advance time
        composeTestRule.mainClock.advanceTimeBy(3000)
        
        // No saves should have occurred
        assertEquals(0, saveCount)
    }
    
    // Helper class to track saves and errors
    private class SaveTracker {
        var saveCount = 0
            private set
        
        var lastSavedState: EditorState? = null
            private set
            
        var lastError: Throwable? = null
            private set
            
        fun recordSave(state: EditorState) {
            saveCount++
            lastSavedState = state
            lastError = null
        }
        
        fun recordError(error: Throwable) {
            lastError = error
        }
    }
}