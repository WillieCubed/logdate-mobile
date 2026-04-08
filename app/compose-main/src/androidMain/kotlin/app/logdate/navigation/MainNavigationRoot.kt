@file:OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3AdaptiveApi::class)

package app.logdate.navigation

/**
 * # LogDate Main Navigation Root
 *
 * This file contains the core navigation setup for the LogDate app, implementing
 * Navigation 3 with adaptive UI patterns, shared element transitions, and
 * sophisticated scene management.
 *
 * ## Key Components
 *
 * - **MainNavigationRoot**: The root composable that sets up the entire navigation system
 * - **SharedTransitionLayout**: Enables shared element transitions across navigation
 * - **NavDisplay**: The core Navigation 3 container that manages scenes and transitions
 * - **Scene Strategy Chain**: Hierarchical strategy pattern for scene selection
 * - **Transition Specifications**: Material Design 3 compliant navigation animations
 *
 * ## Architecture Integration
 *
 * This navigation root integrates with:
 * - **HomeSceneStrategy**: Adaptive layouts for main app content
 * - **ListDetailSceneStrategy**: Specialized layouts for settings flows
 * - **MainAppNavigator**: Centralized navigation state management
 * - **Entry Providers**: Route definitions and content mapping
 *
 * ## Navigation Patterns
 *
 * The system supports multiple navigation patterns:
 * - **Tab-based navigation**: Bottom nav (small) / Side rail (medium+)
 * - **Hierarchical navigation**: Detail screens with proper back handling
 * - **Modal navigation**: Settings and onboarding flows
 * - **Immersive navigation**: Fullscreen content like rewind stories
 *
 * ## Transition System
 *
 * Implements comprehensive transition specifications:
 * - **Forward navigation**: Context-aware slide and fade transitions
 * - **Back navigation**: Reverse animations maintaining spatial consistency
 * - **Predictive back**: Android 13+ gesture-based back previews
 * - **Shared elements**: Seamless transitions between related content
 */

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import androidx.navigation3.ui.NavDisplay
import app.logdate.client.datastore.LogdatePreferencesDataSource
import app.logdate.client.enterPictureInPicture
import app.logdate.client.sharing.SharingLauncher
import app.logdate.feature.core.main.HomeViewModel
import app.logdate.feature.journals.ui.detail.NoteViewerScreen
import app.logdate.feature.timeline.ui.TimelineLoadingPlaceholder
import app.logdate.navigation.routes.CloudAccountSetupFlowRoute
import app.logdate.navigation.routes.appSettingsRoutes
import app.logdate.navigation.routes.cloudAccountSetupFlow
import app.logdate.navigation.routes.core.NavigationStart
import app.logdate.navigation.routes.core.NewJournalRoute
import app.logdate.navigation.routes.core.NoteViewerRoute
import app.logdate.navigation.routes.core.RewindDetailRoute
import app.logdate.navigation.routes.core.SettingsOverviewRoute
import app.logdate.navigation.routes.core.TimelineDetail
import app.logdate.navigation.routes.core.TimelineListRoute
import app.logdate.navigation.routes.core.goBack
import app.logdate.navigation.routes.core.navigateHomeFromOnboarding
import app.logdate.navigation.routes.core.openDraft
import app.logdate.navigation.routes.core.openEntryEditor
import app.logdate.navigation.routes.core.switchToTab
import app.logdate.navigation.routes.editorRoutes
import app.logdate.navigation.routes.eventRoutes
import app.logdate.navigation.routes.finishJournalCreation
import app.logdate.navigation.routes.journalRoutes
import app.logdate.navigation.routes.libraryRoutes
import app.logdate.navigation.routes.locationRoutes
import app.logdate.navigation.routes.navigateToPostcardEditor
import app.logdate.navigation.routes.navigateToPostcardViewer
import app.logdate.navigation.routes.navigateToPostcardsCollection
import app.logdate.navigation.routes.noteViewerRouteTransitionMetadata
import app.logdate.navigation.routes.onboarding
import app.logdate.navigation.routes.openAccountSettings
import app.logdate.navigation.routes.openAdvancedSettings
import app.logdate.navigation.routes.openBirthdaySettings
import app.logdate.navigation.routes.openCalendarSyncActivity
import app.logdate.navigation.routes.openCalendarSyncCalendars
import app.logdate.navigation.routes.openCalendarSyncSettings
import app.logdate.navigation.routes.openClearDataSettings
import app.logdate.navigation.routes.openDayBoundarySettings
import app.logdate.navigation.routes.openDevicesSettings
import app.logdate.navigation.routes.openEventDetail
import app.logdate.navigation.routes.openEventsSettings
import app.logdate.navigation.routes.openExportSettings
import app.logdate.navigation.routes.openJournalDetail
import app.logdate.navigation.routes.openJournalSettings
import app.logdate.navigation.routes.openLibrarySettings
import app.logdate.navigation.routes.openLocationAdvanced
import app.logdate.navigation.routes.openLocationInterval
import app.logdate.navigation.routes.openLocationSettings
import app.logdate.navigation.routes.openLocationTimeline
import app.logdate.navigation.routes.openLocationTrackingOptions
import app.logdate.navigation.routes.openMediaDetail
import app.logdate.navigation.routes.openMemoriesSettings
import app.logdate.navigation.routes.openNotificationSettings
import app.logdate.navigation.routes.openPrivacySettings
import app.logdate.navigation.routes.openProfile
import app.logdate.navigation.routes.openRecommendationSettings
import app.logdate.navigation.routes.openResetAppSettings
import app.logdate.navigation.routes.openResetSettings
import app.logdate.navigation.routes.openRewindSettings
import app.logdate.navigation.routes.openSearch
import app.logdate.navigation.routes.openSettings
import app.logdate.navigation.routes.openShareJournal
import app.logdate.navigation.routes.openStreakSettings
import app.logdate.navigation.routes.openSyncSettings
import app.logdate.navigation.routes.openTimelineDetail
import app.logdate.navigation.routes.openTimelineSettings
import app.logdate.navigation.routes.openVoiceNotesSettings
import app.logdate.navigation.routes.openWatchNotificationSettings
import app.logdate.navigation.routes.openWatchSettings
import app.logdate.navigation.routes.openWatchSyncSettings
import app.logdate.navigation.routes.openWatchTroubleshooting
import app.logdate.navigation.routes.postcardRoutes
import app.logdate.navigation.routes.resetApp
import app.logdate.navigation.routes.rewindRoutes
import app.logdate.navigation.routes.routeClass
import app.logdate.navigation.routes.routeEntry
import app.logdate.navigation.routes.searchRoutes
import app.logdate.navigation.routes.supportsNoteViewerSharedTransition
import app.logdate.navigation.routes.timelineRoutes
import app.logdate.navigation.scenes.HomeSceneStrategy
import app.logdate.navigation.scenes.HomeTab
import app.logdate.navigation.scenes.supportsDualPaneHomeScene
import io.github.aakira.napier.Napier
import kotlin.reflect.KClass
import kotlin.uuid.Uuid

/**
 * CompositionLocal for providing SharedTransitionScope throughout the navigation hierarchy.
 * This allows any composable in the app to access the shared transition scope without
 * explicitly passing it through parameters.
 */
val LocalSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

/**
 * CompositionLocal for opening the full audio note viewer.
 * Provided by [MainNavigationRoot] to avoid callback threading through scene classes.
 */
val LocalOpenAudioNoteViewer = staticCompositionLocalOf<(Uuid) -> Unit> { {} }

/**
 * Whether the bottom navigation bar is currently visible.
 * Provided by [NavigationShell][app.logdate.navigation.scenes.NavigationShell]
 * so the global mini audio player can position itself above the nav bar.
 */
val LocalBottomNavVisible = staticCompositionLocalOf { false }

private fun sceneRouteClass(scene: Scene<NavKey>?): KClass<out NavKey>? = scene?.entries?.lastOrNull()?.routeClass()

private fun isMainTabRoute(routeClass: KClass<out NavKey>?): Boolean = HomeTab.entries.any { it.route::class == routeClass }

private val cloudAccountRouteClasses: Set<KClass<out NavKey>> =
    setOf(
        CloudAccountSetupFlowRoute::class,
    )

private fun isCloudAccountRoute(routeClass: KClass<out NavKey>?): Boolean = routeClass in cloudAccountRouteClasses

/**
 * Creates the forward navigation transition specification that implements Material Design 3
 * transition guidelines with context-aware animation selection.
 *
 * ## Transition Decision Logic
 *
 * The system categorizes navigation transitions into distinct patterns:
 *
 * 1. **Tab-to-Tab Navigation**: Lateral movement between main destinations
 *    - Timeline ↔ Journals ↔ Rewind
 *    - Uses FADE transitions for smooth, non-directional movement
 *    - Maintains spatial relationship consistency
 *
 * 2. **Tab-to-Detail Navigation**: Hierarchical forward movement
 *    - Timeline → Timeline Detail, Journals → Journal Detail
 *    - Uses SLIDE IN FROM RIGHT transitions
 *    - Establishes clear forward navigation metaphor
 *
 * 3. **Detail-to-Tab Navigation**: Return to top-level navigation
 *    - Any Detail → Main Tab (unusual forward case)
 *    - Uses FADE transitions for smooth spatial reset
 *    - Avoids directional confusion when jumping hierarchy levels
 *
 * 4. **Detail-to-Detail Navigation**: Deep hierarchical movement
 *    - Journal Detail → Note Detail, Timeline Detail → Entry Editor
 *    - Uses SLIDE IN FROM RIGHT transitions
 *    - Maintains consistent forward progression metaphor
 *
 * ## Animation Specifications
 *
 * - **Fade Transitions**: `fadeIn() togetherWith fadeOut()`
 *   - Duration: System default (~300ms)
 *   - Use case: Lateral navigation, spatial resets
 *   - Visual effect: Content crossfades smoothly
 *
 * - **Slide Transitions**: `slideInHorizontally() togetherWith slideOutHorizontally()`
 *   - Incoming: Slides in from right edge (positive offset)
 *   - Outgoing: Slides out to left edge (negative offset)
 *   - Duration: System default (~300ms)
 *   - Use case: Hierarchical forward navigation
 *   - Visual effect: New content pushes old content off screen
 *
 * ## Material Design 3 Compliance
 *
 * The transition system follows Material Design 3 motion principles:
 * - **Informative**: Transitions indicate navigation direction and hierarchy
 * - **Focused**: Animations draw attention to content relationships
 * - **Expressive**: Motion conveys the app's spatial navigation model
 * - **Purposeful**: Each transition type serves a specific navigation context
 */
private fun createForwardTransitionSpec(): AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform =
    {
        // Access current and target states from the transition scope
        val fromRoute = sceneRouteClass(initialState)
        val toRoute = sceneRouteClass(targetState)

        // Classify routes as main tabs for transition decision-making
        val isFromMainTab = isMainTabRoute(fromRoute)
        val isToMainTab = isMainTabRoute(toRoute)

        when {
            // Tab-to-tab navigation ONLY: fade between main home destinations
            isFromMainTab && isToMainTab -> {
                fadeIn() togetherWith fadeOut()
            }
            // Cloud account flow: slide up from bottom to signal modal context switch
            isCloudAccountRoute(toRoute) && !isCloudAccountRoute(fromRoute) -> {
                slideInVertically(initialOffsetY = { it }) + fadeIn() togetherWith
                    fadeOut()
            }
            // All other navigation: hierarchical slide transitions
            else -> {
                slideInHorizontally(initialOffsetX = { it }) togetherWith
                    slideOutHorizontally(targetOffsetX = { -it })
            }
        }
    }

/**
 * Creates the back navigation transition specification that provides intuitive reverse
 * animations maintaining spatial consistency with forward navigation.
 *
 * ## Back Navigation Patterns
 *
 * 1. **Return to Main Tab**: Detail → Main Tab
 *    - Journal Detail → Journals, Timeline Detail → Timeline
 *    - Uses FADE transitions for smooth spatial reset
 *    - Avoids directional confusion when returning to top-level navigation
 *    - Provides clean transition back to familiar navigation context
 *
 * 2. **Hierarchical Back Navigation**: Detail → Detail, Detail → Sub-detail
 *    - Entry Editor → Journal Detail, Note Detail → Journal Detail
 *    - Uses SLIDE IN FROM LEFT transitions (reverse of forward)
 *    - Maintains consistent directional metaphor for hierarchical movement
 *    - Creates illusion that previous content slides back into view
 *
 * ## Animation Specifications
 *
 * - **Fade for Tab Returns**: `fadeIn() togetherWith fadeOut()`
 *   - Used when returning to any main tab (Timeline, Journals, Rewind)
 *   - Provides smooth spatial reset without directional confusion
 *   - Maintains consistent behavior with tab-to-tab navigation
 *
 * - **Slide for Hierarchical Back**: `slideInHorizontally(left) togetherWith slideOutHorizontally(right)`
 *   - Incoming content slides in from left edge (negative offset)
 *   - Outgoing content slides out to right edge (positive offset)
 *   - Creates perfect reverse of forward slide transitions
 *   - Maintains spatial navigation metaphor consistency
 *
 * ## Spatial Navigation Model
 *
 * The transition system creates a consistent spatial model:
 * ```
 * ← Detailed Content ... Tabs (Home) ... More Detailed Content →
 * ```
 *
 * - Forward navigation moves right (into more specific content)
 * - Back navigation moves left (back to more general content)
 * - Tab switching uses non-directional fades (lateral movement)
 * - Returns to tabs use fades (spatial reset to navigation context)
 */
private fun createBackTransitionSpec(): AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform =
    {
        // Access current and target states from the transition scope
        val fromRoute = sceneRouteClass(initialState)
        val toRoute = sceneRouteClass(targetState)

        // Check if we're returning to a main tab
        val isToMainTab = isMainTabRoute(toRoute)

        when {
            // Returning to main tab: destination is immediately visible underneath;
            // only the outgoing screen fades away to avoid semi-transparent overlap.
            isToMainTab -> {
                EnterTransition.None togetherWith fadeOut()
            }
            // Leaving cloud account flow back to settings: slide down to dismiss
            isCloudAccountRoute(fromRoute) && !isCloudAccountRoute(toRoute) -> {
                fadeIn() togetherWith
                    slideOutVertically(targetOffsetY = { it }) + fadeOut()
            }
            // Hierarchical back navigation: slide in from left (reverse of forward)
            else -> {
                slideInHorizontally(initialOffsetX = { -it }) togetherWith
                    slideOutHorizontally(targetOffsetX = { it })
            }
        }
    }

/**
 * Creates the predictive back navigation transition specification for Android 13+
 * gesture-based back navigation.
 *
 * This specification handles predictive back gestures, providing the same transition logic
 * as standard back navigation but optimized for gesture-based interactions.
 *
 * ## Predictive Back System
 *
 * - **Gesture Integration**: Responds to system back gesture progress
 * - **Preview Animations**: Shows transition preview during gesture
 * - **Consistent Behavior**: Uses identical logic to popTransitionSpec
 * - **Performance Optimized**: Handles high-frequency gesture updates
 *
 * ## Implementation Note
 *
 * The predictive back specification mirrors the standard back transitions
 * to ensure consistent user experience whether using gesture navigation
 * or traditional back button interaction.
 */
private fun createPredictiveBackTransitionSpec(): AnimatedContentTransitionScope<Scene<NavKey>>.(Int) -> ContentTransform =
    { _ ->
        // Access current and target states from the transition scope
        val fromRoute = sceneRouteClass(initialState)
        val toRoute = sceneRouteClass(targetState)

        // Use identical logic to popTransitionSpec for consistency
        val isToMainTab = isMainTabRoute(toRoute)

        when {
            // Returning to main tab: destination is immediately visible underneath;
            // only the outgoing screen fades away to avoid semi-transparent overlap.
            isToMainTab -> {
                EnterTransition.None togetherWith fadeOut()
            }
            // Leaving cloud account flow back to settings: slide down to dismiss
            isCloudAccountRoute(fromRoute) && !isCloudAccountRoute(toRoute) -> {
                fadeIn() togetherWith
                    slideOutVertically(targetOffsetY = { it }) + fadeOut()
            }
            // Hierarchical back navigation: slide in from left (matches popTransitionSpec)
            else -> {
                slideInHorizontally(initialOffsetX = { -it }) togetherWith
                    slideOutHorizontally(targetOffsetX = { it })
            }
        }
    }

/**
 * Creates the scene strategy configuration that determines which scene type to create
 * based on the current navigation state.
 *
 * ## Strategy Chain Architecture
 *
 * The scene strategy uses a chain-of-responsibility pattern:
 * ```
 * ListDetailSceneStrategy → HomeSceneStrategy → NavDisplay Default
 * ```
 *
 * Each strategy in the chain gets to examine the navigation entries and either:
 * - Return a scene (handling the navigation state)
 * - Return null (pass to next strategy in chain)
 *
 * ## Strategy Responsibilities
 *
 * 1. **ListDetailSceneStrategy**: Handles all settings-related routes
 *    - Account settings, privacy settings, data settings, etc.
 *    - Creates scenes with settings-specific navigation patterns
 *    - Supports both fullscreen and modal presentation styles
 *
 * 2. **HomeSceneStrategy**: Handles main app navigation
 *    - Main tabs (Timeline, Journals, Rewind)
 *    - Content details (timeline days, journal entries, rewind stories)
 *    - Adaptive layouts based on screen size and content type
 *    - Two-pane layouts on large screens where appropriate
 *
 * 3. **NavDisplay Default**: Handles excluded routes
 *    - Onboarding flows, authentication, editor
 *    - Routes that don't fit the standard app navigation patterns
 *    - Simple single-screen presentation without navigation chrome
 *
 * ## Scene Selection Logic
 *
 * The HomeSceneStrategy creates different scene types based on route classification:
 *
 * - **MainTab Routes** → HomeScene with navigation chrome
 * - **TwoPaneDetail Routes** → HomeScene with adaptive layout
 * - **FullscreenDetail Routes** → FullscreenScene without navigation chrome
 * - **Excluded Routes** → null (handled by NavDisplay default)
 *
 * @param mainAppNavigator The navigator for handling navigation actions
 * @param selectedTab The mutable state holding the currently selected tab
 */
@Composable
private fun createSceneStrategy(
    mainAppNavigator: MainAppNavigator,
    selectedTab: MutableState<HomeTab>,
    isLibraryEnabled: Boolean,
): SceneStrategy<NavKey> {
    // Remember the callbacks to avoid recreating strategy on every recomposition
    val onTabSelected =
        remember(mainAppNavigator, selectedTab) {
            { tab: HomeTab ->
                selectedTab.value = tab
                mainAppNavigator.switchToTab(tab)
            }
        }

    val onNewEntry =
        remember(mainAppNavigator) {
            { mainAppNavigator.openEntryEditor() }
        }

    val getSelectedTab =
        remember(selectedTab) {
            { selectedTab.value }
        }

    val visibleTabs =
        if (isLibraryEnabled) HomeTab.entries else HomeTab.visibleEntries

    val supportsDualPaneHomeSceneNow =
        currentWindowAdaptiveInfo().windowSizeClass.supportsDualPaneHomeScene()

    val getVisibleTabs =
        remember(visibleTabs) {
            { visibleTabs }
        }

    val getSupportsDualPaneHomeScene =
        remember(supportsDualPaneHomeSceneNow) {
            { supportsDualPaneHomeSceneNow }
        }

    // Now remember the strategy with stable callbacks
    val homeStrategy =
        remember(onTabSelected, onNewEntry, getSelectedTab, getSupportsDualPaneHomeScene, getVisibleTabs) {
            HomeSceneStrategy<NavKey>(
                onTabSelected = onTabSelected,
                onNewEntry = onNewEntry,
                getSelectedTab = getSelectedTab,
                supportsDualPaneHomeScene = getSupportsDualPaneHomeScene,
                getVisibleTabs = getVisibleTabs,
            )
        }

    val listDetailStrategy = rememberListDetailSceneStrategy<NavKey>()

    return remember(homeStrategy, listDetailStrategy) {
        object : SceneStrategy<NavKey> {
            override fun SceneStrategyScope<NavKey>.calculateScene(entries: List<NavEntry<NavKey>>): Scene<NavKey>? {
                // Strategy chain execution: ListDetail (settings) → Home → NavDisplay Default
                val listDetailScene =
                    with(listDetailStrategy) {
                        this@calculateScene.calculateScene(entries)
                    }
                return listDetailScene ?: with(homeStrategy) {
                    this@calculateScene.calculateScene(entries)
                }
            }
        }
    }
}

/**
 * The root composable for LogDate's navigation system, implementing Navigation 3 with
 * adaptive UI patterns, shared element transitions, and sophisticated scene management.
 *
 * This function sets up the complete navigation architecture including:
 * - Transition specifications that follow Material Design 3 guidelines
 * - Scene strategy chain for different UI patterns (Settings → Home → Default)
 * - Shared element transitions between related content
 * - Adaptive layouts that respond to screen size changes
 * - Back stack management with safe navigation fallbacks
 *
 * ## Architecture Integration
 *
 * The navigation root integrates with:
 * - **HomeSceneStrategy**: Adaptive layouts for main app content
 * - **ListDetailSceneStrategy**: Specialized layouts for settings flows
 * - **MainAppNavigator**: Centralized navigation state management
 * - **Entry Providers**: Route definitions and content mapping
 *
 * ## Transition System
 *
 * Implements comprehensive transition specifications:
 * - **Forward navigation**: Context-aware slide and fade transitions
 * - **Back navigation**: Reverse animations maintaining spatial consistency
 * - **Predictive back**: Android 13+ gesture-based back previews
 * - **Shared elements**: Seamless transitions between related content
 *
 * @param mainAppNavigator The navigator that manages the app's navigation state and provides
 *   navigation functions for moving between routes
 */
@Suppress("ktlint:standard:function-naming")
@Composable
fun MainNavigationRoot(
    mainAppNavigator: MainAppNavigator,
    sharingLauncher: SharingLauncher,
) {
    val preferencesDataSource: LogdatePreferencesDataSource = org.koin.compose.koinInject()
    val appContext = LocalContext.current.applicationContext
    val hasTimelineRoute =
        mainAppNavigator.backStack.any { route ->
            route is TimelineListRoute || route is TimelineDetail
        }
    val homeViewModel: HomeViewModel? =
        if (hasTimelineRoute) {
            // Create HomeViewModel only if timeline routes are currently in the backstack,
            // so we avoid DB-heavy initialization during onboarding and non-home flows.
            org.koin.compose.viewmodel
                .koinViewModel()
        } else {
            null
        }

    // Remember the selected tab across recompositions
    val selectedTab = rememberSaveable { mutableStateOf(HomeTab.TIMELINE) }

    val isLibraryEnabled by preferencesDataSource
        .observeLibraryEnabled()
        .collectAsStateWithLifecycle(initialValue = false)

    SharedTransitionLayout {
        CompositionLocalProvider(LocalSharedTransitionScope provides this) {
            // Create the scene strategy with proper memoization to avoid recomposition loops
            val sceneStrategy = createSceneStrategy(mainAppNavigator, selectedTab, isLibraryEnabled)

            NavDisplay(
                transitionSpec = createForwardTransitionSpec(),
                popTransitionSpec = createBackTransitionSpec(),
                predictivePopTransitionSpec = createPredictiveBackTransitionSpec(),
                entryDecorators =
                    listOf(
                        rememberSaveableStateHolderNavEntryDecorator(rememberSaveableStateHolder()),
                        rememberViewModelStoreNavEntryDecorator(),
                    ),
                sceneStrategies = listOf(sceneStrategy),
                backStack = mainAppNavigator.backStack,
                onBack = {
                    Napier.d("Navigation: onBack called, current backstack size: ${mainAppNavigator.backStack.size}")
                    Napier.d("Navigation: Current backstack entries: ${mainAppNavigator.backStack.map { it::class.simpleName }}")

                    // Navigation 3 pattern: remove a single entry per back action
                    val removed = mainAppNavigator.safelyRemoveLastEntry()
                    if (!removed) {
                        Napier.w("Navigation: Did not remove entry (backstack size too small)")
                    }

                    // Safety check: ensure we always have at least one main tab in the backstack
                    val mainTabRoutes = HomeTab.entries.map { it.route }
                    if (mainAppNavigator.backStack.isEmpty() || mainAppNavigator.backStack.none { it in mainTabRoutes }) {
                        Napier.w("Navigation: No main tab in backstack, resetting to Timeline")
                        mainAppNavigator.backStack.clear()
                        mainAppNavigator.backStack.add(HomeTab.TIMELINE.route)
                    }

                    Napier.d("Navigation: After back navigation, backstack size: ${mainAppNavigator.backStack.size}")
                    Napier.d("Navigation: New backstack entries: ${mainAppNavigator.backStack.map { it::class.simpleName }}")
                    Napier.d("Navigation: HomeSceneStrategy will recalculate scene based on new backstack state")
                },
                entryProvider =
                    entryProvider {
                        routeEntry<NavigationStart> { _ ->
                            TimelineLoadingPlaceholder(modifier = Modifier.fillMaxSize())
                        }
                        routeEntry<NoteViewerRoute>(
                            metadata = noteViewerRouteTransitionMetadata,
                        ) { route ->
                            val noteViewerActivity = LocalContext.current as? android.app.Activity
                            val previousRouteClass =
                                mainAppNavigator.backStack
                                    .getOrNull(mainAppNavigator.backStack.lastIndex - 1)
                                    ?.let { it::class }

                            NoteViewerScreen(
                                noteId = route.id,
                                journalId = route.journalId,
                                enableSharedBounds = supportsNoteViewerSharedTransition(previousRouteClass),
                                onGoBack = mainAppNavigator::goBack,
                                onOpenLocationTimeline = mainAppNavigator::openLocationTimeline,
                                onNavigateToNote = { newNoteId ->
                                    mainAppNavigator.backStack.removeLastOrNull()
                                    mainAppNavigator.backStack.add(NoteViewerRoute(newNoteId, route.journalId))
                                },
                                onEnterPiP = {
                                    noteViewerActivity?.let { enterPictureInPicture(it) }
                                },
                            )
                        }
                        onboarding(
                            onBack = mainAppNavigator::goBack,
                            onNavigate = { route -> mainAppNavigator.backStack.add(route) },
                            onComplete = mainAppNavigator::navigateHomeFromOnboarding,
                        )
                        journalRoutes(
                            onBack = mainAppNavigator::goBack,
                            onOpenJournalDetail = mainAppNavigator::openJournalDetail,
                            onCreateJournal = { mainAppNavigator.backStack.add(NewJournalRoute) },
                            onBrowseJournals = mainAppNavigator::openSearch,
                            onJournalDeleted = { mainAppNavigator.backStack.removeLastOrNull() },
                            onNavigateToNoteDetail = { noteId, journalId ->
                                mainAppNavigator.backStack.add(NoteViewerRoute(noteId, journalId))
                            },
                            onNavigateToDay = mainAppNavigator::openTimelineDetail,
                            onOpenEditor = { journalId ->
                                mainAppNavigator.openEntryEditor(journalIds = listOf(journalId))
                            },
                            onNavigateToJournalSettings = mainAppNavigator::openJournalSettings,
                            onNavigateToShareJournal = mainAppNavigator::openShareJournal,
                            onJournalCreated = mainAppNavigator::finishJournalCreation,
                        )
                        homeViewModel?.let { safeHomeViewModel ->
                            timelineRoutes(
                                openEntryEditor = mainAppNavigator::openEntryEditor,
                                onOpenDraft = { draftId ->
                                    mainAppNavigator.openDraft(
                                        draftId = kotlin.uuid.Uuid.parse(draftId),
                                    )
                                },
                                sharingLauncher = sharingLauncher,
                                onOpenTimelineDetail = mainAppNavigator::openTimelineDetail,
                                onCloseTimelineDetail = mainAppNavigator::goBack,
                                onOpenSettings = mainAppNavigator::openSettings,
                                onOpenLocationTimeline = mainAppNavigator::openLocationTimeline,
                                onOpenSearch = mainAppNavigator::openSearch,
                                onImportBackup = mainAppNavigator::openExportSettings,
                                onOpenEvent = { eventId ->
                                    mainAppNavigator.openEventDetail(kotlin.uuid.Uuid.parse(eventId))
                                },
                                onDecorate = { mainAppNavigator.navigateToPostcardEditor() },
                                homeViewModel = safeHomeViewModel,
                            )
                        }
                        locationRoutes(
                            onOpenNote = { noteId ->
                                mainAppNavigator.backStack.add(NoteViewerRoute(noteId))
                            },
                        )
                        libraryRoutes(
                            onOpenMediaDetail = mainAppNavigator::openMediaDetail,
                            onBack = mainAppNavigator::goBack,
                            onNavigateToJournal = mainAppNavigator::openJournalDetail,
                            onOpenSearch = { mainAppNavigator.openSearch() },
                            onOpenPostcards = { mainAppNavigator.navigateToPostcardsCollection() },
                            sharingLauncher = sharingLauncher,
                        )
                        searchRoutes(
                            onBack = mainAppNavigator::goBack,
                            onNavigateToDay = mainAppNavigator::openTimelineDetail,
                        )
                        rewindRoutes(
                            onBack = mainAppNavigator::goBack,
                            onNavigateToRewindDetail = { id ->
                                mainAppNavigator.backStack.add(
                                    RewindDetailRoute(
                                        id,
                                    ),
                                )
                            },
                        )
                        eventRoutes(
                            onBack = mainAppNavigator::goBack,
                        )
                        postcardRoutes(
                            onBack = mainAppNavigator::goBack,
                            onOpenPostcard = { id ->
                                mainAppNavigator.navigateToPostcardViewer(id)
                            },
                            onEditPostcard = { id ->
                                mainAppNavigator.navigateToPostcardEditor(id)
                            },
                            onNavigateToMoment = {
                                mainAppNavigator.goBack()
                            },
                            onShareUri = { uri ->
                                sharingLauncher.shareContent(mediaUris = listOf(uri))
                            },
                        )
                        appSettingsRoutes(
                            onBack = mainAppNavigator::goBack,
                            onAppReset = mainAppNavigator::resetApp,
                            onNavigateToProfile = mainAppNavigator::openProfile,
                            onNavigateToAccount = mainAppNavigator::openAccountSettings,
                            onNavigateToDevices = mainAppNavigator::openDevicesSettings,
                            onNavigateToWatch = mainAppNavigator::openWatchSettings,
                            onNavigateToLocation = mainAppNavigator::openLocationSettings,
                            onNavigateToPrivacy = mainAppNavigator::openPrivacySettings,
                            onOpenLocationTimeline = mainAppNavigator::openLocationTimeline,
                            onNavigateToLibrarySettings = mainAppNavigator::openLibrarySettings,
                            onNavigateToMemories = mainAppNavigator::openMemoriesSettings,
                            onNavigateToVoiceNotes = mainAppNavigator::openVoiceNotesSettings,
                            onNavigateToNotifications = mainAppNavigator::openNotificationSettings,
                            onNavigateToStreaks = mainAppNavigator::openStreakSettings,
                            onNavigateToRewindSettings = mainAppNavigator::openRewindSettings,
                            onNavigateToEventsSettings = mainAppNavigator::openEventsSettings,
                            onNavigateToCalendarSyncSettings = mainAppNavigator::openCalendarSyncSettings,
                            onNavigateToCalendarSyncCalendars = mainAppNavigator::openCalendarSyncCalendars,
                            onNavigateToCalendarSyncActivity = mainAppNavigator::openCalendarSyncActivity,
                            onNavigateToEventDetail = mainAppNavigator::openEventDetail,
                            onNavigateToRecommendations = mainAppNavigator::openRecommendationSettings,
                            onNavigateToTimeline = mainAppNavigator::openTimelineSettings,
                            onNavigateToDayBoundary = mainAppNavigator::openDayBoundarySettings,
                            onNavigateToAdvanced = mainAppNavigator::openAdvancedSettings,
                            onNavigateToSync = mainAppNavigator::openSyncSettings,
                            onNavigateToExport = mainAppNavigator::openExportSettings,
                            onNavigateToReset = mainAppNavigator::openResetSettings,
                            onNavigateToClearData = mainAppNavigator::openClearDataSettings,
                            onNavigateToResetApp = mainAppNavigator::openResetAppSettings,
                            onNavigateToLocationTrackingOptions = mainAppNavigator::openLocationTrackingOptions,
                            onNavigateToLocationInterval = mainAppNavigator::openLocationInterval,
                            onNavigateToLocationAdvanced = mainAppNavigator::openLocationAdvanced,
                            onNavigateToBirthday = mainAppNavigator::openBirthdaySettings,
                            onNavigateToWatchSync = mainAppNavigator::openWatchSyncSettings,
                            onNavigateToWatchNotifications = mainAppNavigator::openWatchNotificationSettings,
                            onNavigateToWatchTroubleshooting = mainAppNavigator::openWatchTroubleshooting,
                            onNavigateToCloudAccountCreation = {
                                mainAppNavigator.backStack.add(CloudAccountSetupFlowRoute())
                            },
                            onNavigateToSignIn = {
                                mainAppNavigator.backStack.add(CloudAccountSetupFlowRoute(startOnSignIn = true))
                            },
                        )
                        cloudAccountSetupFlow(
                            onBack = mainAppNavigator::goBack,
                            onSetupCompleted = {
                                mainAppNavigator.safelyClearBackstack(SettingsOverviewRoute)
                            },
                        )
                        editorRoutes(
                            onBack = mainAppNavigator::goBack,
                            onSave = mainAppNavigator::goBack, // TODO: Figure out whether this is desired behavior
                        )
                    },
            )
        }
    }
}
