# UI Testing Guide

UI tests verify Compose components render correctly and respond to user interactions. These tests use Compose's testing framework for deterministic, fast testing.

## Scope

UI tests cover:
- Component rendering with different states
- User interactions (clicks, text input, scrolling)
- State updates in response to user actions
- Navigation between screens
- Accessibility features

## Test Annotations

Use `@Composable` in test files with proper preview annotations:

```kotlin
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class MyComponentTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun displayText_showsCorrectContent() {
        // Arrange
        composeTestRule.setContent {
            Text(text = "Hello Test")
        }

        // Act & Assert
        composeTestRule.onNodeWithText("Hello Test").assertExists()
    }
}
```

## Finding Components

### By Text

```kotlin
composeTestRule.onNodeWithText("Submit").performClick()
composeTestRule.onNodeWithText("Username").assertIsDisplayed()
```

### By Test Tag

Tag components in code:
```kotlin
Button(
    onClick = { },
    modifier = Modifier.testTag("submit_button")
) {
    Text("Submit")
}
```

Find in tests:
```kotlin
composeTestRule.onNodeWithTag("submit_button").performClick()
```

### By Content Description

```kotlin
Icon(
    painter = painterResource(R.drawable.ic_close),
    contentDescription = "Close dialog",
    modifier = Modifier.testTag("close_button")
)
```

Find:
```kotlin
composeTestRule.onNodeWithContentDescription("Close dialog").performClick()
```

## User Interactions

### Click Events

```kotlin
@Test
fun whenClickingButton_thenCallbackFires() {
    var clicked = false

    composeTestRule.setContent {
        Button(onClick = { clicked = true }) {
            Text("Click me")
        }
    }

    composeTestRule.onNodeWithText("Click me").performClick()
    assert(clicked)
}
```

### Text Input

```kotlin
@Test
fun whenEnteringText_thenUpdatesState() {
    composeTestRule.setContent {
        var text by remember { mutableStateOf("") }
        TextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.testTag("input")
        )
    }

    composeTestRule.onNodeWithTag("input").performTextInput("Hello")
    composeTestRule.onNodeWithText("Hello").assertExists()
}
```

### Scrolling

```kotlin
@Test
fun whenScrolling_thenShowsMoreContent() {
    composeTestRule.setContent {
        LazyColumn {
            items(100) { index ->
                Text("Item $index")
            }
        }
    }

    // Scroll to bottom
    composeTestRule.onNodeWithText("Item 99").performScrollToNode()
    composeTestRule.onNodeWithText("Item 99").assertIsDisplayed()
}
```

## Testing State

### Mutable State

```kotlin
@Test
fun whenToggleChanged_thenStateUpdates() {
    composeTestRule.setContent {
        var isEnabled by remember { mutableStateOf(false) }

        Column {
            Switch(
                checked = isEnabled,
                onCheckedChange = { isEnabled = it },
                modifier = Modifier.testTag("toggle")
            )
            if (isEnabled) {
                Text("Enabled", modifier = Modifier.testTag("status"))
            }
        }
    }

    // Initially disabled
    composeTestRule.onNodeWithTag("status").assertDoesNotExist()

    // Toggle
    composeTestRule.onNodeWithTag("toggle").performClick()

    // Now enabled
    composeTestRule.onNodeWithTag("status").assertExists()
}
```

### ViewModel Integration

```kotlin
@Test
fun whenLoadingData_thenShowsLoadingThenContent() {
    val viewModel = TestTimelineViewModel()

    composeTestRule.setContent {
        TimelineScreen(viewModel = viewModel)
    }

    // Initially loading
    composeTestRule.onNodeWithTag("loading_spinner").assertIsDisplayed()

    // Complete loading
    viewModel.completeLoading()

    // Show content
    composeTestRule.onNodeWithText("Entry 1").assertIsDisplayed()
}
```

## Testing Screens

### Full Screen Test

```kotlin
@RunWith(AndroidJUnit4::class)
class EditorScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val viewModel = FakeEditorViewModel()

    @Test
    fun whenCreatingEntry_thenCanSaveAndNavigate() {
        // Setup
        val onNavigateBack = mockk<() -> Unit>()

        composeTestRule.setContent {
            EditorScreen(
                viewModel = viewModel,
                onNavigateBack = onNavigateBack
            )
        }

        // Enter text
        composeTestRule.onNodeWithTag("editor_input").performTextInput("My entry")

        // Click save
        composeTestRule.onNodeWithText("Save").performClick()

        // Verify save was called
        assertTrue(viewModel.lastSavedEntry?.content == "My entry")

        // Verify navigation
        verify { onNavigateBack() }
    }

    @Test
    fun whenEditingExistingEntry_thenLoadsCurrentContent() {
        val existingEntry = Entry(id = "1", content = "Original content")
        viewModel.setEntry(existingEntry)

        composeTestRule.setContent {
            EditorScreen(viewModel = viewModel, onNavigateBack = {})
        }

        composeTestRule.onNodeWithText("Original content").assertIsDisplayed()
    }
}
```

## Testing Lists

```kotlin
@Test
fun whenDisplayingJournals_thenShowsAllItems() {
    composeTestRule.setContent {
        JournalList(
            journals = listOf(
                Journal(id = "1", name = "Work"),
                Journal(id = "2", name = "Personal"),
                Journal(id = "3", name = "Travel")
            ),
            onJournalSelected = {}
        )
    }

    composeTestRule.onNodeWithText("Work").assertIsDisplayed()
    composeTestRule.onNodeWithText("Personal").assertIsDisplayed()
    composeTestRule.onNodeWithText("Travel").assertIsDisplayed()
}

@Test
fun whenSelectingJournal_thenCallsCallback() {
    var selectedId: String? = null

    composeTestRule.setContent {
        JournalList(
            journals = listOf(Journal(id = "1", name = "Work")),
            onJournalSelected = { selectedId = it.id }
        )
    }

    composeTestRule.onNodeWithText("Work").performClick()
    assertEquals("1", selectedId)
}
```

## Testing Dialogs

```kotlin
@Test
fun whenShowingConfirmDialog_thenCallsCorrectCallback() {
    var confirmed = false

    composeTestRule.setContent {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Confirm Delete") },
            text = { Text("Are you sure?") },
            confirmButton = {
                Button(onClick = { confirmed = true }) {
                    Text("Yes")
                }
            },
            dismissButton = {
                Button(onClick = {}) {
                    Text("No")
                }
            }
        )
    }

    composeTestRule.onNodeWithText("Yes").performClick()
    assert(confirmed)
}
```

## Testing Navigation

```kotlin
@Test
fun whenNavigatingToDetail_thenDisplaysCorrectContent() {
    composeTestRule.setContent {
        Navigation(startDestination = Route.Home)
    }

    // On home screen
    composeTestRule.onNodeWithText("Home").assertIsDisplayed()

    // Navigate to detail
    composeTestRule.onNodeWithTag("journal_item_1").performClick()

    // Show detail screen
    composeTestRule.onNodeWithTag("detail_screen").assertIsDisplayed()
}
```

## Testing Theming

```kotlin
@Test
fun whenInDarkMode_thenAppliesDarkTheme() {
    composeTestRule.setContent {
        LogDateTheme(darkTheme = true) {
            Surface(color = MaterialTheme.colorScheme.background) {
                Text("Content")
            }
        }
    }

    // Verify dark colors applied
    // (May require checking surface color or element styling)
}
```

## Common Assertions

```kotlin
// Existence
composeTestRule.onNodeWithText("Text").assertExists()
composeTestRule.onNodeWithText("Text").assertDoesNotExist()

// Visibility
composeTestRule.onNodeWithText("Text").assertIsDisplayed()
composeTestRule.onNodeWithText("Text").assertIsNotDisplayed()

// Content
composeTestRule.onNodeWithTag("input").assert(hasText("Expected text"))
composeTestRule.onNodeWithTag("button").assert(isEnabled())

// Enabled state
composeTestRule.onNodeWithTag("button").assertIsEnabled()
composeTestRule.onNodeWithTag("button").assertIsNotEnabled()

// Focused state
composeTestRule.onNodeWithTag("input").assertIsFocused()
composeTestRule.onNodeWithTag("input").assertIsNotFocused()
```

## Testing Modifiers and Styling

```kotlin
@Test
fun buttonHasCorrectSize() {
    composeTestRule.setContent {
        Button(
            onClick = {},
            modifier = Modifier
                .size(width = 100.dp, height = 50.dp)
                .testTag("button")
        ) {
            Text("Test")
        }
    }

    composeTestRule.onNodeWithTag("button")
        .assertWidthIsEqualTo(100.dp)
        .assertHeightIsEqualTo(50.dp)
}
```

## Screenshot Differences

### Unit/Integration UI Tests
- For behavioral testing
- Fast execution
- Good for state verification
- Can't capture visual appearance

### Screenshot Tests (Separate)
- For visual regression
- Compare rendered images
- Slower execution
- Detects visual bugs

Use both:
- UI tests for behavior
- Screenshot tests for appearance

## Running UI Tests

```bash
# Run all UI tests
./gradlew connectedAndroidTest

# Run specific test class
./gradlew connectedAndroidTest --tests "EditorScreenTest"

# Run on specific device
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.EditorScreenTest
```

## Best Practices

1. **Use Semantic Matchers**: More maintainable than coordinates
   ```kotlin
   // Good
   composeTestRule.onNodeWithText("Submit").performClick()

   // Avoid
   composeTestRule.onNodeWithTag("button_at_0_100").performClick()
   ```

2. **Test User Journeys**: Not individual implementation details
   ```kotlin
   // Good - user journey
   @Test
   fun whenEnteringText_thenCanSave() { }

   // Avoid - implementation detail
   @Test
   fun stateUpdatesInternally() { }
   ```

3. **Use Test Tags Strategically**: For hard-to-find components
   ```kotlin
   // Icon buttons benefit from test tag
   IconButton(
       onClick = {},
       modifier = Modifier.testTag("close_button")
   )

   // Text is usually fine without tag
   Text("Title")
   ```

4. **Separate UI and Logic Tests**: Different scopes
   ```kotlin
   // UI test - verify rendering and interaction
   @Test
   fun buttonClicksWork()

   // Unit test - verify business logic
   @Test
   fun dataProcessing()
   ```

5. **Mock ViewModel Callbacks**: Isolate component testing
   ```kotlin
   val onSave = mockk<(Entry) -> Unit>()
   EditorScreen(onSave = onSave)
   ```

## Resources

- [Compose Testing](https://developer.android.com/jetpack/compose/testing)
- [Compose Test Cheat Sheet](https://developer.android.com/jetpack/compose/testing-cheatsheet)
- [Testing Best Practices](https://developer.android.com/training/testing/best-practices)
