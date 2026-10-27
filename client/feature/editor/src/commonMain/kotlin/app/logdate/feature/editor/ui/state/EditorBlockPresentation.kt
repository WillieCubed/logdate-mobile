package app.logdate.feature.editor.ui.state

import androidx.compose.runtime.Composable

/** Alternate rendering for an editor block while a new content type is being prototyped. */
class EditorBlockPresentation(
    val inline: @Composable (onFocus: () -> Unit) -> Unit,
    val focused: @Composable () -> Unit,
    val immersive: Boolean = false,
    val focusTransitionProgress: Float = 0f,
)
