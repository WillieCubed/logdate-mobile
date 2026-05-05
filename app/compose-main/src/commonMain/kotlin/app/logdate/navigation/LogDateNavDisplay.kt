@file:OptIn(ExperimentalSharedTransitionApi::class)
@file:Suppress("ktlint:standard:function-naming")

package app.logdate.navigation

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import app.logdate.client.permissions.rememberContactsPermissionState
import app.logdate.client.ui.LockableContent
import app.logdate.client.ui.navigation.DeepLinkAction
import app.logdate.client.ui.navigation.DeepLinkBus
import app.logdate.client.ui.navigation.LocationTimelineRoute
import app.logdate.client.ui.navigation.SearchRoute
import app.logdate.feature.core.GlobalAppUiLoadedState
import app.logdate.feature.core.account.CloudAccountOnboardingScreen
import app.logdate.feature.core.account.CloudAccountOnboardingViewModel
import app.logdate.feature.core.account.navigation.CloudAccountSetupRoute
import app.logdate.feature.core.main.HomeRoute
import app.logdate.feature.core.main.HomeScreen
import app.logdate.feature.core.navigation.BaseRoute
import app.logdate.feature.core.people.ui.PeopleDirectoryScreen
import app.logdate.feature.core.people.ui.PeopleInboxScreen
import app.logdate.feature.core.people.ui.PeopleSettingsScreen
import app.logdate.feature.core.people.ui.PersonDetailScreen
import app.logdate.feature.core.profile.navigation.ProfileRoute
import app.logdate.feature.core.profile.ui.ProfileScreen
import app.logdate.feature.core.requiresUnlock
import app.logdate.feature.core.settings.navigation.AccountSettingsRoute
import app.logdate.feature.core.settings.navigation.AdvancedSettingsRoute
import app.logdate.feature.core.settings.navigation.BirthdaySettingsRoute
import app.logdate.feature.core.settings.navigation.CalendarSyncActivityRoute
import app.logdate.feature.core.settings.navigation.CalendarSyncCalendarsRoute
import app.logdate.feature.core.settings.navigation.CalendarSyncSettingsRoute
import app.logdate.feature.core.settings.navigation.ClearDataSettingsRoute
import app.logdate.feature.core.settings.navigation.DataSettingsRoute
import app.logdate.feature.core.settings.navigation.DayBoundarySettingsRoute
import app.logdate.feature.core.settings.navigation.DevicesRoute
import app.logdate.feature.core.settings.navigation.EventsCalendarRoute
import app.logdate.feature.core.settings.navigation.EventsSettingsRoute
import app.logdate.feature.core.settings.navigation.ExportSettingsRoute
import app.logdate.feature.core.settings.navigation.LibrarySettingsRoute
import app.logdate.feature.core.settings.navigation.LocationAdvancedRoute
import app.logdate.feature.core.settings.navigation.LocationIntervalRoute
import app.logdate.feature.core.settings.navigation.LocationSettingsRoute
import app.logdate.feature.core.settings.navigation.LocationTrackingOptionsRoute
import app.logdate.feature.core.settings.navigation.MemoriesSettingsRoute
import app.logdate.feature.core.settings.navigation.PeopleDirectoryRoute
import app.logdate.feature.core.settings.navigation.PeopleInboxRoute
import app.logdate.feature.core.settings.navigation.PeopleSettingsRoute
import app.logdate.feature.core.settings.navigation.PersonDetailRoute
import app.logdate.feature.core.settings.navigation.PrivacySettingsRoute
import app.logdate.feature.core.settings.navigation.RecommendationSettingsRoute
import app.logdate.feature.core.settings.navigation.ResetAppSettingsRoute
import app.logdate.feature.core.settings.navigation.ResetSettingsRoute
import app.logdate.feature.core.settings.navigation.RewindSettingsRoute
import app.logdate.feature.core.settings.navigation.SettingsRoute
import app.logdate.feature.core.settings.navigation.StreakSettingsRoute
import app.logdate.feature.core.settings.navigation.SyncSettingsRoute
import app.logdate.feature.core.settings.navigation.TimelineSettingsRoute
import app.logdate.feature.core.settings.navigation.VoiceNotesSettingsRoute
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
import app.logdate.feature.core.sync.SyncIssuesScreen
import app.logdate.feature.core.sync.navigation.SyncIssuesRoute
import app.logdate.feature.editor.navigation.EntryEditorRoute
import app.logdate.feature.editor.ui.NoteEditorScreen
import app.logdate.feature.events.navigation.EventDetailRoute
import app.logdate.feature.events.ui.EventDetailScreen
import app.logdate.feature.events.ui.calendar.EventsCalendarScreen
import app.logdate.feature.events.ui.calendarsync.CalendarSyncActivityScreen
import app.logdate.feature.events.ui.calendarsync.CalendarSyncCalendarsScreen
import app.logdate.feature.events.ui.calendarsync.CalendarSyncSettingsScreen
import app.logdate.feature.events.ui.settings.EventsSettingsScreen
import app.logdate.feature.journals.navigation.JournalCreationRoute
import app.logdate.feature.journals.navigation.JournalDetailsRoute
import app.logdate.feature.journals.navigation.JournalSettingsRoute
import app.logdate.feature.journals.navigation.JournalsOverviewRoute
import app.logdate.feature.journals.navigation.NoteDetailRoute
import app.logdate.feature.journals.navigation.ShareJournalRoute
import app.logdate.feature.journals.ui.JournalsOverviewScreen
import app.logdate.feature.journals.ui.creation.JournalCreationScreen
import app.logdate.feature.journals.ui.detail.JournalDetailScreen
import app.logdate.feature.journals.ui.detail.NoteViewerScreen
import app.logdate.feature.journals.ui.settings.JournalSettingsScreen
import app.logdate.feature.journals.ui.share.ShareJournalScreen
import app.logdate.feature.library.navigation.LibraryOverviewRoute
import app.logdate.feature.library.navigation.MediaDetailRoute
import app.logdate.feature.library.ui.LibraryScreen
import app.logdate.feature.library.ui.detail.MediaDetailScreen
import app.logdate.feature.location.timeline.ui.LocationTimelineScreen
import app.logdate.feature.postcards.navigation.PostcardEditorRoute
import app.logdate.feature.postcards.navigation.PostcardViewerRoute
import app.logdate.feature.postcards.navigation.PostcardsCollectionRoute
import app.logdate.feature.postcards.ui.CanvasEditorScreen
import app.logdate.feature.postcards.ui.PostcardViewerScreen
import app.logdate.feature.postcards.ui.PostcardsCollectionScreen
import app.logdate.feature.rewind.navigation.RewindDetailRoute
import app.logdate.feature.rewind.ui.detail.RewindDetailScreen
import app.logdate.feature.search.ui.SearchScreen
import app.logdate.ui.LocalSharedTransitionScope
import app.logdate.ui.audio.AudioPlaybackProvider
import app.logdate.ui.theme.LogDateTheme
import kotlinx.datetime.LocalDate
import kotlin.uuid.Uuid
import app.logdate.feature.core.account.OnboardingStep as CloudAccountStep

/**
 * Multiplatform Navigation 3 root for the LogDate app.
 *
 * Mirrors `LogDateNavHost.kt` (which uses `androidx.navigation.compose`) but built on top of
 * `org.jetbrains.androidx.navigation3` so iOS, desktop, and Android can share a single graph.
 * Every concrete `NavKey` subtype must be registered in
 * [appNavSavedStateConfiguration] for save-state to round-trip on iOS / web.
 *
 * The legacy `LogDateNavHost` is still present and consumed by the existing iOS root view
 * controller; this is the parallel future-graph until the per-platform entry points cut over.
 */
@Suppress("ktlint:standard:function-naming")
@Composable
fun LogDateNavDisplay(
    appUiState: GlobalAppUiLoadedState,
    onShowUnlockPrompt: () -> Unit,
    pendingNavKey: NavKey? = null,
    onPendingNavKeyConsumed: () -> Unit = {},
    onCurrentNavKeyChanged: (NavKey?) -> Unit = {},
) {
    val backStack = rememberNavBackStack(appNavSavedStateConfiguration, BaseRoute)
    var hasRequestedUnlock by remember { mutableStateOf(false) }

    LaunchedEffect(appUiState.isOnboarded, appUiState.requiresUnlock) {
        if (!appUiState.isOnboarded) {
            backStack.clear()
            backStack.add(app.logdate.feature.onboarding.navigation.OnboardingRoute)
            return@LaunchedEffect
        }
        if (appUiState.requiresUnlock) {
            if (!hasRequestedUnlock) {
                hasRequestedUnlock = true
                onShowUnlockPrompt()
            }
            return@LaunchedEffect
        }
        hasRequestedUnlock = false
        if (backStack.lastOrNull() != HomeRoute) {
            backStack.clear()
            backStack.add(HomeRoute)
        }
    }

    LaunchedEffect(pendingNavKey, appUiState.isOnboarded, appUiState.requiresUnlock) {
        val target = pendingNavKey ?: return@LaunchedEffect
        if (!appUiState.isOnboarded || appUiState.requiresUnlock) return@LaunchedEffect
        backStack.add(target)
        onPendingNavKeyConsumed()
    }

    LaunchedEffect(backStack.lastOrNull()) {
        onCurrentNavKeyChanged(backStack.lastOrNull())
    }

    LaunchedEffect(backStack, appUiState.isOnboarded, appUiState.requiresUnlock) {
        if (!appUiState.isOnboarded || appUiState.requiresUnlock) return@LaunchedEffect
        DeepLinkBus.actions.collect { action ->
            when (action) {
                is DeepLinkAction.OpenJournal -> backStack.add(JournalDetailsRoute(action.id))
                is DeepLinkAction.OpenNote -> backStack.add(NoteDetailRoute(action.id))
                is DeepLinkAction.OpenRewind -> backStack.add(RewindDetailRoute(action.id))
                DeepLinkAction.OpenLocationTimeline -> backStack.add(LocationTimelineRoute)
            }
        }
    }

    LogDateTheme {
        SharedTransitionLayout {
            CompositionLocalProvider(LocalSharedTransitionScope provides this) {
                LockableContent(
                    isLocked = appUiState.requiresUnlock,
                    displayName = appUiState.displayName,
                    onUsePasscode = onShowUnlockPrompt,
                ) {
                    AudioPlaybackProvider {
                        NavDisplay(
                            backStack = backStack,
                            onBack = { backStack.removeLastOrNull() },
                            entryProvider =
                                entryProvider {
                                    entry<BaseRoute> { /* loading placeholder */ }
                                    entry<HomeRoute> {
                                        HomeScreen(
                                            onNewEntry = { backStack.add(EntryEditorRoute()) },
                                            onOpenJournal = { backStack.add(JournalDetailsRoute(it)) },
                                            onCreateJournal = { backStack.add(JournalCreationRoute()) },
                                            onBrowseJournals = { backStack.add(JournalsOverviewRoute) },
                                            onOpenRewind = { backStack.add(RewindDetailRoute(it)) },
                                            onOpenSettings = { backStack.add(SettingsRoute()) },
                                            onOpenSearch = { backStack.add(SearchRoute) },
                                            onOpenDraft = { draftId ->
                                                backStack.add(EntryEditorRoute(draftId = draftId))
                                            },
                                            onImportBackup = { backStack.add(ExportSettingsRoute) },
                                            onOpenMediaDetail = { backStack.add(MediaDetailRoute(it)) },
                                            onOpenSyncIssues = { backStack.add(SyncIssuesRoute) },
                                            libraryContent = { modifier ->
                                                LibraryScreen(
                                                    onOpenMediaDetail = { backStack.add(MediaDetailRoute(it)) },
                                                    modifier = modifier,
                                                )
                                            },
                                            locationContent = { modifier ->
                                                LocationTimelineScreen(
                                                    onOpenNote = { backStack.add(NoteDetailRoute(it)) },
                                                    modifier = modifier,
                                                )
                                            },
                                        )
                                    }
                                    entry<SyncIssuesRoute> {
                                        SyncIssuesScreen(
                                            onGoBack = { backStack.removeLastOrNull() },
                                        )
                                    }
                                    entry<JournalsOverviewRoute> {
                                        JournalsOverviewScreen(
                                            onOpenJournal = { backStack.add(JournalDetailsRoute(it)) },
                                            onBrowseJournals = {},
                                            onCreateJournal = { backStack.add(JournalCreationRoute()) },
                                        )
                                    }
                                    entry<JournalDetailsRoute> { route ->
                                        JournalDetailScreen(
                                            journalId = Uuid.parse(route.journalId),
                                            onGoBack = { backStack.removeLastOrNull() },
                                            onJournalDeleted = {
                                                backStack.clear()
                                                backStack.add(JournalsOverviewRoute)
                                            },
                                            onNavigateToNoteDetail = { backStack.add(NoteDetailRoute(it)) },
                                            onOpenEditor = { journalId ->
                                                backStack.add(EntryEditorRoute(journalIds = listOf(journalId.toString())))
                                            },
                                            onNavigateToSettings = { backStack.add(JournalSettingsRoute(it)) },
                                            onNavigateToShare = { backStack.add(ShareJournalRoute(it)) },
                                        )
                                    }
                                    entry<JournalSettingsRoute> { route ->
                                        JournalSettingsScreen(
                                            journalId = Uuid.parse(route.journalId),
                                            onGoBack = { backStack.removeLastOrNull() },
                                            onJournalDeleted = {
                                                backStack.clear()
                                                backStack.add(JournalsOverviewRoute)
                                            },
                                        )
                                    }
                                    entry<JournalCreationRoute> {
                                        JournalCreationScreen(
                                            onGoBack = { backStack.removeLastOrNull() },
                                            onJournalCreated = { id -> backStack.add(JournalDetailsRoute(id)) },
                                        )
                                    }
                                    entry<ShareJournalRoute> { route ->
                                        ShareJournalScreen(
                                            journalId = route.journalId,
                                            onGoBack = { backStack.removeLastOrNull() },
                                        )
                                    }
                                    entry<NoteDetailRoute> { route ->
                                        NoteViewerScreen(
                                            noteId = Uuid.parse(route.noteId),
                                            onGoBack = { backStack.removeLastOrNull() },
                                        )
                                    }
                                    entry<LibraryOverviewRoute> {
                                        LibraryScreen(
                                            onOpenMediaDetail = { backStack.add(MediaDetailRoute(it)) },
                                        )
                                    }
                                    entry<MediaDetailRoute> { route ->
                                        MediaDetailScreen(
                                            mediaId = Uuid.parse(route.id),
                                            onBack = { backStack.removeLastOrNull() },
                                        )
                                    }
                                    entry<EntryEditorRoute> { route ->
                                        NoteEditorScreen(
                                            onNavigateBack = { backStack.removeLastOrNull() },
                                            onEntrySaved = {
                                                backStack.clear()
                                                backStack.add(HomeRoute)
                                            },
                                            entryId = route.entryId?.let(Uuid::parse),
                                            draftId = route.draftId?.let(Uuid::parse),
                                            journalIds = route.journalIds.map(Uuid::parse),
                                        )
                                    }
                                    entry<EventDetailRoute> { route ->
                                        EventDetailScreen(
                                            eventId = Uuid.parse(route.eventId),
                                            onGoBack = { backStack.removeLastOrNull() },
                                        )
                                    }
                                    entry<ProfileRoute> {
                                        ProfileScreen(
                                            onBack = { backStack.removeLastOrNull() },
                                            onNavigateToBirthday = { backStack.add(BirthdaySettingsRoute) },
                                        )
                                    }
                                    entry<RewindDetailRoute> { route ->
                                        RewindDetailScreen(
                                            rewindId = Uuid.parse(route.id),
                                            onExitRewind = { backStack.removeLastOrNull() },
                                        )
                                    }
                                    // Settings
                                    entry<SettingsRoute> {
                                        SettingsOverviewScreen(
                                            onBack = { backStack.removeLastOrNull() },
                                            onNavigateToProfile = { backStack.add(ProfileRoute) },
                                            onNavigateToAccount = { backStack.add(AccountSettingsRoute) },
                                            onNavigateToDevices = { backStack.add(DevicesRoute()) },
                                            onNavigateToWatch = null,
                                            onNavigateToReset = { backStack.add(ResetSettingsRoute) },
                                            onNavigateToLocation = { backStack.add(LocationSettingsRoute) },
                                            onNavigateToPrivacy = { backStack.add(PrivacySettingsRoute) },
                                            onNavigateToLibrarySettings = { backStack.add(LibrarySettingsRoute) },
                                            onNavigateToMemories = { backStack.add(MemoriesSettingsRoute) },
                                            onNavigateToVoiceNotes = { backStack.add(VoiceNotesSettingsRoute) },
                                            onNavigateToNotifications = null,
                                            onNavigateToStreaks = { backStack.add(StreakSettingsRoute) },
                                            onNavigateToRewindSettings = { backStack.add(RewindSettingsRoute) },
                                            onNavigateToEventsSettings = { backStack.add(EventsSettingsRoute) },
                                            onNavigateToPeopleSettings = { backStack.add(PeopleSettingsRoute) },
                                            onNavigateToTimeline = { backStack.add(TimelineSettingsRoute) },
                                            onNavigateToSync = { backStack.add(SyncSettingsRoute) },
                                            onNavigateToExport = { backStack.add(ExportSettingsRoute) },
                                            onNavigateToCloudAccountCreation = {
                                                backStack.add(CloudAccountSetupRoute(startOnSignIn = false))
                                            },
                                            onNavigateToSignIn = {
                                                backStack.add(CloudAccountSetupRoute(startOnSignIn = true))
                                            },
                                        )
                                    }
                                    entry<AccountSettingsRoute> {
                                        AccountSettingsScreen(onBack = { backStack.removeLastOrNull() })
                                    }
                                    entry<PrivacySettingsRoute> {
                                        PrivacySettingsScreen(
                                            onBack = { backStack.removeLastOrNull() },
                                            onNavigateToLocationSettings = { backStack.add(LocationSettingsRoute) },
                                        )
                                    }
                                    entry<DataSettingsRoute> {
                                        DataSettingsScreen(onBack = { backStack.removeLastOrNull() })
                                    }
                                    entry<AdvancedSettingsRoute> {
                                        AdvancedSettingsScreen(onBack = { backStack.removeLastOrNull() })
                                    }
                                    entry<LocationSettingsRoute> {
                                        LocationSettingsScreen(
                                            onBack = { backStack.removeLastOrNull() },
                                            onOpenLocationTimeline = {},
                                            onNavigateToTrackingOptions = { backStack.add(LocationTrackingOptionsRoute) },
                                            onNavigateToInterval = { backStack.add(LocationIntervalRoute) },
                                            onNavigateToAdvanced = { backStack.add(LocationAdvancedRoute) },
                                        )
                                    }
                                    entry<LocationTrackingOptionsRoute> {
                                        LocationTrackingOptionsScreen(onBack = { backStack.removeLastOrNull() })
                                    }
                                    entry<LocationIntervalRoute> {
                                        LocationIntervalScreen(onBack = { backStack.removeLastOrNull() })
                                    }
                                    entry<LocationAdvancedRoute> {
                                        LocationAdvancedScreen(onBack = { backStack.removeLastOrNull() })
                                    }
                                    entry<MemoriesSettingsRoute> {
                                        MemoriesSettingsScreen(
                                            onBack = { backStack.removeLastOrNull() },
                                            onNavigateToRecommendations = { backStack.add(RecommendationSettingsRoute) },
                                        )
                                    }
                                    entry<VoiceNotesSettingsRoute> {
                                        VoiceNotesSettingsScreen(onBack = { backStack.removeLastOrNull() })
                                    }
                                    entry<StreakSettingsRoute> {
                                        StreakSettingsScreen(onBack = { backStack.removeLastOrNull() })
                                    }
                                    entry<TimelineSettingsRoute> {
                                        TimelineSettingsScreen(
                                            onBack = { backStack.removeLastOrNull() },
                                            onNavigateToDayBoundary = { backStack.add(DayBoundarySettingsRoute) },
                                        )
                                    }
                                    entry<DayBoundarySettingsRoute> {
                                        DayBoundarySettingsScreen(onBack = { backStack.removeLastOrNull() })
                                    }
                                    entry<SyncSettingsRoute> {
                                        SyncSettingsScreen(
                                            onBack = { backStack.removeLastOrNull() },
                                            onNavigateToCloudAccountCreation = {
                                                backStack.add(CloudAccountSetupRoute(startOnSignIn = false))
                                            },
                                            onNavigateToSignIn = {
                                                backStack.add(CloudAccountSetupRoute(startOnSignIn = true))
                                            },
                                        )
                                    }
                                    entry<ExportSettingsRoute> {
                                        ExportSettingsScreen(onBack = { backStack.removeLastOrNull() })
                                    }
                                    entry<LibrarySettingsRoute> {
                                        LibrarySettingsScreen(onBack = { backStack.removeLastOrNull() })
                                    }
                                    entry<ResetSettingsRoute> {
                                        ResetSettingsScreen(
                                            onBack = { backStack.removeLastOrNull() },
                                            onNavigateToClearData = { backStack.add(ClearDataSettingsRoute) },
                                            onNavigateToResetApp = { backStack.add(ResetAppSettingsRoute) },
                                        )
                                    }
                                    entry<ClearDataSettingsRoute> {
                                        ClearDataSettingsScreen(onBack = { backStack.removeLastOrNull() })
                                    }
                                    entry<ResetAppSettingsRoute> {
                                        ResetAppSettingsScreen(
                                            onBack = { backStack.removeLastOrNull() },
                                            onAppReset = {
                                                backStack.clear()
                                                backStack.add(BaseRoute)
                                            },
                                        )
                                    }
                                    entry<BirthdaySettingsRoute> {
                                        BirthdaySettingsScreen(onBack = { backStack.removeLastOrNull() })
                                    }
                                    entry<RecommendationSettingsRoute> {
                                        RecommendationSettingsScreen(onBack = { backStack.removeLastOrNull() })
                                    }
                                    entry<DevicesRoute> {
                                        DevicesScreen(onBackClick = { backStack.removeLastOrNull() })
                                    }
                                    entry<RewindSettingsRoute> {
                                        app.logdate.feature.rewind.ui.settings.RewindSettingsScreen(
                                            onBack = { backStack.removeLastOrNull() },
                                        )
                                    }
                                    entry<EventsSettingsRoute> {
                                        EventsSettingsScreen(
                                            onBack = { backStack.removeLastOrNull() },
                                            onNavigateToCalendar = { backStack.add(EventsCalendarRoute) },
                                            onNavigateToCalendarSync = { backStack.add(CalendarSyncSettingsRoute) },
                                        )
                                    }
                                    entry<EventsCalendarRoute> {
                                        EventsCalendarScreen(
                                            onBack = { backStack.removeLastOrNull() },
                                            onNavigateToEvent = { backStack.add(EventDetailRoute(it.toString())) },
                                        )
                                    }
                                    entry<CalendarSyncSettingsRoute> {
                                        CalendarSyncSettingsScreen(
                                            onBack = { backStack.removeLastOrNull() },
                                            onNavigateToCalendars = { backStack.add(CalendarSyncCalendarsRoute) },
                                            onNavigateToActivity = { backStack.add(CalendarSyncActivityRoute) },
                                        )
                                    }
                                    entry<CalendarSyncCalendarsRoute> {
                                        CalendarSyncCalendarsScreen(onBack = { backStack.removeLastOrNull() })
                                    }
                                    entry<CalendarSyncActivityRoute> {
                                        CalendarSyncActivityScreen(
                                            onBack = { backStack.removeLastOrNull() },
                                            onNavigateToEvent = { backStack.add(EventDetailRoute(it.toString())) },
                                        )
                                    }
                                    entry<PeopleSettingsRoute> {
                                        val contactsPermissionState = rememberContactsPermissionState()
                                        PeopleSettingsScreen(
                                            onBack = { backStack.removeLastOrNull() },
                                            onBrowsePeople = { backStack.add(PeopleDirectoryRoute) },
                                            onOpenReviewInbox = { backStack.add(PeopleInboxRoute) },
                                            contactsPermissionState = contactsPermissionState,
                                            onImportSelectedContacts = {},
                                        )
                                    }
                                    entry<PeopleDirectoryRoute> {
                                        PeopleDirectoryScreen(
                                            onBack = { backStack.removeLastOrNull() },
                                            onOpenPerson = { backStack.add(PersonDetailRoute(it)) },
                                        )
                                    }
                                    entry<PeopleInboxRoute> {
                                        PeopleInboxScreen(onBack = { backStack.removeLastOrNull() })
                                    }
                                    entry<PersonDetailRoute> { route ->
                                        PersonDetailScreen(
                                            personId = Uuid.parse(route.personId),
                                            onBack = { backStack.removeLastOrNull() },
                                        )
                                    }
                                    // Cloud account flow (from settings/sync)
                                    entry<CloudAccountSetupRoute> { route ->
                                        val viewModel: CloudAccountOnboardingViewModel =
                                            org.koin.compose.viewmodel
                                                .koinViewModel()
                                        LaunchedEffect(route.startOnSignIn) {
                                            if (route.startOnSignIn) {
                                                viewModel.setInitialStep(CloudAccountStep.SignIn)
                                            }
                                        }
                                        CloudAccountOnboardingScreen(
                                            viewModel = viewModel,
                                            onAccountCreated = { backStack.removeLastOrNull() },
                                            onSkipOnboarding = { backStack.removeLastOrNull() },
                                            onBack = { backStack.removeLastOrNull() },
                                        )
                                    }
                                    // Postcards
                                    entry<PostcardsCollectionRoute> {
                                        PostcardsCollectionScreen(
                                            onOpenPostcard = { backStack.add(PostcardViewerRoute(it)) },
                                            onEditPostcard = { backStack.add(PostcardEditorRoute(it)) },
                                            onCreateNew = { backStack.add(PostcardEditorRoute()) },
                                        )
                                    }
                                    entry<PostcardEditorRoute> {
                                        CanvasEditorScreen(
                                            onNavigateBack = { backStack.removeLastOrNull() },
                                            onSaved = { backStack.removeLastOrNull() },
                                            onToggleFullscreen = null,
                                        )
                                    }
                                    entry<PostcardViewerRoute> {
                                        PostcardViewerScreen(
                                            onNavigateBack = { backStack.removeLastOrNull() },
                                            onEditPostcard = { backStack.add(PostcardEditorRoute(it)) },
                                            onShareUri = {},
                                            onSaveToFiles = null,
                                            onPrint = null,
                                        )
                                    }
                                    // Search
                                    entry<SearchRoute> {
                                        SearchScreen(
                                            onGoBack = { backStack.removeLastOrNull() },
                                            onNavigateToDay = { date -> backStack.add(TimelineDetailRoute(date.toString())) },
                                            onNavigateToJournal = { backStack.add(JournalDetailsRoute(it)) },
                                            onNavigateToPerson = { backStack.add(PersonDetailRoute(it)) },
                                            onNavigateToNote = { backStack.add(NoteDetailRoute(it)) },
                                            onNavigateToPostcard = { backStack.add(PostcardViewerRoute(it)) },
                                            onNavigateToRewind = { backStack.add(RewindDetailRoute(it)) },
                                            onNavigateToMedia = { backStack.add(MediaDetailRoute(it)) },
                                        )
                                    }
                                    // Location timeline (top-level)
                                    entry<LocationTimelineRoute> {
                                        LocationTimelineScreen(
                                            onOpenNote = { backStack.add(NoteDetailRoute(it)) },
                                        )
                                    }
                                    // Timeline per-day detail
                                    entry<TimelineDetailRoute> { route ->
                                        TimelineDetailEntry(
                                            date = LocalDate.parse(route.dateIso),
                                            onClose = { backStack.removeLastOrNull() },
                                            onOpenLocations = { backStack.add(LocationTimelineRoute) },
                                            onOpenEvent = { backStack.add(EventDetailRoute(it)) },
                                        )
                                    }
                                },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Wraps the existing `TimelineDayDetailPanel` (which lives in client/feature/timeline) so the
 * NavDisplay entry stays small. Mirrors the legacy `TimelineDetailScreen` from the
 * androidx.navigation.compose graph.
 */
@Composable
private fun TimelineDetailEntry(
    date: LocalDate,
    onClose: () -> Unit,
    onOpenLocations: () -> Unit,
    onOpenEvent: (eventId: String) -> Unit,
) {
    val viewModel: app.logdate.feature.core.main.HomeViewModel =
        org.koin.compose.viewmodel
            .koinViewModel()
    LaunchedEffect(date) { viewModel.selectDay(date) }
    val uiState by viewModel.uiState.collectAsState()
    uiState.selectedDay?.let { selected ->
        app.logdate.feature.timeline.ui.details.TimelineDayDetailPanel(
            uiState = selected,
            onExit = onClose,
            onOpenEvent = onOpenEvent,
            onOpenLocations = onOpenLocations,
        )
    }
}
