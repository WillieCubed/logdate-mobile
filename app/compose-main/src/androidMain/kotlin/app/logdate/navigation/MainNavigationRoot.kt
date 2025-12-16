@file:OptIn(ExperimentalSharedTransitionApi::class)

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
 * - **SettingsSceneStrategy**: Specialized layouts for settings flows
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
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entry
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSavedStateNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.navigation3.ui.Scene
import androidx.navigation3.ui.SceneStrategy
import androidx.navigation3.ui.rememberSceneSetupNavEntryDecorator
import app.logdate.feature.core.main.HomeViewModel
import app.logdate.navigation.routes.appSettingsRoutes
import app.logdate.navigation.routes.cloudAccountSetup
import app.logdate.navigation.routes.core.JournalList
import app.logdate.navigation.routes.core.NavigationStart
import app.logdate.navigation.routes.core.NewJournalRoute
import app.logdate.navigation.routes.core.OnboardingCompleteRoute
import app.logdate.navigation.routes.core.OnboardingImportRoute
import app.logdate.navigation.routes.core.OnboardingWelcomeBackRoute
import app.logdate.navigation.routes.core.PersonalIntroRoute
import app.logdate.navigation.routes.core.RewindDetailRoute
import app.logdate.navigation.routes.core.RewindList
import app.logdate.navigation.routes.core.TimelineListRoute
import app.logdate.navigation.routes.core.goBack
import app.logdate.navigation.routes.core.navigateHomeFromOnboarding
import app.logdate.navigation.routes.core.openEntryEditor
import app.logdate.navigation.routes.core.switchToTab
import app.logdate.navigation.routes.editorRoutes
import app.logdate.navigation.routes.finishJournalCreation
import app.logdate.navigation.routes.journalRoutes
import app.logdate.navigation.routes.onboarding
import app.logdate.navigation.routes.openAccountSettings
import app.logdate.navigation.routes.openBirthdaySettings
import app.logdate.navigation.routes.openDangerZoneSettings
import app.logdate.navigation.routes.openDataSettings
import app.logdate.navigation.routes.openJournalDetail
import app.logdate.navigation.routes.openJournalSettings
import app.logdate.navigation.routes.openLocationSettings
import app.logdate.navigation.routes.openPrivacySettings
import app.logdate.navigation.routes.openProfile
import app.logdate.navigation.routes.openSearch
import app.logdate.navigation.routes.openSettings
import app.logdate.navigation.routes.openShareJournal
import app.logdate.navigation.routes.openTimelineDetail
import app.logdate.navigation.routes.resetApp
import app.logdate.navigation.routes.rewindRoutes
import app.logdate.navigation.routes.searchRoutes
import app.logdate.navigation.routes.timelineRoutes
import app.logdate.navigation.scenes.HomeSceneStrategy
import app.logdate.navigation.scenes.HomeTab
import app.logdate.navigation.scenes.SettingsSceneStrategy
import io.github.aakira.napier.Napier
import org.koin.compose.viewmodel.koinViewModel

/**
 * CompositionLocal for providing SharedTransitionScope throughout the navigation hierarchy.
 * This allows any composable in the app to access the shared transition scope without
 * explicitly passing it through parameters.
 */
val LocalSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

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
private fun createForwardTransitionSpec(): AnimatedContentTransitionScope<*>.() -> ContentTransform = { 
    // Access current and target states from the transition scope
    val from = initialState
    val to = targetState
    
    // Classify routes as main tabs for transition decision-making
    val isFromMainTab = when (from) {
        TimelineListRoute, JournalList, RewindList -> true
        else -> false
    }
    val isToMainTab = when (to) {
        TimelineListRoute, JournalList, RewindList -> true
        else -> false
    }
    
    when {
        // Tab-to-tab navigation ONLY: fade between main home destinations
        isFromMainTab && isToMainTab -> {
            fadeIn() togetherWith fadeOut()
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
private fun createBackTransitionSpec(): AnimatedContentTransitionScope<*>.() -> ContentTransform = { 
    // Access current and target states from the transition scope
    val to = targetState
    
    // Check if we're returning to a main tab
    val mainTabRoutes = HomeTab.entries.map { it.route }
    val isToMainTab = to in mainTabRoutes
    
    when {
        // Returning to main tab: spatial reset with fade
        isToMainTab -> {
            fadeIn() togetherWith fadeOut()
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
private fun createPredictiveBackTransitionSpec(): AnimatedContentTransitionScope<*>.() -> ContentTransform = { 
    // Access current and target states from the transition scope
    val to = targetState
    
    // Use identical logic to popTransitionSpec for consistency
    val mainTabRoutes = HomeTab.entries.map { it.route }
    val isToMainTab = to in mainTabRoutes
    
    when {
        // Returning to main tab: fade transition (matches popTransitionSpec)
        isToMainTab -> {
            fadeIn() togetherWith fadeOut()
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
 * SettingsSceneStrategy → HomeSceneStrategy → NavDisplay Default
 * ```
 * 
 * Each strategy in the chain gets to examine the navigation entries and either:
 * - Return a scene (handling the navigation state)
 * - Return null (pass to next strategy in chain)
 * 
 * ## Strategy Responsibilities
 * 
 * 1. **SettingsSceneStrategy**: Handles all settings-related routes
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
    selectedTab: MutableState<HomeTab>
): SceneStrategy<NavKey> {
    // Remember the callbacks to avoid recreating strategy on every recomposition
    val onTabSelected = remember(mainAppNavigator, selectedTab) {
        { tab: HomeTab ->
            selectedTab.value = tab
            mainAppNavigator.switchToTab(tab)
        }
    }
    
    val onNewEntry = remember(mainAppNavigator) {
        { mainAppNavigator.openEntryEditor() }
    }
    
    val getSelectedTab = remember(selectedTab) {
        { selectedTab.value }
    }
    
    // Now remember the strategy with stable callbacks
    val homeStrategy = remember(onTabSelected, onNewEntry, getSelectedTab) {
        HomeSceneStrategy<NavKey>(
            onTabSelected = onTabSelected,
            onNewEntry = onNewEntry,
            getSelectedTab = getSelectedTab
        )
    }

    val settingsStrategy = remember { SettingsSceneStrategy<NavKey>() }
    
    return remember(homeStrategy, settingsStrategy) {
        object : SceneStrategy<NavKey> {
            @Composable
            override fun calculateScene(
                entries: List<NavEntry<NavKey>>,
                onBack: (Int) -> Unit
            ): Scene<NavKey>? {
                // Strategy chain execution: Settings → Home → NavDisplay Default
                val settingsScene = settingsStrategy.calculateScene(entries, onBack)
                return settingsScene ?: homeStrategy.calculateScene(entries, onBack)
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
 * - **SettingsSceneStrategy**: Specialized layouts for settings flows
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
 * @param homeViewModel The shared view model that maintains state for the main home tabs
 *   and coordinates data across the primary navigation destinations
 */
@Composable
fun MainNavigationRoot(
    mainAppNavigator: MainAppNavigator,
    homeViewModel: HomeViewModel = koinViewModel(),
) {
    // Remember the selected tab across recompositions
    val selectedTab = rememberSaveable { mutableStateOf(HomeTab.TIMELINE) }

    SharedTransitionLayout {
        CompositionLocalProvider(LocalSharedTransitionScope provides this) {
            // Create the scene strategy with proper memoization to avoid recomposition loops
            val sceneStrategy = createSceneStrategy(mainAppNavigator, selectedTab)
            
            NavDisplay(
        transitionSpec = createForwardTransitionSpec(),
        popTransitionSpec = createBackTransitionSpec(),
        predictivePopTransitionSpec = createPredictiveBackTransitionSpec(),
        entryDecorators = listOf(
            rememberSceneSetupNavEntryDecorator(),
            rememberSavedStateNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator()
        ),
        sceneStrategy = sceneStrategy,
        backStack = mainAppNavigator.backStack,
        onBack = { keysToRemove ->
            Napier.d("Navigation: onBack called, keys to remove: $keysToRemove, current backstack size: ${mainAppNavigator.backStack.size}")
            Napier.d("Navigation: Current backstack entries: ${mainAppNavigator.backStack.map { it::class.simpleName }}")
            
            // Navigation 3 pattern: Simply remove the requested entries and let Scene Strategy handle recalculation
            // Only prevent removal if it would result in a completely empty backstack
            if (keysToRemove >= mainAppNavigator.backStack.size) {
                // Special case: trying to remove all entries (would result in empty backstack)
                // Remove all but one to allow app to continue with a main tab
                val entriesToRemove = (mainAppNavigator.backStack.size - 1).coerceAtLeast(0)
                Napier.w("Navigation: Requested to remove $keysToRemove entries but only removing $entriesToRemove to prevent empty backstack")
                repeat(entriesToRemove) { 
                    val removedEntry = mainAppNavigator.backStack.removeLastOrNull()
                    Napier.d("Navigation: Removed entry: ${removedEntry?.let { it::class.simpleName }}")
                }
                
                // If we only have one entry left and it's not a main tab, navigate to Timeline
                val remainingEntry = mainAppNavigator.backStack.lastOrNull()
                val mainTabRoutes = HomeTab.entries.map { it.route }
                if (remainingEntry != null && remainingEntry !in mainTabRoutes) {
                    Napier.w("Navigation: Last remaining entry is not a main tab, replacing with Timeline")
                    mainAppNavigator.backStack.removeLastOrNull()
                    mainAppNavigator.backStack.add(HomeTab.TIMELINE.route)
                }
            } else {
                // Normal case: remove the requested number of entries
                Napier.d("Navigation: Removing $keysToRemove entries (normal back navigation)")
                repeat(keysToRemove) { 
                    val removedEntry = mainAppNavigator.backStack.removeLastOrNull()
                    Napier.d("Navigation: Removed entry: ${removedEntry?.let { it::class.simpleName }}")
                }
            }
            
            Napier.d("Navigation: After back navigation, backstack size: ${mainAppNavigator.backStack.size}")
            Napier.d("Navigation: New backstack entries: ${mainAppNavigator.backStack.map { it::class.simpleName }}")
            Napier.d("Navigation: HomeSceneStrategy will recalculate scene based on new backstack state")
        },
        entryProvider = entryProvider {
            entry<NavigationStart> { _ ->
                // This is the initial entry, which can be used to stall the app during startup.
                Column(modifier = Modifier.fillMaxSize()) {
                    // Placeholder content while the app is loading
                }
            }
            onboarding(
                onBack = mainAppNavigator::goBack,
                onStartOnboarding = { mainAppNavigator.backStack.add(PersonalIntroRoute) },
                onContinueToEntry = { mainAppNavigator.backStack.add(OnboardingImportRoute) },
                onImportCompleted = { mainAppNavigator.backStack.add(OnboardingCompleteRoute) },
                onWelcomeBack = { mainAppNavigator.backStack.add(OnboardingWelcomeBackRoute) },
                onComplete = mainAppNavigator::navigateHomeFromOnboarding,
            )
            journalRoutes(
                onBack = mainAppNavigator::goBack,
                onOpenJournalDetail = mainAppNavigator::openJournalDetail,
                onCreateJournal = { mainAppNavigator.backStack.add(NewJournalRoute) },
                onJournalDeleted = { mainAppNavigator.backStack.removeLastOrNull() },
                onNavigateToNoteDetail = { journalId, noteId ->
                    // TODO: Implement note detail navigation once the UI is added
                    // This would navigate to a note detail screen with the specific journal and note ID
                },
                onNavigateToJournalSettings = mainAppNavigator::openJournalSettings,
                onNavigateToShareJournal = mainAppNavigator::openShareJournal,
                onJournalCreated = mainAppNavigator::finishJournalCreation
            )
            timelineRoutes(
                openEntryEditor = mainAppNavigator::openEntryEditor,
                onOpenTimelineDetail = mainAppNavigator::openTimelineDetail,
                onCloseTimelineDetail = mainAppNavigator::goBack,
                onOpenSettings = mainAppNavigator::openSettings,
                onOpenSearch = mainAppNavigator::openSearch,
                homeViewModel = homeViewModel,
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
                            id
                        )
                    )
                }
            )
            appSettingsRoutes(
                onBack = mainAppNavigator::goBack,
                onAppReset = mainAppNavigator::resetApp,
                onNavigateToCloudAccountCreation = {
                    mainAppNavigator.backStack.add(app.logdate.navigation.routes.CloudAccountIntroRoute())
                },
                onNavigateToProfile = mainAppNavigator::openProfile,
                onNavigateToAccount = mainAppNavigator::openAccountSettings,
                onNavigateToPrivacy = mainAppNavigator::openPrivacySettings,
                onNavigateToData = mainAppNavigator::openDataSettings,
                onNavigateToDangerZone = mainAppNavigator::openDangerZoneSettings,
                onNavigateToLocation = mainAppNavigator::openLocationSettings,
                onNavigateToBirthdaySettings = mainAppNavigator::openBirthdaySettings
            )
            cloudAccountSetup(
                onBack = mainAppNavigator::goBack,
                onUsernameSelected = { 
                    mainAppNavigator.backStack.add(app.logdate.navigation.routes.UsernameSelectionRoute)
                },
                onDisplayNameSelected = {
                    mainAppNavigator.backStack.add(app.logdate.navigation.routes.DisplayNameSelectionRoute)
                },
                onPasskeyCreated = {
                    mainAppNavigator.backStack.add(app.logdate.navigation.routes.PasskeyCreationRoute)
                },
                onSetupCompleted = {
                    // After setup is completed, go back to settings
                    repeat(mainAppNavigator.backStack.size) { mainAppNavigator.goBack() }
                    mainAppNavigator.openSettings()
                },
                onSkip = {
                    // Simply go back to the previous screen when skipped
                    mainAppNavigator.goBack()
                }
            )
            editorRoutes(
                onBack = mainAppNavigator::goBack,
                onSave = mainAppNavigator::goBack, // TODO: Figure out whether this is desired behavior
            )
        }
    )
        }
    }
}