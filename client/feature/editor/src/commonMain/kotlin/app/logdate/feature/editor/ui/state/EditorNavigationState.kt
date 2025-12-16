package app.logdate.feature.editor.ui.state

import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.CoroutineScope

/**
 * Represents the navigation and mode selection state of the editor.
 * Acts as a focused bus between the ViewModel and navigation/mode components.
 *
 * @property pagerState The pager state for switching between editor modes
 * @property hasContent Whether the editor has any content
 * @property coroutineScope Coroutine scope for launching animations and async operations
 */
@Immutable
data class EditorNavigationState(
    val pagerState: PagerState,
    val hasContent: Boolean,
    val coroutineScope: CoroutineScope
)