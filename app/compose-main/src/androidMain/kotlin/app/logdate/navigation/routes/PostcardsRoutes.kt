package app.logdate.navigation.routes

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import app.logdate.feature.postcards.copyUriToDestination
import app.logdate.feature.postcards.printPostcard
import app.logdate.feature.postcards.ui.CanvasEditorScreen
import app.logdate.feature.postcards.ui.PostcardViewerScreen
import app.logdate.feature.postcards.ui.PostcardsCollectionScreen
import app.logdate.navigation.MainAppNavigator
import app.logdate.navigation.routes.core.PostcardEditorRoute
import app.logdate.navigation.routes.core.PostcardViewerRoute
import app.logdate.navigation.routes.core.PostcardsCollectionRoute
import kotlinx.coroutines.launch
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
        val view = LocalView.current
        var isFullscreen by rememberSaveable { mutableStateOf(false) }

        fun setFullscreen(enabled: Boolean) {
            val controller =
                WindowCompat.getInsetsController(
                    (view.context as Activity).window,
                    view,
                )
            isFullscreen = enabled
            if (enabled) {
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }

        CanvasEditorScreen(
            onNavigateBack = {
                if (isFullscreen) setFullscreen(false)
                onBack()
            },
            onSaved = onBack,
            onToggleFullscreen = { setFullscreen(!isFullscreen) },
        )
    }

    routeEntry<PostcardViewerRoute> { _ ->
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        var pendingSourceUri by rememberSaveable { mutableStateOf<String?>(null) }

        val saveFileLauncher =
            rememberLauncherForActivityResult(
                contract = ActivityResultContracts.CreateDocument("image/png"),
            ) { destinationUri ->
                val source =
                    pendingSourceUri?.let { Uri.parse(it) }
                        ?: return@rememberLauncherForActivityResult
                if (destinationUri != null) {
                    scope.launch { copyUriToDestination(context, source, destinationUri) }
                }
            }

        PostcardViewerScreen(
            onNavigateBack = onBack,
            onEditPostcard = { onEditPostcard(it) },
            onShareUri = onShareUri,
            onSaveToFiles = { uri ->
                pendingSourceUri = uri
                saveFileLauncher.launch("postcard.png")
            },
            onPrint = { uri -> printPostcard(context, Uri.parse(uri)) },
            onNavigateToMoment = onNavigateToMoment,
        )
    }
}
