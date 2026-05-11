@file:OptIn(ExperimentalSharedTransitionApi::class)

package app.logdate.desktop

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.rememberWindowState
import app.logdate.feature.editor.ui.EntryEditorContent
import app.logdate.feature.editor.ui.editor.EntryBlockUiState
import app.logdate.feature.editor.ui.editor.EntryEditorViewModel
import app.logdate.feature.editor.ui.editor.TextBlockUiState
import app.logdate.ui.LocalNavAnimatedVisibilityScope
import app.logdate.ui.LocalSharedTransitionScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.koin.compose.viewmodel.koinViewModel

private const val DEFAULT_ENTRY_TITLE = "New Entry"

/**
 * Soft cap on the derived window title so a long first sentence does not blow
 * out the OS title bar. Chosen to comfortably fit on a 1024dp main window's
 * title bar with room for the platform window controls on either side.
 */
private const val TITLE_PREVIEW_CHARS = 48

/**
 * A window that allows the user to edit a journal entry.
 *
 * Supports a fullscreen, immersive journaling mode toggled with F11 (or
 * Cmd+Shift+F on macOS hosts that map F11 to system controls).
 */
@Suppress("ktlint:standard:function-naming")
@Composable
internal fun EntryEditorWindow(
    appState: LogDateApplicationState,
    state: EntryEditorWindowState = rememberEntryEditorWindowState(appState),
    viewModel: EntryEditorViewModel = koinViewModel(),
) {
    val windowState = rememberWindowState()
    val editorState by viewModel.editorState.collectAsState()

    LaunchedEffect(state.isFullscreen) {
        windowState.placement = if (state.isFullscreen) WindowPlacement.Fullscreen else WindowPlacement.Floating
    }

    LaunchedEffect(Unit) {
        viewModel.editorState
            .map { it.blocks }
            .distinctUntilChanged()
            .map { it.deriveWindowTitle() }
            .distinctUntilChanged()
            .collect { derived -> state.updateTitle(derived) }
    }

    // Shared close handler: if the entry has no unsaved work, dispose the window; otherwise kick
    // off the viewModel save so the existing onEntrySaved hook can close once persistence
    // completes. Guarded against re-entry while a save is already in flight.
    val handleClose: () -> Unit = {
        when {
            editorState.canExitWithoutSaving -> appState.closeWindow(state)
            !editorState.isSaving -> viewModel.saveEntry(editorState)
            else -> Unit
        }
    }

    // When the application-level exit cascade requests this editor close, run the same flow.
    LaunchedEffect(state.closeRequested) {
        if (state.closeRequested) {
            handleClose()
        }
    }

    Window(
        onCloseRequest = handleClose,
        title = state.title,
        state = windowState,
        onKeyEvent = { event ->
            if (event.type == KeyEventType.KeyDown && event.key == Key.F11) {
                state.toggleFullscreen()
                true
            } else {
                false
            }
        },
    ) {
        // The editor's File menu binds Cmd+W to its save-aware close so a stray
        // OS-level Close shortcut still hits handleClose, and Cmd+Q goes through
        // the application-wide cascade so dirty editors save before exit.
        MenuBar {
            Menu(text = "File", mnemonic = 'F') {
                Item(
                    text = "Close Window",
                    shortcut = KeyShortcut(Key.W, meta = true),
                    onClick = handleClose,
                )
                Separator()
                Item(
                    text = "Quit LogDate",
                    shortcut = KeyShortcut(Key.Q, meta = true),
                    onClick = appState::exit,
                )
            }
        }
        SharedTransitionLayout {
            CompositionLocalProvider(
                LocalSharedTransitionScope provides this,
            ) {
                AnimatedVisibility(true) {
                    CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                        EntryEditorContent(
                            viewModel = viewModel,
                            onNavigateBack = handleClose,
                            onEntrySaved = { appState.closeWindow(state) },
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
    initialTitle: String = DEFAULT_ENTRY_TITLE,
) : LogDateWindowState {
    var title: String by mutableStateOf(initialTitle)
        private set

    var isFullscreen: Boolean by mutableStateOf(false)
        private set

    /**
     * One-shot signal that this editor should run its save-or-close flow. Flipped to true by
     * [LogDateApplicationState.exit] / [LogDateApplicationState.closeWindow] when closing the
     * main window so each editor saves its dirty draft before the app exits.
     */
    var closeRequested: Boolean by mutableStateOf(false)
        private set

    fun updateTitle(newTitle: String) {
        title = newTitle
    }

    fun toggleFullscreen() {
        isFullscreen = !isFullscreen
    }

    fun requestClose() {
        closeRequested = true
    }

    override fun exit(): Boolean = true
}

private fun List<EntryBlockUiState>.deriveWindowTitle(): String {
    val firstLine =
        asSequence()
            .filterIsInstance<TextBlockUiState>()
            .map { it.content.trim() }
            .firstOrNull { it.isNotBlank() }
            ?.lineSequence()
            ?.firstOrNull { it.isNotBlank() }
            ?.trim()
            .orEmpty()

    if (firstLine.isEmpty()) return DEFAULT_ENTRY_TITLE
    if (firstLine.length <= TITLE_PREVIEW_CHARS) return firstLine
    return firstLine.take(TITLE_PREVIEW_CHARS).trimEnd() + "…"
}
