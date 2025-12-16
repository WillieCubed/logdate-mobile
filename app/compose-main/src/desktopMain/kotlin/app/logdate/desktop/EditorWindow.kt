@file:OptIn(ExperimentalSharedTransitionApi::class)

package app.logdate.desktop

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Window
import app.logdate.feature.editor.ui.EntryEditorContent
import app.logdate.feature.editor.ui.editor.EntryEditorViewModel
import app.logdate.ui.LocalNavAnimatedVisibilityScope
import app.logdate.ui.LocalSharedTransitionScope
import org.koin.compose.viewmodel.koinViewModel

/**
 * A window that allows the user to edit a journal entry.
 */
@Composable
internal fun EntryEditorWindow(
    appState: LogDateApplicationState,
    state: EntryEditorWindowState = rememberEntryEditorWindowState(appState),
    viewModel: EntryEditorViewModel = koinViewModel(),
) {
    // TODO: Support fullscreen, immersive journaling experience
    Window(
        onCloseRequest = state::exit,
        title = state.title,
    ) {
        SharedTransitionLayout {
            CompositionLocalProvider(
                LocalSharedTransitionScope provides this
            ) {
                // TODO: Implement logic to automatically update title
                AnimatedVisibility(true) {
                    CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                        EntryEditorContent(
                            viewModel = viewModel,
                            onNavigateBack = {
                                state.exit()
                            },
                            onEntrySaved = {
                                state.exit()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun rememberEntryEditorWindowState(appState: LogDateApplicationState): EntryEditorWindowState =
    remember {
        EntryEditorWindowState(appState)
    }

class EntryEditorWindowState(
    appState: LogDateApplicationState,
    initialTitle: String = "New Entry",
) : LogDateWindowState {

    var title: String by mutableStateOf(initialTitle)
        private set

    fun updateTitle(newTitle: String) {
        title = newTitle
    }

    override fun exit(): Boolean {
        // TODO: Implement cleanup
        return true
    }
}