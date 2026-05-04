package app.logdate.feature.postcards.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import androidx.navigation3.runtime.NavKey
import app.logdate.feature.postcards.ui.CanvasEditorScreen
import app.logdate.feature.postcards.ui.PostcardViewerScreen
import app.logdate.feature.postcards.ui.PostcardsCollectionScreen
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/**
 * Browse the user's postcard collection (grid of cards). Reachable from the Library tab.
 */
@Serializable
data object PostcardsCollectionRoute : NavKey

/**
 * Open the canvas editor for a new or existing postcard. The optional `postcardId` is read
 * back out of `SavedStateHandle` by `CanvasEditorViewModel` (the property name must match
 * the ViewModel's `savedStateHandle.get<String>("postcardId")` lookup).
 */
@Serializable
data class PostcardEditorRoute(val postcardId: String? = null) : NavKey {
    constructor(postcardId: Uuid?) : this(postcardId?.toString())
}

@Serializable
data class PostcardViewerRoute(val postcardId: String) : NavKey {
    constructor(postcardId: Uuid) : this(postcardId.toString())
}

fun NavController.navigateToPostcardsCollection() {
    navigate(PostcardsCollectionRoute)
}

fun NavController.navigateToPostcardEditor(postcardId: Uuid? = null) {
    navigate(PostcardEditorRoute(postcardId))
}

fun NavController.navigateToPostcardViewer(postcardId: Uuid) {
    navigate(PostcardViewerRoute(postcardId))
}

/**
 * Registers all three postcards destinations. iOS / desktop pass null for the platform-specific
 * `onSaveToFiles`, `onPrint`, and `onToggleFullscreen` callbacks for now — those depend on
 * Android `ActivityResultContracts` and `WindowInsetsControllerCompat` and need separate
 * platform integration.
 */
fun NavGraphBuilder.postcardsRoutes(
    navController: NavController,
    onShareUri: (String) -> Unit = {},
    onNavigateToMoment: (Uuid) -> Unit = {},
) {
    composable<PostcardsCollectionRoute> {
        PostcardsCollectionScreen(
            onOpenPostcard = { postcardId -> navController.navigateToPostcardViewer(postcardId) },
            onEditPostcard = { postcardId -> navController.navigateToPostcardEditor(postcardId) },
            onCreateNew = { navController.navigateToPostcardEditor() },
        )
    }
    composable<PostcardEditorRoute> {
        CanvasEditorScreen(
            onNavigateBack = { navController.popBackStack() },
            onSaved = { navController.popBackStack() },
            onToggleFullscreen = null,
        )
    }
    composable<PostcardViewerRoute> { entry ->
        val route = entry.toRoute<PostcardViewerRoute>()
        PostcardViewerScreen(
            onNavigateBack = { navController.popBackStack() },
            onEditPostcard = { postcardId -> navController.navigateToPostcardEditor(postcardId) },
            onShareUri = onShareUri,
            onSaveToFiles = null,
            onPrint = null,
            onNavigateToMoment = onNavigateToMoment,
        )
    }
}
