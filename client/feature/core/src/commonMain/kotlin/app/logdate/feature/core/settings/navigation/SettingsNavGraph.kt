package app.logdate.feature.core.settings.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import app.logdate.client.permissions.rememberContactsPermissionState
import app.logdate.feature.core.account.navigation.cloudAccountSetupRoute
import app.logdate.feature.core.account.navigation.navigateToCloudAccountSetup
import app.logdate.feature.core.people.ui.PeopleDirectoryScreen
import app.logdate.feature.core.people.ui.PeopleInboxScreen
import app.logdate.feature.core.people.ui.PeopleSettingsScreen
import app.logdate.feature.core.people.ui.PersonDetailScreen
import app.logdate.feature.core.profile.navigation.ProfileRoute
import app.logdate.feature.core.settings.ui.AccountSettingsScreen
import app.logdate.feature.core.settings.ui.AdvancedSettingsScreen
import app.logdate.feature.core.settings.ui.BirthdaySettingsScreen
import app.logdate.feature.core.settings.ui.ClearDataSettingsScreen
import app.logdate.feature.core.settings.ui.DataSettingsScreen
import app.logdate.feature.core.settings.ui.DayBoundarySettingsScreen
import app.logdate.feature.core.settings.ui.ExportSettingsScreen
import app.logdate.feature.core.settings.ui.LibrarySettingsScreen
import app.logdate.feature.core.settings.ui.LocationAdvancedScreen
import app.logdate.feature.core.settings.ui.LocationIntervalScreen
import app.logdate.feature.core.settings.ui.LocationSettingsScreen
import app.logdate.feature.core.settings.ui.LocationTrackingOptionsScreen
import app.logdate.feature.core.settings.ui.MemoriesSettingsScreen
import app.logdate.feature.core.settings.ui.PrivacySettingsScreen
import app.logdate.feature.core.settings.ui.RecommendationSettingsScreen
import app.logdate.feature.core.settings.ui.ResetAppSettingsScreen
import app.logdate.feature.core.settings.ui.ResetSettingsScreen
import app.logdate.feature.core.settings.ui.SettingsOverviewScreen
import app.logdate.feature.core.settings.ui.StreakSettingsScreen
import app.logdate.feature.core.settings.ui.SyncSettingsScreen
import app.logdate.feature.core.settings.ui.TimelineSettingsScreen
import app.logdate.feature.core.settings.ui.VoiceNotesSettingsScreen
import app.logdate.feature.core.settings.ui.devices.DevicesScreen
import app.logdate.feature.events.ui.calendar.EventsCalendarScreen
import app.logdate.feature.events.ui.calendarsync.CalendarSyncActivityScreen
import app.logdate.feature.events.ui.calendarsync.CalendarSyncCalendarsScreen
import app.logdate.feature.events.ui.calendarsync.CalendarSyncSettingsScreen
import app.logdate.feature.events.ui.settings.EventsSettingsScreen
import app.logdate.feature.rewind.ui.settings.RewindSettingsScreen
import kotlin.uuid.Uuid

/**
 * Registers all settings routes in the common navigation graph.
 *
 * Mirrors Android's `appSettingsRoutes` callback surface so iOS and desktop expose the same
 * settings hierarchy. Wear OS, Health Connect, and system notifications are platform-specific
 * and surface as `null` callbacks (which the overview hides) on iOS/desktop.
 */
fun NavGraphBuilder.settingsGraph(navController: NavController) {
    composable<SettingsRoute> {
        SettingsOverviewScreen(
            onBack = { navController.popBackStack() },
            onNavigateToProfile = { navController.navigate(ProfileRoute) },
            onNavigateToAccount = { navController.navigate(AccountSettingsRoute) },
            onNavigateToDevices = { navController.navigate(DevicesRoute()) },
            // Wear OS not available on iOS/desktop — null hides the tile entirely.
            onNavigateToWatch = null,
            onNavigateToReset = { navController.navigate(ResetSettingsRoute) },
            onNavigateToLocation = { navController.navigate(LocationSettingsRoute) },
            onNavigateToPrivacy = { navController.navigate(PrivacySettingsRoute) },
            onNavigateToLibrarySettings = { navController.navigate(LibrarySettingsRoute) },
            onNavigateToMemories = { navController.navigate(MemoriesSettingsRoute) },
            onNavigateToVoiceNotes = { navController.navigate(VoiceNotesSettingsRoute) },
            onNavigateToNotifications = null,
            onNavigateToStreaks = { navController.navigate(StreakSettingsRoute) },
            onNavigateToRewindSettings = { navController.navigate(RewindSettingsRoute) },
            onNavigateToEventsSettings = { navController.navigate(EventsSettingsRoute) },
            onNavigateToPeopleSettings = { navController.navigate(PeopleSettingsRoute) },
            onNavigateToTimeline = { navController.navigate(TimelineSettingsRoute) },
            onNavigateToSync = { navController.navigate(SyncSettingsRoute) },
            onNavigateToExport = { navController.navigate(ExportSettingsRoute) },
            onNavigateToCloudAccountCreation = {
                navController.navigateToCloudAccountSetup(startOnSignIn = false)
            },
            onNavigateToSignIn = {
                navController.navigateToCloudAccountSetup(startOnSignIn = true)
            },
        )
    }
    cloudAccountSetupRoute(
        onAccountCreated = { navController.popBackStack() },
        onSkipped = { navController.popBackStack() },
        onBack = { navController.popBackStack() },
    )
    composable<AccountSettingsRoute> {
        AccountSettingsScreen(
            onBack = { navController.popBackStack() },
        )
    }
    composable<PrivacySettingsRoute> {
        PrivacySettingsScreen(
            onBack = { navController.popBackStack() },
            onNavigateToLocationSettings = { navController.navigate(LocationSettingsRoute) },
        )
    }
    composable<DataSettingsRoute> {
        DataSettingsScreen(
            onBack = { navController.popBackStack() },
        )
    }
    composable<AdvancedSettingsRoute> {
        AdvancedSettingsScreen(
            onBack = { navController.popBackStack() },
        )
    }
    composable<LocationSettingsRoute> {
        LocationSettingsScreen(
            onBack = { navController.popBackStack() },
            onOpenLocationTimeline = { /* Surfaced from the home tab on iOS/desktop, not the overview. */ },
            onNavigateToTrackingOptions = { navController.navigate(LocationTrackingOptionsRoute) },
            onNavigateToInterval = { navController.navigate(LocationIntervalRoute) },
            onNavigateToAdvanced = { navController.navigate(LocationAdvancedRoute) },
        )
    }
    composable<LocationTrackingOptionsRoute> {
        LocationTrackingOptionsScreen(
            onBack = { navController.popBackStack() },
        )
    }
    composable<LocationIntervalRoute> {
        LocationIntervalScreen(
            onBack = { navController.popBackStack() },
        )
    }
    composable<LocationAdvancedRoute> {
        LocationAdvancedScreen(
            onBack = { navController.popBackStack() },
        )
    }
    composable<MemoriesSettingsRoute> {
        MemoriesSettingsScreen(
            onBack = { navController.popBackStack() },
            onNavigateToRecommendations = { navController.navigate(RecommendationSettingsRoute) },
        )
    }
    composable<VoiceNotesSettingsRoute> {
        VoiceNotesSettingsScreen(
            onBack = { navController.popBackStack() },
        )
    }
    composable<StreakSettingsRoute> {
        StreakSettingsScreen(
            onBack = { navController.popBackStack() },
        )
    }
    composable<TimelineSettingsRoute> {
        TimelineSettingsScreen(
            onBack = { navController.popBackStack() },
            onNavigateToDayBoundary = { navController.navigate(DayBoundarySettingsRoute) },
        )
    }
    composable<DayBoundarySettingsRoute> {
        DayBoundarySettingsScreen(
            onBack = { navController.popBackStack() },
        )
    }
    composable<SyncSettingsRoute> {
        SyncSettingsScreen(
            onBack = { navController.popBackStack() },
            onNavigateToCloudAccountCreation = {
                navController.navigateToCloudAccountSetup(startOnSignIn = false)
            },
            onNavigateToSignIn = {
                navController.navigateToCloudAccountSetup(startOnSignIn = true)
            },
        )
    }
    composable<ExportSettingsRoute> {
        ExportSettingsScreen(
            onBack = { navController.popBackStack() },
        )
    }
    composable<LibrarySettingsRoute> {
        LibrarySettingsScreen(
            onBack = { navController.popBackStack() },
        )
    }
    composable<ResetSettingsRoute> {
        ResetSettingsScreen(
            onBack = { navController.popBackStack() },
            onNavigateToClearData = { navController.navigate(ClearDataSettingsRoute) },
            onNavigateToResetApp = { navController.navigate(ResetAppSettingsRoute) },
        )
    }
    composable<ClearDataSettingsRoute> {
        ClearDataSettingsScreen(
            onBack = { navController.popBackStack() },
        )
    }
    composable<ResetAppSettingsRoute> {
        ResetAppSettingsScreen(
            onBack = { navController.popBackStack() },
            onAppReset = {
                // After reset, navigate back to root
                navController.popBackStack(SettingsRoute(), inclusive = true)
            },
        )
    }
    composable<BirthdaySettingsRoute> {
        BirthdaySettingsScreen(
            onBack = { navController.popBackStack() },
        )
    }
    composable<RecommendationSettingsRoute> {
        RecommendationSettingsScreen(
            onBack = { navController.popBackStack() },
        )
    }
    composable<DevicesRoute> {
        DevicesScreen(
            onBackClick = { navController.popBackStack() },
        )
    }
    composable<RewindSettingsRoute> {
        RewindSettingsScreen(
            onBack = { navController.popBackStack() },
        )
    }
    composable<EventsSettingsRoute> {
        EventsSettingsScreen(
            onBack = { navController.popBackStack() },
            onNavigateToCalendar = { navController.navigate(EventsCalendarRoute) },
            onNavigateToCalendarSync = { navController.navigate(CalendarSyncSettingsRoute) },
        )
    }
    composable<EventsCalendarRoute> {
        EventsCalendarScreen(
            onBack = { navController.popBackStack() },
            onNavigateToEvent = { eventId ->
                // Hand the detail navigation back to the root NavHost which registers eventDetailRoute.
                navController.navigate(
                    app.logdate.feature.events.navigation.EventDetailRoute(eventId.toString()),
                )
            },
        )
    }
    composable<CalendarSyncSettingsRoute> {
        CalendarSyncSettingsScreen(
            onBack = { navController.popBackStack() },
            onNavigateToCalendars = { navController.navigate(CalendarSyncCalendarsRoute) },
            onNavigateToActivity = { navController.navigate(CalendarSyncActivityRoute) },
        )
    }
    composable<CalendarSyncCalendarsRoute> {
        CalendarSyncCalendarsScreen(
            onBack = { navController.popBackStack() },
        )
    }
    composable<CalendarSyncActivityRoute> {
        CalendarSyncActivityScreen(
            onBack = { navController.popBackStack() },
            onNavigateToEvent = { eventId ->
                navController.navigate(
                    app.logdate.feature.events.navigation.EventDetailRoute(eventId.toString()),
                )
            },
        )
    }
    composable<PeopleSettingsRoute> {
        val contactsPermissionState = rememberContactsPermissionState()
        PeopleSettingsScreen(
            onBack = { navController.popBackStack() },
            onBrowsePeople = { navController.navigate(PeopleDirectoryRoute) },
            onOpenReviewInbox = { navController.navigate(PeopleInboxRoute) },
            contactsPermissionState = contactsPermissionState,
            // Selected-contacts import is Android-only (relies on the system contacts picker
            // contract). On iOS/desktop the in-app permission grant alone enables people
            // discovery from already-attributed entries.
            onImportSelectedContacts = {},
        )
    }
    composable<PeopleDirectoryRoute> {
        PeopleDirectoryScreen(
            onBack = { navController.popBackStack() },
            onOpenPerson = { personId -> navController.navigate(PersonDetailRoute(personId)) },
        )
    }
    composable<PeopleInboxRoute> {
        PeopleInboxScreen(
            onBack = { navController.popBackStack() },
        )
    }
    composable<PersonDetailRoute> { entry ->
        val route = entry.toRoute<PersonDetailRoute>()
        PersonDetailScreen(
            personId = Uuid.parse(route.personId),
            onBack = { navController.popBackStack() },
        )
    }
}
