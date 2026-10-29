@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package app.logdate.feature.editor.ui.handwriting

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.logdate.feature.editor.ui.LocalAnimatedVisibilityScope
import app.logdate.feature.editor.ui.LocalBlockFocusProgress
import app.logdate.feature.editor.ui.LocalRetainedEditorSurface
import app.logdate.feature.editor.ui.LocalSharedTransitionScope
import app.logdate.feature.editor.ui.editorBlockFocusElement
import app.logdate.feature.editor.ui.layout.EditorBlockCornerRadius
import kotlin.uuid.Uuid

/** Share just the paper and ink; toolbars and neighboring entry content stay outside the overlay. */
@Composable
internal fun Modifier.handwritingSharedElement(
    blockId: Uuid,
    focusProgress: Float,
): Modifier {
    if (LocalRetainedEditorSurface.current) return this
    val shared = LocalSharedTransitionScope.current ?: return this
    val visibility = LocalAnimatedVisibilityScope.current ?: return this
    return editorBlockFocusElement(shared, visibility, blockId, EditorBlockCornerRadius, LocalBlockFocusProgress.current ?: focusProgress)
}
