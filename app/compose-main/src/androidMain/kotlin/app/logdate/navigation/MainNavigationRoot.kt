package app.logdate.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entry
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSavedStateNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.navigation3.ui.Scene
import androidx.navigation3.ui.SceneStrategy
import androidx.navigation3.ui.rememberSceneSetupNavEntryDecorator
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_MEDIUM_LOWER_BOUND
import app.logdate.feature.core.main.HomeViewModel
import app.logdate.feature.journals.ui.JournalsOverviewScreen
import app.logdate.feature.onboarding.ui.OnboardingStartScreen
import app.logdate.feature.timeline.ui.TimelineViewModel
import app.logdate.feature.timeline.ui.details.TimelineDayDetailPanel
import app.logdate.ui.profiles.toUiState
import app.logdate.ui.timeline.HomeTimelineUiState
import app.logdate.ui.timeline.TextNoteUiState
import app.logdate.ui.timeline.TimelineDayUiState
import app.logdate.ui.timeline.TimelinePane
import app.logdate.ui.timeline.TimelineUiState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.Serializable
import org.koin.compose.viewmodel.koinViewModel
import kotlin.uuid.Uuid

@Serializable
data object OnboardingStart : NavKey

@Serializable
data object OnboardingSignIn : NavKey
data object TimelineList : NavKey
data object TimelineDetail : NavKey
data object JournalList : NavKey
data class NewJournal(val id: Uuid? = null) : NavKey
data class JournalDetail(val id: Uuid) : NavKey
data object EntryEditor : NavKey
data object RewindList : NavKey
data object RewindDetail : NavKey

@Serializable
data class AppSettings(val id: Uuid? = null) : NavKey

@Composable
fun App() {
    val backStack = rememberNavBackStack(OnboardingStart)

    NavDisplay(
        entryDecorators = listOf(
            // Add the default decorators for managing scenes and saving state
            rememberSceneSetupNavEntryDecorator(),
            rememberSavedStateNavEntryDecorator(),
            // Then add the view model store decorator
            rememberViewModelStoreNavEntryDecorator()
        ),
        backStack = backStack,
        onBack = { keysToRemove -> repeat(keysToRemove) { backStack.removeLastOrNull() } },
        entryProvider = entryProvider {
            entry<OnboardingStart> { _ ->
                OnboardingStartScreen(
                    onNext = { backStack.add(TimelineList) },
                    onStartFromBackup = { backStack.add(TimelineList) } // Placeholder for backup logic
                )
            }
            entry<OnboardingSignIn> { _ ->
                // Placeholder for sign-in screen
                // You can implement the actual sign-in logic here
            }
            entry<JournalList> { _ ->
                JournalsOverviewScreen(
                    onOpenJournal = { journalId ->
                        backStack.add(JournalDetail(journalId))
                    },
                    onBrowseJournals = { /* TODO: Handle browse navigation */ },
                    onCreateJournal = { backStack.add(NewJournal()) }
                )
            }
            entry<TimelineList>(
                // Indicate this entry can be displayed in a two-pane layout
                metadata = TwoPaneScene.twoPane()
            ) { _ ->
                TimelinePaneScene()
            }
        }
    )
}

/**
 * A custom [Scene] that displays two [NavEntry]s side-by-side in a 50/50 split.
 */
class TwoPaneScene<T : Any>(
    override val key: Any,
    override val previousEntries: List<NavEntry<T>>,
    val firstEntry: NavEntry<T>,
    val secondEntry: NavEntry<T>,
) : Scene<T> {
    override val entries: List<NavEntry<T>> = listOf(firstEntry, secondEntry)
    override val content: @Composable (() -> Unit) = {
        Row(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.weight(0.5f)) {
                firstEntry.content.invoke(firstEntry.key)
            }
            Column(modifier = Modifier.weight(0.5f)) {
                secondEntry.content.invoke(secondEntry.key)
            }
        }
    }

    companion object {
        internal const val TWO_PANE_KEY = "TwoPane"

        /**
         * Helper function to add metadata to a [NavEntry] indicating it can be displayed
         * in a two-pane layout.
         */
        fun twoPane() = mapOf(TWO_PANE_KEY to true)
    }
}

// --- TwoPaneSceneStrategy ---
/**
 * A [SceneStrategy] that activates a [TwoPaneScene] if the window is wide enough
 * and the top two back stack entries declare support for two-pane display.
 */
class TwoPaneSceneStrategy<T : Any> : SceneStrategy<T> {
    @Composable
    override fun calculateScene(
        entries: List<NavEntry<T>>,
        onBack: (Int) -> Unit,
    ): Scene<T>? {

        val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass

        // Condition 1: Only return a Scene if the window is sufficiently wide to render two panes.
        // We use isWidthAtLeastBreakpoint with WIDTH_DP_MEDIUM_LOWER_BOUND (600dp).
        if (!windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_MEDIUM_LOWER_BOUND)) {
            return null
        }

        val lastTwoEntries = entries.takeLast(2)

        // Condition 2: Only return a Scene if there are two entries, and both have declared
        // they can be displayed in a two pane scene.
        return if (lastTwoEntries.size == 2 &&
            lastTwoEntries.all { it.metadata.containsKey(TwoPaneScene.TWO_PANE_KEY) }
        ) {
            val firstEntry = lastTwoEntries.first()
            val secondEntry = lastTwoEntries.last()

            // The scene key must uniquely represent the state of the scene.
            val sceneKey = Pair(firstEntry.key, secondEntry.key)

            TwoPaneScene(
                key = sceneKey,
                // Where we go back to is a UX decision. In this case, we only remove the top
                // entry from the back stack, despite displaying two entries in this scene.
                // This is because in this app we only ever add one entry to the
                // back stack at a time. It would therefore be confusing to the user to add one
                // when navigating forward, but remove two when navigating back.
                previousEntries = entries.dropLast(1),
                firstEntry = firstEntry,
                secondEntry = secondEntry
            )
        } else {
            null
        }
    }
}

@Composable
fun TimelinePaneScene(
    viewModel: HomeViewModel = koinViewModel(),
    onNewEntry: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    TimelinePane(
        uiState = TimelineUiState(items = uiState.items),
        onNewEntry = onNewEntry,
        onShareMemory = {},
        onOpenDay = {},
        onProfileClick = onOpenSettings,
        modifier = Modifier.safeDrawingPadding()
    )
}

@Composable
fun TimelineDetailPaneScene(
    viewModel: HomeViewModel = koinViewModel(),
    onNewEntry: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    TimelineDayDetailPanel(
        uiState=uiState.items.filter { it. }
}
