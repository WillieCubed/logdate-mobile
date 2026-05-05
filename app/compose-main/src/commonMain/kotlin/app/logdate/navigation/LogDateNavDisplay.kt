@file:OptIn(ExperimentalSharedTransitionApi::class)
@file:Suppress("ktlint:standard:function-naming")

package app.logdate.navigation

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
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
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.ui.NavDisplay
import app.logdate.client.repository.search.SearchContentType
import app.logdate.client.repository.search.SearchResult
import app.logdate.client.ui.LockableContent
import app.logdate.client.ui.navigation.DeepLinkAction
import app.logdate.client.ui.navigation.DeepLinkBus
import app.logdate.client.ui.navigation.LocationTimelineRoute
import app.logdate.client.ui.navigation.SearchRoute
import app.logdate.feature.core.GlobalAppUiLoadedState
import app.logdate.feature.core.account.navigation.CloudAccountSetupRoute
import app.logdate.feature.core.account.navigation.cloudAccountSetupEntry
import app.logdate.feature.core.main.HomeRoute
import app.logdate.feature.core.main.HomeScreen
import app.logdate.feature.core.navigation.BaseRoute
import app.logdate.feature.core.profile.navigation.ProfileRoute
import app.logdate.feature.core.profile.navigation.profileEntry
import app.logdate.feature.core.requiresUnlock
import app.logdate.feature.core.settings.navigation.BirthdaySettingsRoute
import app.logdate.feature.core.settings.navigation.ExportSettingsRoute
import app.logdate.feature.core.settings.navigation.PersonDetailRoute
import app.logdate.feature.core.settings.navigation.SettingsRoute
import app.logdate.feature.core.settings.navigation.SyncSettingsRoute
import app.logdate.feature.core.settings.navigation.WatchSettingsRoute
import app.logdate.feature.core.settings.navigation.WatchTroubleshootingRoute
import app.logdate.feature.core.settings.navigation.settingsEntries
import app.logdate.feature.core.settings.navigation.watchEntries
import app.logdate.feature.core.sync.navigation.SyncIssuesRoute
import app.logdate.feature.core.sync.navigation.syncIssuesEntry
import app.logdate.feature.editor.navigation.EntryEditorRoute
import app.logdate.feature.editor.navigation.editorEntry
import app.logdate.feature.events.navigation.EventDetailRoute
import app.logdate.feature.events.navigation.eventDetailEntry
import app.logdate.feature.journals.navigation.JournalCreationRoute
import app.logdate.feature.journals.navigation.JournalDetailsRoute
import app.logdate.feature.journals.navigation.JournalSettingsRoute
import app.logdate.feature.journals.navigation.JournalsOverviewRoute
import app.logdate.feature.journals.navigation.NoteDetailRoute
import app.logdate.feature.journals.navigation.ShareJournalRoute
import app.logdate.feature.journals.navigation.journalEntries
import app.logdate.feature.library.navigation.MediaDetailRoute
import app.logdate.feature.library.navigation.libraryEntries
import app.logdate.feature.library.ui.LibraryScreen
import app.logdate.feature.location.timeline.ui.LocationTimelineScreen
import app.logdate.feature.onboarding.navigation.onboardingEntries
import app.logdate.feature.postcards.navigation.PostcardEditorRoute
import app.logdate.feature.postcards.navigation.PostcardViewerRoute
import app.logdate.feature.postcards.navigation.postcardsEntries
import app.logdate.feature.rewind.navigation.RewindDetailRoute
import app.logdate.feature.rewind.navigation.rewindDetailEntry
import app.logdate.feature.search.ui.SearchScreen
import app.logdate.navigation.scenes.HomeSceneStrategy
import app.logdate.navigation.scenes.supportsDualPaneHomeScene
import app.logdate.ui.LocalSharedTransitionScope
import app.logdate.ui.audio.AudioPlaybackProvider
import app.logdate.ui.navigation.taggedEntry
import app.logdate.ui.theme.LogDateTheme
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Multiplatform Navigation 3 root for the LogDate app.
 *
 * Each feature module exposes its own `xEntry()` extensions on `EntryProviderScope<NavKey>`
 * so this root never imports a screen composable directly — the modular navigation contract
 * lives in the feature modules themselves. Routes that don't yet have a per-module helper
 * are registered inline via [taggedEntry]; tagging is required so [HomeSceneStrategy] can
 * recover the route type from `NavEntry.metadata`.
 *
 * Every concrete `NavKey` subtype must be registered in [appNavSavedStateConfiguration] for
 * save-state to round-trip on iOS / web.
 */
@Suppress("ktlint:standard:function-naming")
@Composable
fun LogDateNavDisplay(
    appUiState: GlobalAppUiLoadedState,
    onShowUnlockPrompt: () -> Unit,
    pendingNavKey: NavKey? = null,
    onPendingNavKeyConsumed: () -> Unit = {},
    onCurrentNavKeyChanged: (NavKey?) -> Unit = {},
    sceneStrategy: SceneStrategy<NavKey> = rememberHomeSceneStrategy(),
) {
    val backStack = rememberNavBackStack(appNavSavedStateConfiguration, BaseRoute)
    var hasRequestedUnlock by remember { mutableStateOf(false) }

    LaunchedEffect(appUiState.isOnboarded, appUiState.requiresUnlock) {
        if (!appUiState.isOnboarded) {
            backStack.clear()
            backStack.add(app.logdate.feature.onboarding.navigation.OnboardingStart)
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
                            sceneStrategies = listOf(sceneStrategy),
                            entryProvider =
                                entryProvider {
                                    taggedEntry<BaseRoute> { /* loading placeholder */ }
                                    taggedEntry<HomeRoute> {
                                        HomeScreen(
                                            onNewEntry = { backStack.add(EntryEditorRoute()) },
                                            onOpenJournal = { backStack.add(JournalDetailsRoute(it)) },
                                            onCreateJournal = { backStack.add(JournalCreationRoute()) },
                                            onBrowseJournals = { backStack.add(JournalsOverviewRoute) },
                                            onOpenRewind = { backStack.add(RewindDetailRoute(it)) },
                                            onOpenSettings = { backStack.add(SettingsRoute()) },
                                            onOpenSearch = { backStack.add(SearchRoute()) },
                                            onOpenDraft = { draftId ->
                                                backStack.add(EntryEditorRoute(draftId = draftId))
                                            },
                                            onImportBackup = { backStack.add(ExportSettingsRoute) },
                                            onOpenMediaDetail = { backStack.add(MediaDetailRoute(it)) },
                                            onOpenSyncIssues = { backStack.add(SyncIssuesRoute) },
                                            onOpenDay = { date -> backStack.add(TimelineDetailRoute(date.toString())) },
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
                                    syncIssuesEntry(onBack = { backStack.removeLastOrNull() })
                                    journalEntries(
                                        onOpenJournal = { backStack.add(JournalDetailsRoute(it)) },
                                        onCreateJournal = { backStack.add(JournalCreationRoute()) },
                                        onBack = { backStack.removeLastOrNull() },
                                        onJournalDeleted = {
                                            backStack.clear()
                                            backStack.add(JournalsOverviewRoute)
                                        },
                                        onOpenNote = { backStack.add(NoteDetailRoute(it)) },
                                        onOpenEditorForJournal = { journalId ->
                                            backStack.add(EntryEditorRoute(journalIds = listOf(journalId.toString())))
                                        },
                                        onOpenJournalSettings = { backStack.add(JournalSettingsRoute(it)) },
                                        onShareJournal = { backStack.add(ShareJournalRoute(it)) },
                                    )
                                    libraryEntries(
                                        onOpenMediaDetail = { backStack.add(MediaDetailRoute(it)) },
                                        onBack = { backStack.removeLastOrNull() },
                                    )
                                    editorEntry(
                                        onNavigateBack = { backStack.removeLastOrNull() },
                                        onEntrySaved = {
                                            backStack.clear()
                                            backStack.add(HomeRoute)
                                        },
                                    )
                                    eventDetailEntry(onGoBack = { backStack.removeLastOrNull() })
                                    profileEntry(
                                        onBack = { backStack.removeLastOrNull() },
                                        onNavigateToBirthday = { backStack.add(BirthdaySettingsRoute) },
                                    )
                                    rewindDetailEntry(onExitRewind = { backStack.removeLastOrNull() })
                                    settingsEntries(
                                        onBack = { backStack.removeLastOrNull() },
                                        onResetApp = {
                                            backStack.clear()
                                            backStack.add(BaseRoute)
                                        },
                                        onNavigateToProfile = { backStack.add(ProfileRoute) },
                                        onNavigateToCloudAccountCreation = {
                                            backStack.add(CloudAccountSetupRoute(startOnSignIn = false))
                                        },
                                        onNavigateToSignIn = {
                                            backStack.add(CloudAccountSetupRoute(startOnSignIn = true))
                                        },
                                        onNavigateToEvent = { backStack.add(EventDetailRoute(it.toString())) },
                                        onNavigateToWatch =
                                            if (isWatchSupported) {
                                                { backStack.add(WatchSettingsRoute) }
                                            } else {
                                                null
                                            },
                                        onNavigateTo = { route -> backStack.add(route) },
                                    )
                                    if (isWatchSupported) {
                                        watchEntries(
                                            onBack = { backStack.removeLastOrNull() },
                                            onNavigateToSync = { backStack.add(SyncSettingsRoute) },
                                            onNavigateToNotifications = { backStack.add(SettingsRoute()) },
                                            onNavigateToTroubleshooting = { backStack.add(WatchTroubleshootingRoute) },
                                        )
                                    }
                                    cloudAccountSetupEntry(
                                        onAccountCreated = { backStack.removeLastOrNull() },
                                        onSkipped = { backStack.removeLastOrNull() },
                                        onBack = { backStack.removeLastOrNull() },
                                    )
                                    onboardingEntries(
                                        onNavigateBack = { backStack.removeLastOrNull() },
                                        onWelcomeBack = {
                                            backStack.clear()
                                            backStack.add(HomeRoute)
                                        },
                                        onOnboardingComplete = {
                                            backStack.clear()
                                            backStack.add(HomeRoute)
                                        },
                                        onGoToItem = { route -> backStack.add(route) },
                                    )
                                    postcardsEntries(
                                        onOpenPostcard = { backStack.add(PostcardViewerRoute(it)) },
                                        onEditPostcard = { backStack.add(PostcardEditorRoute(it)) },
                                        onCreateNew = { backStack.add(PostcardEditorRoute()) },
                                        onNavigateBack = { backStack.removeLastOrNull() },
                                        onSaved = { backStack.removeLastOrNull() },
                                    )
                                    // Search
                                    taggedEntry<SearchRoute> { route ->
                                        SearchScreen(
                                            onGoBack = { backStack.removeLastOrNull() },
                                            onResultClick = { result -> backStack.add(searchResultRoute(result)) },
                                            onResultOpenDay = { result -> backStack.add(searchResultDayRoute(result)) },
                                            initialQuery = route.query,
                                            initialTypeFtsValues = route.typeFtsValues,
                                            initialDateRangeName = route.dateRangeName,
                                        )
                                    }
                                    // Location timeline (top-level)
                                    taggedEntry<LocationTimelineRoute> {
                                        LocationTimelineScreen(
                                            onOpenNote = { backStack.add(NoteDetailRoute(it)) },
                                        )
                                    }
                                    // Timeline per-day detail
                                    taggedEntry<TimelineDetailRoute> { route ->
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
 * Default [SceneStrategy] for the LogDate graph. Activates [HomeSceneStrategy]'s two-pane
 * layout when the current window is wide enough; otherwise the strategy returns `null` and
 * `NavDisplay` falls back to its single-pane default.
 */
@Composable
private fun rememberHomeSceneStrategy(): SceneStrategy<NavKey> {
    val supportsDualPane = currentWindowAdaptiveInfo().windowSizeClass.supportsDualPaneHomeScene()
    return remember(supportsDualPane) {
        HomeSceneStrategy { supportsDualPane }
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

/**
 * Dispatches a search-result tap to the most specific entry-detail route available, falling back
 * to the containing day for content types without their own detail screen.
 */
private fun searchResultRoute(result: SearchResult): NavKey =
    when (result.contentType) {
        SearchContentType.JOURNAL -> JournalDetailsRoute(result.uid)
        SearchContentType.PERSON -> PersonDetailRoute(result.uid)
        SearchContentType.TEXT_NOTE -> NoteDetailRoute(result.uid)
        SearchContentType.POSTCARD -> PostcardViewerRoute(result.uid)
        SearchContentType.REWIND -> RewindDetailRoute(result.uid)
        SearchContentType.MEDIA_CAPTION -> MediaDetailRoute(result.uid)
        SearchContentType.TRANSCRIPTION,
        SearchContentType.AMBIENT_SOUND,
        SearchContentType.STICKER,
        SearchContentType.PLACE,
        -> searchResultDayRoute(result)
    }

/**
 * Resolves a search result's containing day route. Used both as the fallback in
 * [searchResultRoute] and by the long-press "Open day view" action.
 */
private fun searchResultDayRoute(result: SearchResult): TimelineDetailRoute {
    val date =
        result.created
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date
    return TimelineDetailRoute(date.toString())
}
