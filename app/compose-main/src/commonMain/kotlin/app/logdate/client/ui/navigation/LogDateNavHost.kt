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
import app.logdate.feature.core.settings.navigation.BirthdaySettingsRoute
import app.logdate.feature.core.settings.navigation.ExportSettingsRoute
import app.logdate.feature.core.settings.navigation.SettingsRoute
import app.logdate.feature.core.settings.navigation.settingsGraph
import app.logdate.feature.editor.ui.EntryEditorContent
import app.logdate.feature.events.navigation.eventDetailRoute
import app.logdate.feature.journals.navigation.journalDetailsRoute
import app.logdate.feature.journals.navigation.journalSettingsRoute
import app.logdate.feature.journals.navigation.journalsOverviewRoute
import app.logdate.feature.journals.navigation.navigateToJournal
import app.logdate.feature.journals.navigation.navigateToJournalCreation
import app.logdate.feature.journals.navigation.navigateToJournalsOverview
import app.logdate.feature.journals.navigation.navigateToNoteDetail
import app.logdate.feature.journals.navigation.newJournalRoute
import app.logdate.feature.journals.navigation.noteDetailRoute
import app.logdate.feature.library.navigation.MediaDetailRoute
import app.logdate.feature.library.navigation.libraryRoute
import app.logdate.feature.library.navigation.mediaDetailRoute
import app.logdate.feature.library.ui.LibraryScreen
import app.logdate.feature.location.timeline.ui.LocationTimelineScreen
import app.logdate.feature.onboarding.navigation.onboardingGraph
import app.logdate.feature.rewind.navigation.navigateToRewind
import app.logdate.feature.rewind.navigation.rewindRoutes
import app.logdate.feature.search.ui.SearchScreen
import kotlinx.serialization.Serializable

@Serializable
internal data object SearchRoute

@Serializable
internal data object LocationTimelineRoute

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
            onOpenSettings = { navController.navigate(SettingsRoute()) },
            onBrowseJournals = navController::navigateToJournalsOverview,
            onOpenSearch = { navController.navigate(SearchRoute) },
            onOpenDraft = { navController.navigate("editor") },
            onImportBackup = { navController.navigate(ExportSettingsRoute) },
            onOpenMediaDetail = { mediaId -> navController.navigate(MediaDetailRoute(mediaId)) },
            libraryContent = { modifier ->
                LibraryScreen(
                    onOpenMediaDetail = { mediaId -> navController.navigate(MediaDetailRoute(mediaId)) },
                    modifier = modifier,
                )
            },
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
        libraryRoute(
            onOpenMediaDetail = { mediaId -> navController.navigate(MediaDetailRoute(mediaId)) },
        )
        mediaDetailRoute(
            onGoBack = { navController.popBackStack() },
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
        eventDetailRoute(
            onGoBack = { navController.popBackStack() },
        )

        profileRoute(
            onGoBack = { navController.popBackStack() },
            onNavigateToBirthday = { navController.navigate(BirthdaySettingsRoute) },
        )

        rewindRoutes(
            onOpenRewind = { navController.navigateToRewind(it) },
            onGoBack = { navController.popBackStack() },
        )

        settingsGraph(navController)
        composable<SearchRoute> {
            SearchScreen(
                onGoBack = { navController.popBackStack() },
                onNavigateToDay = { /* Timeline day detail not available in common nav */ },
                onNavigateToJournal = { journalId -> navController.navigateToJournal(journalId) },
            )
        }
        composable<LocationTimelineRoute> {
            LocationTimelineScreen(
                onOpenNote = { noteId -> navController.navigateToNoteDetail(noteId) },
            )
        }
    }
}
