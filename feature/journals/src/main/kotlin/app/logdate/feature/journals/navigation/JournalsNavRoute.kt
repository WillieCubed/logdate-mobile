package app.logdate.feature.journals.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import app.logdate.feature.journals.ui.creation.JournalCreationRoute
import app.logdate.feature.journals.ui.detail.JournalDetailRoute

const val ROUTE_JOURNAL_BASE = "journals"
const val JOURNAL_ID_ARG = "journalId"
const val JOURNAL_NEW_ROUTE = "$ROUTE_JOURNAL_BASE/new"
const val JOURNAL_DETAILS_ROUTE = "$ROUTE_JOURNAL_BASE/{$JOURNAL_ID_ARG}"

fun NavController.navigateToJournal(journalId: String) {
    navigate("$ROUTE_JOURNAL_BASE/${journalId}")
}

fun NavController.navigateFromNew(journalId: String) {
    // Navigate to journal detail after creating a new journal, except make sure to pop the back stack
    // to avoid going back to the new journal screen.
    navigate("$ROUTE_JOURNAL_BASE/$journalId") {
        popUpTo(JOURNAL_NEW_ROUTE) { inclusive = true }
    }
}

fun NavGraphBuilder.journalsRoute(
    onGoBack: () -> Unit,
    onJournalCreated: (String) -> Unit,
    onJournalDeleted: () -> Unit,
) {
    composable(
        route = JOURNAL_DETAILS_ROUTE,
        arguments = listOf(navArgument(JOURNAL_ID_ARG) { type = NavType.StringType }),
        enterTransition = {
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Left,
            )
        },
        exitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Right,
            )
        }
    ) { // journalDetailRoute
        JournalDetailRoute(onGoBack = onGoBack, onJournalDeleted = onJournalDeleted)
    }
    newJournalRoute(
        onGoBack = onGoBack,
        onCreateJournal = onJournalCreated,
    )
}

fun NavGraphBuilder.newJournalRoute(
    onGoBack: () -> Unit,
    onCreateJournal: (journalId: String) -> Unit,
) {
    composable(
        route = JOURNAL_NEW_ROUTE,
        enterTransition = {
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Left,
            )
        },
        exitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Right,
            )
        }
    ) {
        JournalCreationRoute(
            onGoBack = onGoBack,
            initialTitle = "",
            onJournalCreated = onCreateJournal,
        )
    }
}
