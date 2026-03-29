package app.logdate.navigation.routes

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import app.logdate.feature.postcards.ui.CanvasEditorScreen
import app.logdate.feature.postcards.ui.PostcardViewerScreen
import app.logdate.feature.postcards.ui.PostcardsCollectionScreen
import app.logdate.navigation.MainAppNavigator
import app.logdate.navigation.routes.core.PostcardEditorRoute
import app.logdate.navigation.routes.core.PostcardViewerRoute
import app.logdate.navigation.routes.core.PostcardsCollectionRoute
import kotlin.uuid.Uuid

fun MainAppNavigator.navigateToPostcardsCollection() {
    backStack.add(PostcardsCollectionRoute)
}

fun MainAppNavigator.navigateToPostcardEditor(
    postcardId: Uuid? = null,
    sourceMomentRef: Uuid? = null,
) {
    backStack.add(PostcardEditorRoute(postcardId, sourceMomentRef))
}

fun MainAppNavigator.navigateToPostcardViewer(postcardId: Uuid) {
    backStack.add(PostcardViewerRoute(postcardId))
}

/**
 * Provides navigation routes for Postcards screens.
 */
@Suppress("ktlint:standard:function-naming")
fun EntryProviderScope<NavKey>.postcardRoutes(
    onBack: () -> Unit,
    onOpenPostcard: (Uuid) -> Unit,
    onEditPostcard: (Uuid?) -> Unit,
    onNavigateToMoment: (Uuid) -> Unit,
) {
    routeEntry<PostcardsCollectionRoute> { _ ->
        PostcardsCollectionScreen(
            onOpenPostcard = onOpenPostcard,
            onCreateNew = { onEditPostcard(null) },
        )
    }

    routeEntry<PostcardEditorRoute> { _ ->
        CanvasEditorScreen(
            onNavigateBack = onBack,
            onSaved = onBack,
        )
    }

    routeEntry<PostcardViewerRoute> { route ->
        PostcardViewerScreen(
            onNavigateBack = onBack,
            onEditPostcard = { onEditPostcard(it) },
            onNavigateToMoment = onNavigateToMoment,
        )
    }
}
