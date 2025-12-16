@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package app.logdate.navigation.routes

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigation3.runtime.EntryProviderBuilder
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entry
import app.logdate.feature.editor.ui.LocalAnimatedVisibilityScope as EditorLocalAnimatedVisibilityScope
import app.logdate.feature.editor.ui.LocalSharedTransitionScope
import app.logdate.feature.editor.ui.NoteEditorScreen
import app.logdate.navigation.LocalSharedTransitionScope as NavigationLocalSharedTransitionScope
import app.logdate.navigation.routes.core.EntryEditor
import app.logdate.navigation.scenes.LocalAnimatedVisibilityScope


/**
 * Provides the navigation routes for the note editor screen.
 *
 * This function sets up the entry for the note editor with custom transition animations.
 *
 * @param onBack Callback to handle back navigation.
 * @param onSave Callback triggered after the entry is saved
 */
fun EntryProviderBuilder<NavKey>.editorRoutes(
    onBack: () -> Unit,
    onSave: () -> Unit,
) {
    entry<EntryEditor> {
        // Bridge the SharedTransitionScope from navigation to editor module
        val navigationScope = NavigationLocalSharedTransitionScope.current
        
        // Use AnimatedVisibility to provide the AnimatedVisibilityScope
        AnimatedVisibility(visible = true) {
            CompositionLocalProvider(
                LocalSharedTransitionScope provides navigationScope,
                EditorLocalAnimatedVisibilityScope provides this@AnimatedVisibility
            ) {
                NoteEditorScreen(
                    onNavigateBack = onBack,
                    onEntrySaved = onSave,
                )
            }
        }
    }
}