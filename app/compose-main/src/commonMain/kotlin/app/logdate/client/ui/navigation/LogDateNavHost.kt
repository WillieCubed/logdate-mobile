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
import app.logdate.feature.core.settings.navigation.navigateToSettings
import app.logdate.feature.core.settings.navigation.settingsDestination
import app.logdate.feature.editor.ui.EntryEditorContent
import app.logdate.feature.journals.navigation.journalDetailsRoute
import app.logdate.feature.journals.navigation.journalSettingsRoute
import app.logdate.feature.journals.navigation.navigateToJournal
import app.logdate.feature.journals.navigation.navigateToJournalCreation
import app.logdate.feature.journals.navigation.navigateToJournalsOverview
import app.logdate.feature.journals.navigation.navigateToNoteDetail
import app.logdate.feature.journals.navigation.newJournalRoute
import app.logdate.feature.journals.navigation.noteDetailRoute
import app.logdate.feature.onboarding.navigation.onboardingGraph
import app.logdate.feature.onboarding.navigation.startOnboarding
import app.logdate.feature.rewind.navigation.navigateToRewind

/**
 * The root composable for app-wide navigation.
 */
@Composable
internal fun LogDateNavHost(
    navController: NavHostController = rememberNavController(),
) {
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
                // Instead of using navigateToNoteCreation, we'll navigate to the editorDestination directly
                // using the EntryEditor route from MainNavigationRoot
                navController.navigate("editor")
            },
            onOpenJournal = navController::navigateToJournal,
            onCreateJournal = navController::navigateToJournalCreation,
            onOpenRewind = {
                navController.navigateToRewind(it)
            },
            onOpenSettings = navController::navigateToSettings,
        )
        journalDetailsRoute(
            onGoBack = {
                navController.popBackStack()
            },
            onJournalDeleted = {
                navController.navigateToJournalsOverview()
            },
            onNavigateToNoteDetail = { noteId, journalId ->
                navController.navigateToNoteDetail(noteId, journalId)
            },
        )
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
                }
            )
        }
        noteDetailRoute(
            onGoBack = {
                navController.popBackStack()
            },
        )
        settingsDestination(
            onGoBack = {
                navController.popBackStack()
            },
            onAppReset = navController::startOnboarding,
            onNavigateToCloudAccountCreation = {
                // TODO: Implement cloud account creation navigation
                navController.popBackStack()
            },
            onNavigateToProfile = {
                // TODO: Implement profile navigation
                navController.popBackStack()
            },
            navController = navController,
        )
    }
}