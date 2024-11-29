package app.logdate.client.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import app.logdate.feature.core.main.homeGraph
import app.logdate.feature.core.main.navigateHome
import app.logdate.feature.core.navigation.BaseRoute
import app.logdate.feature.core.navigation.landingDestination
import app.logdate.feature.core.settings.navigation.navigateToSettings
import app.logdate.feature.core.settings.navigation.settingsDestination
import app.logdate.feature.editor.navigation.editorDestination
import app.logdate.feature.editor.navigation.navigateToNoteCreation
import app.logdate.feature.journals.navigation.journalDetailsRoute
import app.logdate.feature.journals.navigation.navigateToJournalFromNew
import app.logdate.feature.journals.navigation.navigateToJournalsOverview
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
                navController.navigateToNoteCreation()
            },
            onOpenRewind = {
                navController.navigateToRewind(it)
            },
            onOpenSettings = navController::navigateToSettings,
        )
        journalDetailsRoute(
            onGoBack = {
                navController.popBackStack()
            },
            onJournalCreated = { journalId ->
                navController.navigateToJournalFromNew(journalId)
            },
            onJournalDeleted = {
                navController.navigateToJournalsOverview()
            },
        )
        editorDestination(
            onClose = {
                navController.popBackStack()
            },
            onNoteSaved = {
                navController.navigateHome()
            },
        )
        settingsDestination(
            onGoBack = {
                navController.popBackStack()
            },
            onAppReset = navController::startOnboarding,
        )
    }
}