package app.logdate.feature.journals.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import app.logdate.feature.journals.ui.JournalDetailRoute

const val JOURNAL_ID_ARG = "journalId"
const val JOURNAL_DETAILS_ROUTE = "journal/{$JOURNAL_ID_ARG}"

fun NavController.navigateToJournal(journalId: String) =
    navigate("journal/${journalId}")

fun NavGraphBuilder.journalsRoute(
    onGoBack: () -> Unit,
) {
    composable(
        route = JOURNAL_DETAILS_ROUTE,
        arguments = listOf(navArgument(JOURNAL_ID_ARG) { type = NavType.StringType }),

        // TOOD: Add enter and exit transitions
    ) { // journalDetailRoute
        JournalDetailRoute(onGoBack = onGoBack)
    }
}

//fun NavGraphBuilder.journalsRoute(
//    onGoBack: () -> Unit,
//    onOpenJournal: () -> Unit,
//) {
//    composable(
//        route = JOURNALS_ROUTE,
//        // TODO: Support deep link
//    ) {
//        JournalsRoute(onGoBack, onOpenJournal)
//    }
//    composable(
//        route = "$JOURNALS_ROUTE/{journalId}",
//        arguments = listOf(navArgument("journalId") { type = NavType.StringType }),
//    ) {
//
//    }
//}