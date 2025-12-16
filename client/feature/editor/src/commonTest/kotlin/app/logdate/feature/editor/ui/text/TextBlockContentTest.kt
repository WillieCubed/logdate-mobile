package app.logdate.feature.editor.ui.text

import app.logdate.feature.editor.ui.editor.TextBlockUiState
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * UI tests for the TextBlockContent component.
 * 
 * Note: This uses JUnit 4 and the Compose testing framework. These tests
 * verify the UI behavior of the TextBlockContent composable, ensuring text changes
 * properly update state and trigger callbacks.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TextBlockContentTest {
    
    @get:Rule
    val composeTestRule = createComposeRule()
    
    private lateinit var testScope: TestScope
    private val textChangeTracker = TextChangeTracker()
    
    @BeforeTest
    fun setup() {
        testScope = TestScope(UnconfinedTestDispatcher())
    }
    
    @Test
    fun testTextBlockDisplaysContent() {
        // Prepare a block with content
        val block = TextBlockUiState(content = "Test content")
        
        // Set up the component
        composeTestRule.setContent {
            TextBlockContent(
                block = block,
                isExpanded = false,
                onTextChanged = textChangeTracker::recordTextChange,
                onFocused = {},
                readOnly = false
            )
        }
        
        // Verify the content is displayed
        composeTestRule.onNodeWithText("Test content").assertExists()
    }
    
    @Test
    fun testTextInputTriggersCallback() {
        // Prepare an empty block
        val block = TextBlockUiState(content = "")
        
        // Set up the component with text change tracking
        composeTestRule.setContent {
            TextBlockContent(
                block = block,
                isExpanded = true, // Make it focused
                onTextChanged = textChangeTracker::recordTextChange,
                onFocused = {},
                readOnly = false
            )
        }
        
        // Input text
        composeTestRule.onNodeWithText("What's on your mind?").performTextInput("Hello, world!")
        
        // Verify callback was triggered with correct text
        assertEquals(1, textChangeTracker.changeCount)
        assertEquals("Hello, world!", textChangeTracker.lastTextValue)
    }
    
    @Test
    fun testReadOnlyBlockRejectsInput() {
        // Prepare a block with content
        val block = TextBlockUiState(content = "Read-only content")
        
        // Set up the component with read-only flag
        composeTestRule.setContent {
            TextBlockContent(
                block = block,
                isExpanded = true,
                onTextChanged = textChangeTracker::recordTextChange,
                onFocused = {},
                readOnly = true // Set as read-only
            )
        }
        
        // Attempt to input text
        composeTestRule.onNodeWithText("Read-only content").performTextInput(" additional")
        
        // Verify no callback was triggered
        assertEquals(0, textChangeTracker.changeCount)
        assertNull(textChangeTracker.lastTextValue)
    }
    
    @Test
    fun testEmptyBlockShowsPlaceholder() {
        // Prepare an empty block
        val block = TextBlockUiState(content = "")
        
        // Set up the component
        composeTestRule.setContent {
            TextBlockContent(
                block = block,
                isExpanded = false, // Not expanded to show placeholder
                onTextChanged = textChangeTracker::recordTextChange,
                onFocused = {},
                readOnly = false
            )
        }
        
        // Verify placeholder is shown
        composeTestRule.onNodeWithText("What's on your mind?").assertExists()
    }
    
    // Helper class to track text changes
    private class TextChangeTracker {
        var changeCount = 0
            private set
        
        var lastTextValue: String? = null
            private set
            
        fun recordTextChange(newText: String) {
            changeCount++
            lastTextValue = newText
        }
    }
}