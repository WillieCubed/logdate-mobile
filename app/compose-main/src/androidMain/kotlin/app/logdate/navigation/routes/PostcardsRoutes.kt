package app.logdate.navigation.routes

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
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
    onShareUri: (String) -> Unit = {},
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
        val context = LocalContext.current
        var pendingSourceUri: Uri? = null
        val saveFileLauncher =
            rememberLauncherForActivityResult(
                contract = ActivityResultContracts.CreateDocument("image/png"),
            ) { destinationUri ->
                if (destinationUri != null && pendingSourceUri != null) {
                    context.contentResolver.openInputStream(pendingSourceUri!!)?.use { input ->
                        context.contentResolver.openOutputStream(destinationUri)?.use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }

        PostcardViewerScreen(
            onNavigateBack = onBack,
            onEditPostcard = { onEditPostcard(it) },
            onShareUri = onShareUri,
            onSaveToFiles = { uri ->
                pendingSourceUri = Uri.parse(uri)
                saveFileLauncher.launch("postcard.png")
            },
            onNavigateToMoment = onNavigateToMoment,
        )
    }
}
