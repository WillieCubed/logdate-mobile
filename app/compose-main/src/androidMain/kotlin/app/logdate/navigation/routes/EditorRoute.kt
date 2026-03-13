@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package app.logdate.navigation.routes

import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import app.logdate.feature.editor.ui.LocalSharedTransitionScope
import app.logdate.feature.editor.ui.NoteEditorScreen
import app.logdate.navigation.routes.core.EntryEditor
import app.logdate.feature.editor.ui.LocalAnimatedVisibilityScope as EditorLocalAnimatedVisibilityScope
import app.logdate.navigation.LocalSharedTransitionScope as NavigationLocalSharedTransitionScope

private const val FAB_TO_EDITOR_SHARED_ELEMENT_KEY = "fab_to_editor"

// Front-loads the bounds movement so the shape rounding is visible early in the transition
// rather than only at the very end when the bounds approach FAB size.
private val FabEditorBoundsTransform =
    BoundsTransform { _, _ ->
        tween(durationMillis = 350, easing = FastOutSlowInEasing)
    }

/**
 * Provides the navigation routes for the note editor screen.
 *
 * This function sets up the entry for the note editor with custom transition animations.
 *
 * @param onBack Callback to handle back navigation.
 * @param onSave Callback triggered after the entry is saved
 */
fun EntryProviderScope<NavKey>.editorRoutes(
    onBack: () -> Unit,
    onSave: () -> Unit,
) {
    routeEntry<EntryEditor> {
        val navigationScope = NavigationLocalSharedTransitionScope.current
        val animatedContentScope = LocalNavAnimatedContentScope.current

        val sharedBoundsModifier =
            if (navigationScope != null) {
                with(navigationScope) {
                    Modifier.sharedBounds(
                        rememberSharedContentState(key = FAB_TO_EDITOR_SHARED_ELEMENT_KEY),
                        animatedVisibilityScope = animatedContentScope,
                        boundsTransform = FabEditorBoundsTransform,
                        clipInOverlayDuringTransition = OverlayClip(MaterialTheme.shapes.large),
                    )
                }
            } else {
                Modifier
            }

        CompositionLocalProvider(
            LocalSharedTransitionScope provides navigationScope,
            EditorLocalAnimatedVisibilityScope provides animatedContentScope,
        ) {
            NoteEditorScreen(
                onNavigateBack = onBack,
                onEntrySaved = onSave,
                modifier = sharedBoundsModifier,
            )
        }
    }
}
