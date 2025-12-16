# Compose State Management Best Practices

This document outlines the recommended patterns for managing state in our Jetpack Compose-based applications across all platforms (Android, iOS, Desktop).

## Core Principles

1. **Single Source of Truth**: Each piece of state should have one owner and a clear flow.
2. **Unidirectional Data Flow**: State flows down, events flow up.
3. **State Hoisting**: State should be kept at the lowest common ancestor of all components that need it.
4. **Immutable State**: State objects should be immutable to prevent unintended side effects.
5. **Separation of Concerns**: UI state should be separate from business logic.

## State Management Patterns

### 1. Non-ViewModel State Holders

For UI-only state that doesn't need persistence or business logic, use dedicated state holder classes instead of ViewModels. These are perfect for managing UI-specific state that shouldn't pollute your ViewModel.

#### Basic State Holders

```kotlin
// Simple UI-only state holder
class ExpandableListState(
    initialExpandedItems: Set<String> = emptySet()
) {
    // State
    private val _expandedItems = mutableStateOf(initialExpandedItems)
    val expandedItems: Set<String> get() = _expandedItems.value
    
    // Actions
    fun toggleExpanded(itemId: String) {
        _expandedItems.value = _expandedItems.value.toMutableSet().apply {
            if (contains(itemId)) remove(itemId) else add(itemId)
        }
    }
    
    fun collapseAll() {
        _expandedItems.value = emptySet()
    }
}

// Usage in Composable with remember
@Composable
fun ExpandableList(items: List<ListItem>) {
    // Create and remember the state holder
    val listState = remember { ExpandableListState() }
    
    // Use the state
    LazyColumn {
        items(items) { item ->
            ExpandableItem(
                item = item,
                isExpanded = listState.expandedItems.contains(item.id),
                onToggle = { listState.toggleExpanded(item.id) }
            )
        }
    }
    
    // Optional reset button
    Button(onClick = { listState.collapseAll() }) {
        Text("Collapse All")
    }
}
```

#### State Holder Types

There are several types of state holders that you can use depending on your needs:

1. **Stateless Holders** - Simply group callbacks and data into a single object for better organization:
   ```kotlin
   @Immutable
   data class SearchBarCallbacks(
       val onQueryChanged: (String) -> Unit,
       val onSearch: () -> Unit,
       val onClear: () -> Unit
   )

   @Composable
   fun SearchBar(
       query: String,
       callbacks: SearchBarCallbacks
   ) {
       // Use callbacks.onQueryChanged, etc.
   }
   ```

2. **Mutable State Holders** - Contain mutable state with appropriate encapsulation:
   ```kotlin
   class PanelState {
       private val _isExpanded = mutableStateOf(false)
       val isExpanded: Boolean get() = _isExpanded.value
       
       fun expand() { _isExpanded.value = true }
       fun collapse() { _isExpanded.value = false }
       fun toggle() { _isExpanded.value = !_isExpanded.value }
   }
   ```

3. **Bidirectional State Holders** - For when state can be updated from both UI and logic:
   ```kotlin
   class TextFieldState(
       initialText: String = "",
       private val onTextChanged: (String) -> Unit = {}
   ) {
       private val _text = mutableStateOf(initialText)
       val text: String get() = _text.value
       
       fun onTextChanged(newText: String) {
           _text.value = newText
           onTextChanged(newText)
       }
       
       // Called when external source updates text
       fun updateText(newText: String) {
           if (_text.value != newText) {
               _text.value = newText
           }
       }
   }
   ```

4. **Composite State Holders** - Combine multiple state holders for complex components:
   ```kotlin
   class FormState(
       val nameField: TextFieldState,
       val emailField: TextFieldState,
       val phoneField: TextFieldState
   ) {
       val isValid: Boolean
           get() = nameField.isValid && emailField.isValid && phoneField.isValid
       
       fun resetAll() {
           nameField.reset()
           emailField.reset()
           phoneField.reset()
       }
   }
   ```

#### State Holder Lifecycle

State holder objects have a lifecycle tied to the Composition:

1. **Creation**: Usually created and remembered with the `remember` composable function
2. **Updates**: Internal state is updated through exposed functions
3. **Recomposition**: When state changes, it triggers recomposition of dependent UI
4. **Disposal**: When the composition that created the state is removed, the state is disposed

State holder functions can also trigger side effects via LaunchedEffect:

```kotlin
@Composable
fun rememberAnimationState(): AnimationState {
    val state = remember { AnimationState() }
    
    // Handle animation effects
    LaunchedEffect(state.isAnimating) {
        if (state.isAnimating) {
            // Animation control code here
            while (state.isAnimating) {
                // Animation frame
                delay(16)
                state.advanceFrame()
            }
        }
    }
    
    return state
}
```

Benefits of UI-only state holders:
- Keeps UI logic separate from business logic
- Makes components more reusable
- Easier to test UI interactions
- Prevents ViewModels from becoming bloated with UI concerns
- Can be created and used at any level in the composition
- More precise control over state lifetime
- Can be composed and nested for complex UIs

When to use:
- For UI state that doesn't need persistence across configuration changes
- For state that's specific to a single component or screen
- For complex UI interactions that don't affect your data model
- When you want to make a component fully self-contained
- For libraries and reusable components

Examples of UI-only state:
- Expanded/collapsed states in lists
- Tab selections
- Dialog visibility
- Animation states
- Scroll positions
- Text field focus state
- Form validation state
- Pager states
- Drawer open/closed states

### 2. Specialized State Buses

For complex screens with multiple distinct features, use specialized state objects that act as "buses" between ViewModels and UI components.

```kotlin
// Example from the editor feature
@Immutable
data class EditorTextState(
    val blocks: List<EntryBlockData>,
    val expandedBlockId: Uuid?,
    val onTextChanged: (TextBlockData, String) -> Unit,
    val onBlockFocused: (Uuid) -> Unit
)

@Immutable
data class EditorAudioState(
    val onAudioRecordingStarted: () -> Unit,
    val onAudioRecordingStopped: () -> Unit,
    val onAudioRecordingSaved: (String) -> Unit,
    val onCreateAudioBlock: (String) -> Unit
)
```

**Benefits:**
- Each state object has a clear purpose and scope
- Components only receive the state they need
- Easier to understand and maintain
- Better type safety
- More testable

### 2. Composite State Holders

When a screen contains related features, use a composite state holder that groups specialized states:

```kotlin
@Stable
class EditorUiState(
    val textState: EditorTextState,
    val audioState: EditorAudioState,
    val journalState: EditorJournalState,
    val navigationState: EditorNavigationState,
    val rawEditorState: EditorState
) {
    // Convenience properties
    val hasContent get() = blocks.isNotEmpty()
    val blocks get() = textState.blocks
    // ...
}
```

### 3. Factory Functions with remember

Use factory functions to create and remember state objects:

```kotlin
@Composable
fun rememberEditorUiState(
    viewModel: EntryEditorViewModel,
    pagerState: PagerState,
    editorState: EditorState,
    onAudioRecordingStarted: () -> Unit,
    onAudioRecordingStopped: () -> Unit
): EditorUiState {
    val coroutineScope = rememberCoroutineScope()
    val hasContent = editorState.blocks.isNotEmpty()
    
    return remember(editorState, pagerState, hasContent) {
        // Create and combine state objects
        // ...
    }
}
```

### 4. State Wrappers for ViewModels

Use wrapper components to isolate ViewModels and prevent direct passing to child components:

```kotlin
@Composable
fun EditorAudioWrapper(
    audioState: EditorAudioState,
    modifier: Modifier = Modifier
) {
    // Inject ViewModel at this level
    val audioViewModel = koinViewModel<AudioRecordingViewModel>()
    
    // Connect state callbacks to ViewModel
    AudioEditorContent(
        onSaveRecording = { uri -> 
            audioState.onCreateAudioBlock(uri) 
        },
        // ...
    )
}
```

## Deciding Between ViewModels and State Holders

When designing your Compose UI, you'll need to decide when to use ViewModels versus UI-specific state holders:

### Use ViewModels When:

- State needs to survive configuration changes
- State involves business logic
- Data comes from repositories or other data sources
- State is shared between multiple screens
- Operations involve background work or coroutines
- You need to handle system events (like process death)

### Use State Holders When:

- State is purely UI-related (animations, expanded items, scroll position)
- State is scoped to a single component or composable
- Logic is simple and doesn't involve data sources
- You want to make a component fully self-contained and reusable
- You need fine-grained control over state lifetime in the composition

### Hybrid Approach

In most real applications, you'll use a combination:

```kotlin
@Composable
fun ProfileScreen(viewModel: ProfileViewModel = koinViewModel()) {
    // ViewModel provides the business data
    val profileData by viewModel.profileData.collectAsState()
    
    // UI state holder manages the UI-specific state
    val expandableState = rememberExpandableState()
    val animationState = rememberAnimationState()
    
    // Use both to render the UI
    ProfileContent(
        profile = profileData,
        expandableState = expandableState,
        animationState = animationState,
        onSaveProfile = viewModel::saveProfile
    )
}
```

This separation keeps your ViewModels focused on business logic while UI-specific concerns stay in the UI layer where they belong.

## Anti-Patterns to Avoid

### 1. Passing ViewModels to Child Components

❌ **Bad Practice**:
```kotlin
@Composable
fun EditorContent(viewModel: EditorViewModel) {
    // Using viewModel directly
}
```

✅ **Good Practice**:
```kotlin
@Composable
fun EditorContent(
    textState: EditorTextState,
    onTextChanged: (String) -> Unit
) {
    // Using callbacks from state
}
```

### 2. Deep Callback Nesting

❌ **Bad Practice**:
```kotlin
@Composable
fun Parent(
    onEventA: () -> Unit,
    onEventB: (Int) -> Unit,
    onEventC: (String, Boolean) -> Unit,
    // More callbacks...
) {
    Child(
        onEventA = onEventA,
        onEventB = onEventB,
        onEventC = onEventC,
        // Passing all callbacks down...
    )
}
```

✅ **Good Practice**:
```kotlin
@Composable
fun Parent(state: ParentState) {
    Child(state.childState)
}
```

### 3. Mutable State in Composables

❌ **Bad Practice**:
```kotlin
@Composable
fun Counter() {
    var count = mutableStateOf(0)
    // Direct mutation in event handlers
}
```

✅ **Good Practice**:
```kotlin
@Composable
fun Counter(
    count: Int,
    onIncrement: () -> Unit
) {
    // Using immutable state and callbacks
}
```

### 4. Complex State Transformations in Composables

❌ **Bad Practice**:
```kotlin
@Composable
fun UserList(users: List<User>) {
    // Complex filtering and mapping in the Composable
    val filteredUsers = users.filter { it.isActive }
        .map { UserUiModel(it) }
        .sortedBy { it.name }
}
```

✅ **Good Practice**:
```kotlin
// ViewModel or State Holder
val uiState = users.map { UserUiState(it) }

// Composable
@Composable
fun UserList(users: List<UserUiState>) {
    // Simply display the already-processed list
}
```

## Implementation Patterns

### 1. Basic State Holder

For simple components with minimal state:

```kotlin
// Basic state holder
class ToggleButtonState(
    initialIsToggled: Boolean = false
) {
    // State
    private val _isToggled = mutableStateOf(initialIsToggled)
    val isToggled: Boolean get() = _isToggled.value
    
    // Action
    fun toggle() {
        _isToggled.value = !_isToggled.value
    }
}

// Factory function
@Composable
fun rememberToggleButtonState(
    initialIsToggled: Boolean = false
): ToggleButtonState {
    return remember { ToggleButtonState(initialIsToggled) }
}

// Usage
@Composable
fun ToggleButton() {
    val state = rememberToggleButtonState()
    
    Button(
        onClick = { state.toggle() },
        colors = if (state.isToggled) activeColors else inactiveColors
    ) {
        Text(if (state.isToggled) "ON" else "OFF")
    }
}
```

### 2. State Holder with Multiple Properties

For components with several state properties:

```kotlin
class FormFieldState(
    initialValue: String = "",
    private val validator: (String) -> Boolean = { true }
) {
    // State properties
    private val _value = mutableStateOf(initialValue)
    val value: String get() = _value.value
    
    private val _isFocused = mutableStateOf(false)
    val isFocused: Boolean get() = _isFocused.value
    
    private val _isValid = mutableStateOf(validator(initialValue))
    val isValid: Boolean get() = _isValid.value
    
    private val _errorMessage = mutableStateOf<String?>(null)
    val errorMessage: String? get() = _errorMessage.value
    
    // Actions
    fun onValueChange(newValue: String) {
        _value.value = newValue
        _isValid.value = validator(newValue)
        _errorMessage.value = if (_isValid.value) null else "Invalid input"
    }
    
    fun onFocusChanged(isFocused: Boolean) {
        _isFocused.value = isFocused
    }
    
    fun reset() {
        _value.value = ""
        _errorMessage.value = null
        _isValid.value = true
    }
}
```

### 3. Stateful Component with Children

For components that manage state for their children:

```kotlin
// State
class TabRowState(
    initialSelectedIndex: Int = 0
) {
    private val _selectedIndex = mutableStateOf(initialSelectedIndex)
    val selectedIndex: Int get() = _selectedIndex.value
    
    fun selectTab(index: Int) {
        _selectedIndex.value = index
    }
}

// Component
@Composable
fun TabRow(
    tabs: List<String>,
    content: @Composable (Int) -> Unit
) {
    val tabState = remember { TabRowState() }
    
    Column {
        Row {
            tabs.forEachIndexed { index, title ->
                Tab(
                    title = title,
                    selected = index == tabState.selectedIndex,
                    onClick = { tabState.selectTab(index) }
                )
            }
        }
        
        // Show the content for the selected tab
        content(tabState.selectedIndex)
    }
}
```

### 4. State Derivation and Transformation

For components that need derived state:

```kotlin
class FilteredListState<T>(
    items: List<T> = emptyList(),
    private val filterPredicate: (T, String) -> Boolean
) {
    // Original items
    private val _items = mutableStateOf(items)
    
    // Filter query
    private val _filterQuery = mutableStateOf("")
    val filterQuery: String get() = _filterQuery.value
    
    // Derived state: filtered items
    val filteredItems: List<T> get() {
        val query = filterQuery
        return if (query.isBlank()) {
            _items.value
        } else {
            _items.value.filter { item ->
                filterPredicate(item, query)
            }
        }
    }
    
    // Actions
    fun updateItems(newItems: List<T>) {
        _items.value = newItems
    }
    
    fun updateFilter(query: String) {
        _filterQuery.value = query
    }
}
```

### 5. Non-ViewModel State Holders with Side Effects

For state holders that need to perform side effects or handle events:

```kotlin
class MediaPlayerState(
    private val mediaPlayer: MediaPlayer,
    initialUri: Uri? = null
) {
    // State
    private val _isPlaying = mutableStateOf(false)
    val isPlaying: Boolean get() = _isPlaying.value
    
    private val _currentPosition = mutableStateOf(0L)
    val currentPosition: Long get() = _currentPosition.value
    
    private val _duration = mutableStateOf(0L)
    val duration: Long get() = _duration.value
    
    private val _uri = mutableStateOf(initialUri)
    val uri: Uri? get() = _uri.value
    
    // Initialize player
    init {
        initialUri?.let { setMediaSource(it) }
    }
    
    // Actions
    fun setMediaSource(uri: Uri) {
        _uri.value = uri
        mediaPlayer.reset()
        mediaPlayer.setDataSource(uri.toString())
        mediaPlayer.prepare()
        _duration.value = mediaPlayer.duration.toLong()
        _currentPosition.value = 0
    }
    
    fun play() {
        uri?.let {
            mediaPlayer.start()
            _isPlaying.value = true
        }
    }
    
    fun pause() {
        if (isPlaying) {
            mediaPlayer.pause()
            _isPlaying.value = false
            _currentPosition.value = mediaPlayer.currentPosition.toLong()
        }
    }
    
    fun seekTo(position: Long) {
        mediaPlayer.seekTo(position.toInt())
        _currentPosition.value = position
    }
    
    fun release() {
        mediaPlayer.release()
    }
}

// Usage with LaunchedEffect for timers and cleanup
@Composable
fun MediaPlayer(uri: Uri) {
    val context = LocalContext.current
    val mediaPlayer = remember { MediaPlayer() }
    val playerState = remember(uri) { MediaPlayerState(mediaPlayer, uri) }
    
    // Position tracking effect
    LaunchedEffect(playerState.isPlaying) {
        while (playerState.isPlaying) {
            delay(100)
            playerState.updatePosition(mediaPlayer.currentPosition.toLong())
        }
    }
    
    // Cleanup effect
    DisposableEffect(Unit) {
        onDispose {
            playerState.release()
        }
    }
    
    // Player UI
    Column {
        Text("Now playing: ${uri.lastPathSegment}")
        Slider(
            value = playerState.currentPosition.toFloat(),
            onValueChange = { playerState.seekTo(it.toLong()) },
            valueRange = 0f..playerState.duration.toFloat()
        )
        Row {
            IconButton(onClick = { 
                if (playerState.isPlaying) playerState.pause() else playerState.play() 
            }) {
                Icon(
                    imageVector = if (playerState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (playerState.isPlaying) "Pause" else "Play"
                )
            }
            // Other controls...
        }
    }
}
```

### 6. Coordinated State Holders

For components that need to coordinate multiple state holders:

```kotlin
// Individual state holders
class PagerState(initialPage: Int = 0) {
    private val _currentPage = mutableStateOf(initialPage)
    val currentPage: Int get() = _currentPage.value
    
    fun setPage(page: Int) {
        _currentPage.value = page
    }
}

class TabState(initialSelectedTab: Int = 0) {
    private val _selectedTab = mutableStateOf(initialSelectedTab)
    val selectedTab: Int get() = _selectedTab.value
    
    fun selectTab(tab: Int) {
        _selectedTab.value = tab
    }
}

// Coordinator that connects them
class TabPagerCoordinator(
    private val tabState: TabState,
    private val pagerState: PagerState
) {
    // Tab changes update pager
    fun onTabSelected(index: Int) {
        tabState.selectTab(index)
        pagerState.setPage(index)
    }
    
    // Pager changes update tabs
    fun onPageChanged(page: Int) {
        pagerState.setPage(page)
        tabState.selectTab(page)
    }
}

// Usage in composable
@Composable
fun TabPagerScreen(pages: List<String>) {
    val tabState = remember { TabState() }
    val pagerState = remember { PagerState() }
    val coordinator = remember(tabState, pagerState) { 
        TabPagerCoordinator(tabState, pagerState) 
    }
    
    Column {
        // Tabs
        Row {
            pages.forEachIndexed { index, title ->
                Tab(
                    selected = index == tabState.selectedTab,
                    onClick = { coordinator.onTabSelected(index) },
                    text = { Text(title) }
                )
            }
        }
        
        // Pager
        HorizontalPager(
            state = pagerState,
            onPageChange = { coordinator.onPageChanged(it) }
        ) { page ->
            // Page content
            Text("Content for ${pages[page]}")
        }
    }
}
```

## State Design Guidelines

### 1. UiState Classes

For screens with multiple states, use sealed interfaces:

```kotlin
sealed interface TimelineUiState {
    object Loading : TimelineUiState
    data class Error(val message: String) : TimelineUiState
    data class Success(val items: List<TimelineItem>) : TimelineUiState
}
```

### 2. Event Handling

Use sealed classes for UI events:

```kotlin
sealed class EditorEvent {
    object Save : EditorEvent()
    object Cancel : EditorEvent()
    data class AddBlock(val type: BlockType) : EditorEvent()
    data class UpdateBlock(val id: UUID, val content: String) : EditorEvent()
}
```

### 3. State Immutability

Always use immutable data structures for state:

```kotlin
// Good
data class ProfileState(
    val name: String,
    val bio: String,
    val avatarUrl: String
)

// Avoid
class MutableProfileState {
    var name: String = ""
    var bio: String = ""
    var avatarUrl: String = ""
}
```

### 4. State Annotations

Use appropriate state annotations to optimize recomposition:

- `@Immutable`: For completely immutable state objects
- `@Stable`: For objects whose properties might change but identity remains the same

## Creating and Using State Holders

### 1. Creating Simple State Holders

For basic state management, create a simple class with state and functions to modify it:

```kotlin
class SearchBarState(
    initialQuery: String = ""
) {
    // State
    private val _query = mutableStateOf(initialQuery)
    val query: String get() = _query.value
    
    // Is the search active
    private val _isActive = mutableStateOf(false)
    val isActive: Boolean get() = _isActive.value
    
    // Actions
    fun onQueryChanged(newQuery: String) {
        _query.value = newQuery
    }
    
    fun activate() {
        _isActive.value = true
    }
    
    fun deactivate() {
        _isActive.value = false
    }
    
    fun clear() {
        _query.value = ""
    }
}
```

### 2. Providing Factory Functions

Create factory functions with the `@Composable` annotation to manage state creation:

```kotlin
@Composable
fun rememberSearchBarState(
    initialQuery: String = "",
    // Other parameters that influence initial state
): SearchBarState {
    return remember {
        SearchBarState(initialQuery)
    }
}
```

For state that depends on other values, include them as `remember` keys:

```kotlin
@Composable
fun rememberSearchBarState(
    initialQuery: String = "",
    searchHistory: List<String>
): SearchBarState {
    return remember(searchHistory) {
        SearchBarState(initialQuery, searchHistory)
    }
}
```

### 3. State Holder Usage Patterns

#### Basic Usage:

```kotlin
@Composable
fun SearchScreen() {
    val searchState = rememberSearchBarState()
    
    SearchBar(
        query = searchState.query,
        isActive = searchState.isActive,
        onQueryChange = searchState::onQueryChanged,
        onActivate = searchState::activate,
        onDeactivate = searchState::deactivate,
        onClear = searchState::clear
    )
    
    SearchResults(query = searchState.query)
}
```

#### Passing to Multiple Components:

```kotlin
@Composable
fun SearchScreen() {
    val searchState = rememberSearchBarState()
    
    Column {
        SearchBar(searchState)
        SearchFilters(searchState)
        SearchResults(searchState)
    }
}

@Composable
fun SearchBar(state: SearchBarState) {
    // Use state properties and callbacks
}
```

#### Composing Multiple State Holders:

```kotlin
@Composable
fun AdvancedSearchScreen() {
    val queryState = rememberSearchBarState()
    val filterState = rememberFilterState()
    val resultsState = rememberResultsState(
        query = queryState.query,
        filters = filterState.activeFilters
    )
    
    Column {
        SearchBar(queryState)
        FilterBar(filterState)
        SearchResults(resultsState)
    }
}
```

### 4. State Holder with Saved State

For state that survives configuration changes, use rememberSaveable:

```kotlin
@Composable
fun rememberSaveableSearchBarState(
    initialQuery: String = ""
): SearchBarState {
    val savedQuery = rememberSaveable { mutableStateOf(initialQuery) }
    val savedIsActive = rememberSaveable { mutableStateOf(false) }
    
    return remember {
        SearchBarState(
            initialQuery = savedQuery.value,
            initialIsActive = savedIsActive.value,
            onQueryChanged = { savedQuery.value = it },
            onActiveChanged = { savedIsActive.value = it }
        )
    }
}
```

## Testing State

### 1. Unit Testing State Holders

```kotlin
@Test
fun `test editor state creation with text block`() {
    val textState = EditorTextState(
        blocks = listOf(TextBlockData("Sample text")),
        expandedBlockId = null,
        onTextChanged = { _, _ -> },
        onBlockFocused = { }
    )
    
    assertEquals(1, textState.blocks.size)
    assertTrue(textState.blocks[0] is TextBlockData)
}
```

### 2. Testing Components with Test States

```kotlin
@Test
fun `editor shows text block when provided`() {
    composeTestRule.setContent {
        val testState = EditorTextState(
            blocks = listOf(TextBlockData("Test content")),
            expandedBlockId = null,
            onTextChanged = { _, _ -> },
            onBlockFocused = { }
        )
        RenderTextEditorContent(testState)
    }
    
    composeTestRule.onNodeWithText("Test content").assertExists()
}
```

## Performance Considerations

### 1. Minimize Recomposition Scope

- Use multiple smaller state objects instead of one large one
- Only update the specific state that changes

### 2. Proper remember() Usage

Always specify the keys that should trigger recreation:

```kotlin
val derivedState = remember(key1, key2) {
    // Compute something based on key1 and key2
}
```

### 3. State Holder Creation

Create state holders at the appropriate level:

```kotlin
// Good - create at the screen level
@Composable
fun EditorScreen() {
    val viewModel = koinViewModel<EditorViewModel>()
    val state = rememberEditorUiState(...)
    
    EditorContent(state)
}

// Bad - create too deep in the tree
@Composable
fun EditorContent() {
    val viewModel = koinViewModel<EditorViewModel>() // Wrong level!
}
```

## Examples

### Simple Screen

```kotlin
// ViewModel with UiState
class ProfileViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState = _uiState.asStateFlow()
    
    fun updateName(name: String) {
        _uiState.update { it.copy(name = name) }
    }
}

// Screen
@Composable
fun ProfileScreen(viewModel: ProfileViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsState()
    
    ProfileContent(
        name = state.name,
        onNameChanged = viewModel::updateName
    )
}

// Content
@Composable
fun ProfileContent(
    name: String,
    onNameChanged: (String) -> Unit
) {
    // UI implementation
}
```

### Complex Screen

For screens with multiple distinct features, use the specialized state bus pattern as demonstrated in the Editor feature:

```kotlin
// Top level screen collects all state
@Composable
fun ComplexScreen(viewModel: ComplexViewModel = koinViewModel()) {
    val uiState = rememberComplexUiState(viewModel, ...)
    
    // Pass specialized states to respective components
    FeatureA(uiState.featureAState)
    FeatureB(uiState.featureBState)
}
```

## Advanced State Holder Techniques

### 1. Composition-Local State Holders

For state that needs to be accessible deeply in the composition tree without prop-drilling:

```kotlin
// Create a composition local
private val LocalThemeState = compositionLocalOf<ThemeState> { 
    error("ThemeState not provided") 
}

// State holder
class ThemeState(
    initialIsDark: Boolean = false
) {
    private val _isDark = mutableStateOf(initialIsDark)
    val isDark: Boolean get() = _isDark.value
    
    fun toggleTheme() {
        _isDark.value = !_isDark.value
    }
}

// Provider composable
@Composable
fun ThemeStateProvider(
    initialIsDark: Boolean = false,
    content: @Composable () -> Unit
) {
    val themeState = remember { ThemeState(initialIsDark) }
    
    CompositionLocalProvider(
        LocalThemeState provides themeState
    ) {
        content()
    }
}

// Usage anywhere in the tree
@Composable
fun DeepComponent() {
    val themeState = LocalThemeState.current
    
    Surface(
        color = if (themeState.isDark) DarkThemeColor else LightThemeColor
    ) {
        // Content
    }
}
```

### 2. StateFlow Integration

When you need to convert StateFlow to Compose state:

```kotlin
class PaginatedListState<T>(
    private val paginator: Paginator<T>
) {
    // Expose StateFlow from the paginator
    val items: StateFlow<List<T>> = paginator.items
    
    // Actions
    fun loadNextPage() {
        paginator.loadNextPage()
    }
    
    fun refresh() {
        paginator.refresh()
    }
}

// Usage in Composable
@Composable
fun PaginatedList(state: PaginatedListState<Item>) {
    // Convert StateFlow to Compose State
    val items by state.items.collectAsState()
    
    LazyColumn {
        items(items) { item ->
            ItemRow(item)
        }
        
        // Load more when reaching the end
        item {
            if (items.isNotEmpty()) {
                LoadMoreButton(onClick = state::loadNextPage)
            }
        }
    }
}
```

### 3. State Holder Composition

Compose multiple state holders for complex UIs:

```kotlin
@Composable
fun ComplexForm() {
    // Create individual field states
    val nameState = rememberFormFieldState(validator = { it.length > 2 })
    val emailState = rememberFormFieldState(validator = { it.contains("@") })
    val phoneState = rememberFormFieldState(validator = { it.matches(phoneRegex) })
    
    // Compose them into a form state
    val formState = remember(nameState, emailState, phoneState) {
        FormState(
            fields = listOf(nameState, emailState, phoneState),
            onSubmit = { 
                // Handle form submission 
            }
        )
    }
    
    Column {
        FormField(
            state = nameState,
            label = "Name"
        )
        
        FormField(
            state = emailState,
            label = "Email"
        )
        
        FormField(
            state = phoneState,
            label = "Phone"
        )
        
        Button(
            onClick = formState::submit,
            enabled = formState.isValid
        ) {
            Text("Submit")
        }
    }
}
```

### 4. State Holders with CoroutineScope

For state holders that need to launch coroutines:

```kotlin
class AsyncLoadingState<T>(
    private val coroutineScope: CoroutineScope,
    private val loadData: suspend () -> Result<T>
) {
    // State
    private val _status = mutableStateOf<LoadingStatus<T>>(LoadingStatus.Initial)
    val status: LoadingStatus<T> get() = _status.value

    // Properties
    val isLoading: Boolean get() = status is LoadingStatus.Loading
    val data: T? get() = (status as? LoadingStatus.Success)?.data
    val error: Throwable? get() = (status as? LoadingStatus.Error)?.error
    
    // Action
    fun load() {
        if (isLoading) return
        
        _status.value = LoadingStatus.Loading
        
        coroutineScope.launch {
            loadData()
                .onSuccess { _status.value = LoadingStatus.Success(it) }
                .onFailure { _status.value = LoadingStatus.Error(it) }
        }
    }
    
    fun retry() = load()
    
    fun reset() {
        _status.value = LoadingStatus.Initial
    }
    
    // Status sealed class
    sealed class LoadingStatus<out T> {
        object Initial : LoadingStatus<Nothing>()
        object Loading : LoadingStatus<Nothing>()
        data class Success<T>(val data: T) : LoadingStatus<T>()
        data class Error(val error: Throwable) : LoadingStatus<Nothing>()
    }
}

// Factory function that provides CoroutineScope
@Composable
fun <T> rememberAsyncLoadingState(
    loadData: suspend () -> Result<T>
): AsyncLoadingState<T> {
    val coroutineScope = rememberCoroutineScope()
    
    return remember(loadData) {
        AsyncLoadingState(coroutineScope, loadData)
    }
}

// Usage
@Composable
fun UserProfile(userId: String) {
    val loadingState = rememberAsyncLoadingState<UserProfile> {
        userRepository.getUser(userId).map { it.toUserProfile() }
    }
    
    // Load data on first composition
    LaunchedEffect(userId) {
        loadingState.load()
    }
    
    // Show UI based on loading state
    when (val status = loadingState.status) {
        is LoadingStatus.Initial, 
        is LoadingStatus.Loading -> LoadingIndicator()
        is LoadingStatus.Error -> ErrorView(
            error = status.error,
            onRetry = loadingState::retry
        )
        is LoadingStatus.Success -> ProfileContent(
            profile = status.data,
            onRefresh = loadingState::load
        )
    }
}
```

### 5. Reactive State Holders with Flow

For state holders that need to react to Flow-based data sources:

```kotlin
class ReactiveListState<T>(
    private val coroutineScope: CoroutineScope,
    private val dataFlow: Flow<List<T>>,
    initialSortOrder: SortOrder = SortOrder.ASCENDING,
    private val sortFunction: (List<T>, SortOrder) -> List<T>
) {
    // Sort order state
    private val _sortOrder = mutableStateOf(initialSortOrder)
    val sortOrder: SortOrder get() = _sortOrder.value
    
    // Loading state
    private val _isLoading = mutableStateOf(true)
    val isLoading: Boolean get() = _isLoading.value
    
    // Items state (will be updated by the flow)
    private val _items = mutableStateOf<List<T>>(emptyList())
    
    // Derived sorted items
    val items: List<T> get() = sortFunction(_items.value, sortOrder)
    
    init {
        // Collect the flow and update the items
        coroutineScope.launch {
            dataFlow
                .onStart { _isLoading.value = true }
                .onCompletion { _isLoading.value = false }
                .collect { newItems ->
                    _items.value = newItems
                }
        }
    }
    
    // Action to change sort order
    fun toggleSortOrder() {
        _sortOrder.value = when (sortOrder) {
            SortOrder.ASCENDING -> SortOrder.DESCENDING
            SortOrder.DESCENDING -> SortOrder.ASCENDING
        }
    }
    
    enum class SortOrder { ASCENDING, DESCENDING }
}

// Factory function
@Composable
fun <T> rememberReactiveListState(
    dataFlow: Flow<List<T>>,
    initialSortOrder: SortOrder = SortOrder.ASCENDING,
    sortFunction: (List<T>, SortOrder) -> List<T>
): ReactiveListState<T> {
    val coroutineScope = rememberCoroutineScope()
    
    return remember(dataFlow, sortFunction) {
        ReactiveListState(
            coroutineScope = coroutineScope,
            dataFlow = dataFlow,
            initialSortOrder = initialSortOrder,
            sortFunction = sortFunction
        )
    }
}
```

## State Restoration and Persistence

### 1. Using rememberSaveable for Basic State Persistence

For state that needs to survive configuration changes (like screen rotations on Android):

```kotlin
@Composable
fun PersistentCounter() {
    // This state will survive configuration changes
    var count by rememberSaveable { mutableStateOf(0) }
    
    Button(onClick = { count++ }) {
        Text("Count: $count")
    }
}
```

### 2. Custom Saver for Complex State

For state holders that need to be saved and restored:

```kotlin
// Define a custom saver for your state holder
val formStateSaver = Saver<FormState, Map<String, String>>(
    save = { formState ->
        mapOf(
            "name" to formState.nameField.value,
            "email" to formState.emailField.value,
            "phone" to formState.phoneField.value
        )
    },
    restore = { savedMap ->
        FormState(
            nameField = TextFieldState(savedMap["name"] ?: ""),
            emailField = TextFieldState(savedMap["email"] ?: ""),
            phoneField = TextFieldState(savedMap["phone"] ?: "")
        )
    }
)

// Use it in a composable
@Composable
fun PersistentForm() {
    val formState = rememberSaveable(saver = formStateSaver) {
        FormState(
            nameField = TextFieldState(),
            emailField = TextFieldState(),
            phoneField = TextFieldState()
        )
    }
    
    // Form UI using formState...
}
```

### 3. Making State Holders Parcelable (Android-specific)

For Android, you can make state holders `Parcelable` for automatic saving:

```kotlin
@Parcelize
data class SearchState(
    val query: String = "",
    val isActive: Boolean = false
) : Parcelable

@Composable
fun SearchScreen() {
    var searchState by rememberSaveable { 
        mutableStateOf(SearchState()) 
    }
    
    // UI using searchState...
}
```

### 4. SavedStateHandle Integration with ViewModels

For ViewModels, use SavedStateHandle to persist UI state:

```kotlin
class SearchViewModel(
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    // Saved state for search query
    private val _searchQuery = savedStateHandle.getStateFlow("searchQuery", "")
    val searchQuery = _searchQuery.asStateFlow()
    
    // Update and save state
    fun updateSearchQuery(query: String) {
        savedStateHandle["searchQuery"] = query
    }
}
```

## Multi-platform Considerations

When building for multiple platforms (Android, iOS, Desktop), consider these state management approaches:

### 1. Platform-Specific State Holders

Create expect/actual implementations for platform-specific capabilities:

```kotlin
// Common code
expect class LocationState {
    val currentLocation: StateFlow<Location?>
    fun startLocationUpdates()
    fun stopLocationUpdates()
}

// Android implementation
actual class LocationState(
    private val locationClient: FusedLocationProviderClient,
    private val coroutineScope: CoroutineScope
) {
    private val _currentLocation = MutableStateFlow<Location?>(null)
    actual val currentLocation = _currentLocation.asStateFlow()
    
    actual fun startLocationUpdates() {
        // Android-specific implementation
    }
    
    actual fun stopLocationUpdates() {
        // Android-specific implementation
    }
}

// iOS implementation
actual class LocationState(
    private val locationManager: CLLocationManager,
    private val coroutineScope: CoroutineScope
) {
    private val _currentLocation = MutableStateFlow<Location?>(null)
    actual val currentLocation = _currentLocation.asStateFlow()
    
    actual fun startLocationUpdates() {
        // iOS-specific implementation
    }
    
    actual fun stopLocationUpdates() {
        // iOS-specific implementation
    }
}
```

### 2. Platform-Aware Factory Functions

Use factory functions that adapt to the platform:

```kotlin
@Composable
expect fun rememberFilePickerState(): FilePickerState

// Android implementation
@Composable
actual fun rememberFilePickerState(): FilePickerState {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    return remember {
        AndroidFilePickerState(context, coroutineScope)
    }
}

// Desktop implementation
@Composable
actual fun rememberFilePickerState(): FilePickerState {
    val coroutineScope = rememberCoroutineScope()
    
    return remember {
        DesktopFilePickerState(coroutineScope)
    }
}
```

## Performance Optimizations

### 1. State Derivation with Memoization

For expensive derived state, use `derivedStateOf`:

```kotlin
class FilterableListState<T>(
    items: List<T>,
    private val filterPredicate: (T, String) -> Boolean
) {
    // Original items
    private val _items = mutableStateOf(items)
    
    // Filter query
    private val _filterQuery = mutableStateOf("")
    val filterQuery: String get() = _filterQuery.value
    
    // Efficiently derived state
    private val _filteredItems = derivedStateOf {
        val query = filterQuery
        if (query.isBlank()) {
            _items.value
        } else {
            _items.value.filter { item ->
                filterPredicate(item, query)
            }
        }
    }
    val filteredItems: List<T> get() = _filteredItems.value
    
    fun updateQuery(query: String) {
        _filterQuery.value = query
    }
}
```

### 2. Avoiding Unnecessary State Holders

For very simple cases, use simpler patterns:

```kotlin
// Instead of this
class SimpleToggleState {
    private val _isToggled = mutableStateOf(false)
    val isToggled: Boolean get() = _isToggled.value
    
    fun toggle() {
        _isToggled.value = !_isToggled.value
    }
}

// Consider this
@Composable
fun SimpleToggle() {
    var isToggled by remember { mutableStateOf(false) }
    
    Switch(
        checked = isToggled,
        onCheckedChange = { isToggled = it }
    )
}
```

## Conclusion

Following these state management best practices will result in more maintainable, testable, and performant Compose applications. By selecting the appropriate state management pattern for each situation, you can keep your codebase clean and your UI components reusable.

Key takeaways:
- Use specialized state holders for UI-specific state
- Keep ViewModels focused on business logic
- Separate state by feature or concern
- Compose state holders for complex UIs
- Avoid passing ViewModels down the component tree
- Use factory functions with remember for efficient state creation
- Consider platform-specific requirements when building multi-platform apps
- Optimize performance with appropriate state derivation techniques

By applying these patterns consistently across the codebase, we ensure that all developers can quickly understand and contribute to any part of the application.

## Additional Resources

- [Compose State Official Documentation](https://developer.android.com/jetpack/compose/state)
- [Managing State in Jetpack Compose](https://developer.android.com/jetpack/compose/state-hoisting)
- [State Hoisting in Compose](https://developer.android.com/jetpack/compose/state-hoisting)
- [Understanding Compose Recomposition](https://developer.android.com/jetpack/compose/lifecycle)
- [Side Effects in Compose](https://developer.android.com/jetpack/compose/side-effects)