package app.logdate.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import app.logdate.feature.core.main.HomeRoute
import app.logdate.feature.editor.navigation.EntryEditorRoute
import app.logdate.feature.editor.navigation.RecordingEditorDestination
import app.logdate.feature.editor.navigation.completeEditorVisit
import app.logdate.feature.editor.navigation.editorEntry
import app.logdate.ui.navigation.taggedEntry
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class EditorVisitLifecycleTest {
    @Test
    fun repeatedSaveDiscardBackAndDismissVisitsReleaseExitStateAndRecordingOwner() =
        runDesktopComposeUiTest(width = 411, height = 891) {
            val owner =
                object : ViewModelStoreOwner {
                    override val viewModelStore = ViewModelStore()
                }
            val route = EntryEditorRoute()
            val stack = mutableStateListOf<NavKey>(HomeRoute, route)
            val production = entryProvider<NavKey> { editorEntry(onNavigateBack = {}, onEntrySaved = {}) }
            var active: EditorVisitModel? = null
            setContent {
                CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                    NavDisplay(
                        backStack = stack,
                        onBack = { stack.removeLastOrNull() },
                        entryDecorators =
                            listOf(
                                rememberSaveableStateHolderNavEntryDecorator(),
                                rememberPerVisitViewModelsDecorator(),
                            ),
                        entryProvider =
                            entryProvider {
                                taggedEntry<HomeRoute> { Box(Modifier.fillMaxSize().testTag("home")) }
                                taggedEntry<EntryEditorRoute>(metadata = { production(it).metadata }) {
                                    val model = viewModel { EditorVisitModel() }
                                    SideEffect { active = model }
                                    Box(Modifier.fillMaxSize().testTag("editor"))
                                }
                            },
                    )
                }
            }
            for (exit in listOf("save", "discard", "Back", "gesture dismissal", "recording")) {
                onNodeWithTag("editor").assertIsDisplayed()
                lateinit var previous: EditorVisitModel
                runOnIdle {
                    previous = requireNotNull(active)
                    assertFalse(previous.shouldExit, "Add entry must start a fresh visit after $exit")
                    previous.shouldExit = true
                    stack.removeAt(stack.lastIndex)
                }
                onNodeWithTag("home").assertIsDisplayed()
                waitUntil(timeoutMillis = 5_000) { previous.cleared }
                runOnIdle { stack.add(route) }
                onNodeWithTag("editor").assertIsDisplayed()
                runOnIdle {
                    assertNotSame(previous, active)
                    assertTrue(previous.cleared)
                }
            }
            lateinit var recording: EditorVisitModel
            lateinit var other: EditorVisitModel
            val otherRoute = EntryEditorRoute(initialText = "Keep this shared text")
            runOnIdle {
                recording = requireNotNull(active)
                stack.add(otherRoute)
            }
            onNodeWithTag("editor").assertIsDisplayed()
            runOnIdle {
                other = requireNotNull(active)
                assertNotSame(recording, other)
                other.shouldExit = true
                RecordingEditorDestination.bind("current-draft", route)
                RecordingEditorDestination.open(stack, EntryEditorRoute(draftId = "current-draft", resumeRecording = true))
            }
            onNodeWithTag("editor").assertIsDisplayed()
            runOnIdle {
                assertTrue(active === recording, "Notification must re-present the original recording controls")
                assertFalse(recording.cleared)
                assertFalse(other.cleared, "Another editor's accepted content must stay owned")
                stack.completeEditorVisit(route, HomeRoute)
            }
            onNodeWithTag("editor").assertIsDisplayed()
            runOnIdle {
                assertTrue(active === other)
                assertTrue(other.shouldExit, "Retained editor state must survive a recording return")
                owner.viewModelStore.clear()
            }
        }
}

private class EditorVisitModel : ViewModel() {
    var shouldExit = false
    var cleared = false

    override fun onCleared() {
        cleared = true
    }
}
