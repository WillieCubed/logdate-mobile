# Defining View Models

View Models 

# Handling Business Logic

`ViewModel` methods are expected to be called directly from the main (UI)
thread. As such, you must be cognizant of how you process logic.

## Pattern: Observing State


## Pattern: Handling Asynchronous Logic

Do not expose suspend functions directly to Compose UI logic as they will not have
any means of consuming them. Instead, use the `ViewModel.viewModelScope` coroutine
scope exposed via extension property to launch operations:

```kotlin
class TimelineViewModel(
    private val getNewestActivities: GetRecentActivitiesUseCase,
) : ViewModel() {

    private val timelineItems = MutableStateFlow<List<TimelineActivity>>(listOf())

    fun refresh() {
        viewModelScope.launch {
            val newItems = getNewestActivities()
            timelineItems.value = newItems
        }
    }
}
```

## Pattern: Composing use cases

Use `Flow.combine` to combine data from flows and other sources into UI states.

## Mapping to UI State

Generally, for any non-trivial screen, it's expected that you'll need some logic
to map between a domain-level `UseCase` and a UI view model.


# Defining UI States

For convenience your UI state holder should have default parameters for all of
its values in its constructor. These default values should be reasonable
defaults.

Use Kotlin's sealed classes to handle mutually exclusive states, but remember
that

## Handling Errors

You should use a 

# Consuming View Models

Generally, only screen-level composables should directly be consuming
`ViewModel` objects. They should never instantiate them directly except during
testing. Instead, you should use dependency injection with Koin. Koin provides a
`koinViewModel` function to do this:

```kotlin
@Composable
fun TimelineScreen(
    viewModel: TimelineViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    TimelinePane(
        uiState = uiState,
    )
}
```

You should separate the actual content rendering from screen-level UI logic used
to control that functionality. This is useful when you need to update `ViewModel`
state that directly affects UI state. In this example, we use a `rewindId`
supplied by a navigation route to update the currently rendered
`RewindExperienceContent`:

```kotlin
@Composable
fun RewindDetailScreen(
    onClose: () -> Unit,
    rewindId: Uuid,
    viewModel: RewindExperienceViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    LaunchedEffect(rewindId) {
        viewModel.setRewind(rewindId)
    }
    
    RewindExperienceContent(
        uiState = uiState,
        onClose = onClose,
    )
}
```

This way, you can separate stateful logic from rendering screens from 

For multiple screens that are within the same scope and depend on some shared
state (e.g. during an onboarding flow), you should share a `ViewModel`. It is
an antipattern to create a viewmodel for multiple screens that are linked together
unless a screen has so much functionality or complexity that it would increase
clarity to create a viewmodel scoped to one specific screen.

## Exposing View Model State for 