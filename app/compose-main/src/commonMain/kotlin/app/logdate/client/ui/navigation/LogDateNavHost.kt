package app.logdate.client.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.logdate.feature.core.main.homeGraph
import app.logdate.feature.core.main.navigateHome
import app.logdate.feature.core.navigation.BaseRoute
import app.logdate.feature.core.navigation.landingDestination
import app.logdate.feature.core.profile.navigation.profileRoute
import app.logdate.feature.editor.ui.EntryEditorContent
import app.logdate.feature.journals.navigation.journalDetailsRoute
import app.logdate.feature.journals.navigation.journalSettingsRoute
import app.logdate.feature.journals.navigation.journalsOverviewRoute
import app.logdate.feature.journals.navigation.navigateToJournal
import app.logdate.feature.journals.navigation.navigateToJournalCreation
import app.logdate.feature.journals.navigation.navigateToJournalsOverview
import app.logdate.feature.journals.navigation.navigateToNoteDetail
import app.logdate.feature.journals.navigation.newJournalRoute
import app.logdate.feature.journals.navigation.noteDetailRoute
import app.logdate.feature.onboarding.navigation.onboardingGraph
import app.logdate.feature.rewind.navigation.navigateToRewind
import app.logdate.feature.rewind.navigation.rewindRoutes

/**
 * The root composable for app-wide navigation.
 */
@Suppress("ktlint:standard:function-naming")
@Composable
internal fun LogDateNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(
        navController = navController,
        startDestination = BaseRoute,
    ) {
        landingDestination()
        onboardingGraph(
            onNavigateBack = {
                navController.popBackStack()
            },
            onOnboardingComplete = {
                navController.navigateHome()
            },
            onWelcomeBack = {
                navController.navigateHome()
            },
            onGoToItem = {
                navController.navigate(it)
            },
        )
        homeGraph(
            onCreateNote = {
                // Use the editor destination directly to avoid extra route plumbing.
                navController.navigate("editor")
            },
            onOpenJournal = navController::navigateToJournal,
            onCreateJournal = navController::navigateToJournalCreation,
            onOpenRewind = {
                navController.navigateToRewind(it)
            },
            onOpenSettings = { /* Settings handled by Navigation 3 on Android */ },
            onBrowseJournals = navController::navigateToJournalsOverview,
        )
        journalsOverviewRoute(
            onOpenJournal = navController::navigateToJournal,
            onCreateJournal = navController::navigateToJournalCreation,
        )
        @Suppress("DEPRECATION")
        run {
            journalDetailsRoute(
                onGoBack = {
                    navController.popBackStack()
                },
                onJournalDeleted = {
                    navController.navigateToJournalsOverview()
                },
                onNavigateToNoteDetail = { noteId ->
                    navController.navigateToNoteDetail(noteId)
                },
            )
        }
        journalSettingsRoute(
            onGoBack = {
                navController.popBackStack()
            },
        )
        newJournalRoute(
            onGoBack = {
                navController.popBackStack()
            },
            onCreateJournal = { journalId ->
                navController.navigateToJournal(journalId)
            },
        )
        // Use a composable instead of editorDestination
        composable("editor") {
            EntryEditorContent(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onEntrySaved = {
                    navController.navigateHome()
                },
            )
        }
        noteDetailRoute(
            onGoBack = {
                navController.popBackStack()
            },
        )
        // Settings destination handled by Navigation 3 on Android

        profileRoute(
            onGoBack = { navController.popBackStack() },
            onNavigateToBirthday = { /* TODO: Navigate to birthday detail page */ },
        )

        rewindRoutes(
            onOpenRewind = { navController.navigateToRewind(it) },
            onGoBack = { navController.popBackStack() },
        )
    }
}
