package app.logdate.feature.editor.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.logdate.feature.editor.ui.EntryEditorScreen
import kotlinx.serialization.Serializable

/**
 * Route for the note creation screen.
 *
 * @param textContent The initial text content to display in the note editor.
 * @param attachments A list of attachments expressed as URIs to display in the note editor.
 */
@Serializable
data class NewNoteRoute(
    val textContent: String? = null,
    val attachments: List<String> = emptyList(),
)

/**
 * Navigates to the note creation screen.
 */
fun NavController.navigateToNoteCreation(
    initialTextContent: String? = null,
    attachments: List<String> = emptyList(),
) {
    navigate(NewNoteRoute(
        textContent = initialTextContent,
        attachments = attachments,
    ))
}

/**
 * Defines the navigation graph for the note creation screen.
 *
 * @param onClose The callback to invoke when the user closes the note creation screen
 * @param onNoteSaved The callback to invoke when the user has saved a note. Clients should use
 * this to navigate back to the previous screen.
 */
fun NavGraphBuilder.editorDestination(
    onClose: () -> Unit,
    onNoteSaved: () -> Unit,
) {
    composable<NewNoteRoute>(
//        enterTransition = {
//            slideIntoContainer(
//                AnimatedContentTransitionScope.SlideDirection.Up,
//                animationSpec = tween(400),
//            )
//        },
//        exitTransition = {
//            slideOutOfContainer(
//                AnimatedContentTransitionScope.SlideDirection.Down,
//                animationSpec = tween(200),
//            )
//        },
        deepLinks = listOf(
            // TODO: Re-add deep links for sharing text, images, and multiple images
        ),
    ) {
        EntryEditorScreen(
            onNavigateBack = onClose,
            onEntrySaved = {
                onNoteSaved()
            },
        )
    }
}

