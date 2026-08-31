@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package app.logdate.feature.editor.navigation

import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import app.logdate.feature.editor.ui.NoteEditorScreen
import app.logdate.ui.LocalSharedTransitionScope
import app.logdate.ui.common.transitions.TransitionKeys
import app.logdate.ui.navigation.taggedEntry
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid
import app.logdate.feature.editor.ui.LocalAnimatedVisibilityScope as EditorLocalAnimatedVisibilityScope
import app.logdate.feature.editor.ui.LocalSharedTransitionScope as EditorLocalSharedTransitionScope

private val FabEditorBoundsTransform =
    BoundsTransform { _, _ ->
        tween(durationMillis = 350, easing = FastOutSlowInEasing)
    }

/**
 * Typed route for the entry editor. Carries optional context so the screen can resume an
 * existing entry or draft, or pre-select journals for a brand-new entry.
 *
 * Strings rather than `Uuid`s because Navigation 3's saved-state serialization on iOS does
 * not handle `Uuid` natively yet.
 */
@Serializable
data class EntryEditorRoute(
    val entryId: String? = null,
    val draftId: String? = null,
    val journalIds: List<String> = emptyList(),
) : NavKey

/** Pushes the editor onto the back stack with optional entry / draft / journal context. */
fun NavBackStack<NavKey>.navigateToEditor(
    entryId: Uuid? = null,
    draftId: Uuid? = null,
    journalIds: List<Uuid> = emptyList(),
) {
    add(
        EntryEditorRoute(
            entryId = entryId?.toString(),
            draftId = draftId?.toString(),
            journalIds = journalIds.map { it.toString() },
        ),
    )
}

/**
 * Registers the editor entry. The hosting graph supplies callbacks for back / save so the
 * editor module never has to know about the surrounding back stack shape.
 */
fun EntryProviderScope<NavKey>.editorEntry(
    onNavigateBack: () -> Unit,
    onEntrySaved: () -> Unit,
    metadata: Map<String, Any> = emptyMap(),
) {
    taggedEntry<EntryEditorRoute>(metadata = metadata) { route ->
        val navigationScope = LocalSharedTransitionScope.current
        val animatedContentScope = LocalNavAnimatedContentScope.current
        val sharedBoundsModifier =
            if (navigationScope != null) {
                with(navigationScope) {
                    Modifier.sharedBounds(
                        rememberSharedContentState(key = TransitionKeys.FAB_TO_EDITOR_TRANSITION),
                        animatedVisibilityScope = animatedContentScope,
                        boundsTransform = FabEditorBoundsTransform,
                        clipInOverlayDuringTransition = OverlayClip(MaterialTheme.shapes.large),
                    )
                }
            } else {
                Modifier
            }

        CompositionLocalProvider(
            EditorLocalSharedTransitionScope provides navigationScope,
            EditorLocalAnimatedVisibilityScope provides animatedContentScope,
        ) {
            NoteEditorScreen(
                onNavigateBack = onNavigateBack,
                onEntrySaved = onEntrySaved,
                entryId = route.entryId?.let(Uuid::parse),
                draftId = route.draftId?.let(Uuid::parse),
                journalIds = route.journalIds.map(Uuid::parse),
                modifier = sharedBoundsModifier,
            )
        }
    }
}
