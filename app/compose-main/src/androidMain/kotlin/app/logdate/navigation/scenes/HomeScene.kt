@file:OptIn(ExperimentalSharedTransitionApi::class)

package app.logdate.navigation.scenes

/**
 * LogDate Navigation Architecture - Home Scene Implementation
 * 
 * This file implements the core navigation scenes and strategies for LogDate's adaptive UI,
 * built on Android's Navigation 3 framework. It provides a sophisticated navigation system
 * that adapts to different screen sizes and content types.
 * 
 * Architecture Overview:
 * 
 * The navigation system consists of three main components:
 * 
 * 1. Scene Types (UI Layout Controllers):
 *    - HomeScene: Renders content with navigation chrome (bottom nav/rail + content)
 *    - FullscreenScene: Renders content without any navigation UI for immersive experiences
 * 
 * 2. Route Classification System (Decision Logic):
 *    Routes are classified into four categories that determine their display behavior:
 *    - MainTab: Timeline, Journals, Rewind overview (always shown with navigation)
 *    - TwoPaneDetail: Timeline details (shown side-by-side on large screens)
 *    - FullscreenDetail: Rewind details, Journal details (always immersive)
 *    - Excluded: Onboarding, Settings, Editor (handled by other strategies or NavDisplay)
 * 
 * 3. Scene Strategy (Routing Logic):
 *    HomeSceneStrategy analyzes the navigation back stack and creates appropriate scenes:
 *    Back Stack Analysis → Route Classification → Scene Creation → UI Rendering
 * 
 * Navigation Flow Examples:
 * 
 * Example 1: Main Tab Navigation
 *   Timeline → Journals → Rewind
 *   ↓           ↓          ↓
 *   MainTab    MainTab    MainTab
 *   ↓           ↓          ↓
 *   HomeScene  HomeScene  HomeScene (with navigation chrome)
 * 
 * Example 2: Detail Navigation
 *   Timeline → Timeline Detail (Large Screen)
 *   ↓          ↓
 *   MainTab    TwoPaneDetail
 *   ↓          ↓
 *   HomeScene  HomeScene (main + detail side-by-side)
 * 
 *   Timeline → Timeline Detail (Small Screen)
 *   ↓          ↓
 *   MainTab    TwoPaneDetail
 *   ↓          ↓
 *   HomeScene  HomeScene (detail only, navigation hidden)
 * 
 * Example 3: Immersive Content
 *   Rewind List → Rewind Detail
 *   ↓             ↓
 *   MainTab       FullscreenDetail
 *   ↓             ↓
 *   HomeScene     FullscreenScene (no navigation chrome)
 * 
 * Screen Size Adaptations:
 * 
 * The system adapts to three screen size categories:
 * - Small (<600dp): Bottom navigation, single-pane content
 * - Medium (600dp-1240dp): Side navigation rail, single-pane content  
 * - Large (≥1240dp): Side navigation rail, two-pane content (where supported)
 * 
 * Integration with Navigation 3:
 * 
 * This system integrates with Navigation 3's core concepts:
 * - NavDisplay: Main navigation container that hosts scenes
 * - Scene: Defines layout and content for a navigation state
 * - SceneStrategy: Determines which scene to create based on back stack
 * - NavEntry: Individual navigation entries with content and metadata
 * 
 * Key Design Principles:
 * 
 * 1. Adaptive by Default: All layouts respond to screen size changes
 * 2. Content-First: Navigation chrome adapts to content, not vice versa
 * 3. Immersive When Needed: Full-screen experiences for media and storytelling
 * 4. Consistent Patterns: Predictable navigation behavior across the app
 * 5. Performance Optimized: Scenes are created on-demand and reused when possible
 */

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
// TODO: Fix shared element API imports
// import androidx.compose.animation.rememberSharedContentState
// import androidx.compose.animation.sharedElement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.logdate.navigation.LocalSharedTransitionScope
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.ui.Scene
import androidx.navigation3.ui.SceneStrategy
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_EXPANDED_LOWER_BOUND
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_MEDIUM_LOWER_BOUND
import app.logdate.navigation.components.LogDateBottomNavigationBar
import app.logdate.navigation.components.LogDateNavigationRail
import app.logdate.navigation.routes.AccountCreationCompletionRoute
import app.logdate.navigation.routes.CloudAccountIntroRoute
import app.logdate.navigation.routes.DisplayNameSelectionRoute
import app.logdate.navigation.routes.PasskeyCreationRoute
import app.logdate.navigation.routes.UsernameSelectionRoute
import app.logdate.navigation.routes.core.AccountSettingsRoute
import app.logdate.navigation.routes.core.BirthdaySettingsRoute
import app.logdate.navigation.routes.core.DangerZoneSettingsRoute
import app.logdate.navigation.routes.core.DataSettingsRoute
import app.logdate.navigation.routes.core.EntryEditor
import app.logdate.navigation.routes.core.JournalList
import app.logdate.navigation.routes.core.LocationSettingsRoute
import app.logdate.navigation.routes.core.NavigationStart
import app.logdate.navigation.routes.core.OnboardingCompleteRoute
import app.logdate.navigation.routes.core.OnboardingEntryRoute
import app.logdate.navigation.routes.core.OnboardingImportRoute
import app.logdate.navigation.routes.core.OnboardingSignIn
import app.logdate.navigation.routes.core.OnboardingStart
import app.logdate.navigation.routes.core.OnboardingWelcomeBackRoute
import app.logdate.navigation.routes.core.PrivacySettingsRoute
import app.logdate.navigation.routes.core.RewindList
import app.logdate.navigation.routes.core.SettingsOverviewRoute
import app.logdate.navigation.routes.core.TimelineDetail
import app.logdate.navigation.routes.core.TimelineListRoute
import app.logdate.ui.scaffold.ResponsiveScaffold
import app.logdate.ui.scaffold.ResponsiveScaffoldDefaults
import app.logdate.ui.scaffold.ResponsiveScaffoldDefaults.rememberSnackbarHostState
import app.logdate.ui.scaffold.SnackbarHost
import io.github.aakira.napier.Napier

/**
 * CompositionLocal for providing AnimatedVisibilityScope throughout the home scene.
 */
val LocalAnimatedVisibilityScope = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * Shared element key for the FAB to editor transition.
 * This key is used to identify the FAB as a shared element that transitions
 * to the entry editor screen.
 */
private const val FAB_TO_EDITOR_SHARED_ELEMENT_KEY = "fab_to_editor"

/**
 * Defines how different route types should be handled by the HomeSceneStrategy.
 * 
 * This sealed class provides a type-safe way to classify routes and determine their
 * display behavior in the navigation system. Each classification corresponds to a
 * specific UI pattern and user experience.
 * 
 * Route Classification Decision Matrix:
 * 
 * Route Type        | Screen Size    | Classification  | Scene Type      | Navigation Chrome
 * ----------------- | -------------- | --------------- | --------------- | -----------------
 * Timeline List     | Any            | MainTab         | HomeScene       | Yes (Bottom Nav/Rail)
 * Journal List      | Any            | MainTab         | HomeScene       | Yes (Bottom Nav/Rail)
 * Rewind List       | Any            | MainTab         | HomeScene       | Yes (Bottom Nav/Rail)
 * Timeline Detail   | Large (≥1240dp)| TwoPaneDetail   | HomeScene       | Yes (Two-pane with rail)
 * Timeline Detail   | Small/Medium   | TwoPaneDetail   | HomeScene       | No (Detail only)
 * Rewind Detail     | Any            | FullscreenDetail| FullscreenScene | No (Immersive)
 * Journal Detail    | Any            | FullscreenDetail| FullscreenScene | No (Immersive)
 * Settings          | Any            | Excluded        | (SettingsStrategy)| Strategy-dependent
 * Onboarding        | Any            | Excluded        | (NavDisplay)    | No
 * Editor            | Any            | Excluded        | (NavDisplay)    | No
 * 
 * Classification Examples:
 * 
 * MainTab:
 *   These routes always show with navigation chrome
 *   TimelineListRoute -> RouteClassification.MainTab(HomeTab.TIMELINE)
 *   JournalList -> RouteClassification.MainTab(HomeTab.JOURNALS)
 *   RewindList -> RouteClassification.MainTab(HomeTab.REWIND)
 * 
 * TwoPaneDetail:
 *   These routes can show side-by-side with their parent on large screens
 *   TimelineDetail(LocalDate.now()) -> RouteClassification.TwoPaneDetail(HomeTab.TIMELINE)
 *   Becomes: Timeline List | Timeline Detail (on large screens)
 *        or: Timeline Detail only (on small screens)
 * 
 * FullscreenDetail:
 *   These routes always take over the entire screen
 *   RewindDetailRoute(uuid) -> RouteClassification.FullscreenDetail
 *   JournalDetail(uuid) -> RouteClassification.FullscreenDetail
 *   Result: Immersive, fullscreen experience without navigation UI
 * 
 * Excluded:
 *   These routes are handled by other strategies or NavDisplay directly
 *   OnboardingStart -> RouteClassification.Excluded
 *   EntryEditor(uuid) -> RouteClassification.Excluded
 *   SettingsOverviewRoute -> RouteClassification.Excluded
 *   Result: No HomeScene created, handled elsewhere in the navigation system
 */
sealed class RouteClassification {
    /**
     * Main tab routes that should be displayed with navigation UI.
     * 
     * These are the primary destinations of the app (Timeline, Journals, Rewind overview)
     * that should always be accessible through bottom navigation or navigation rail.
     * 
     * @param tab The specific tab this route represents
     */
    data class MainTab(val tab: HomeTab) : RouteClassification()
    
    /**
     * Detail routes that should be displayed in two-pane mode on large screens,
     * with both main and detail content visible.
     * 
     * On smaller screens, these routes display as full-screen details with hidden navigation.
     * On larger screens (≥1240dp), they display side-by-side with their parent tab.
     * 
     * Examples: Timeline day details that can show alongside the timeline list.
     * 
     * @param parentTab The main tab that this detail belongs to
     */
    data class TwoPaneDetail(val parentTab: HomeTab) : RouteClassification()
    
    /**
     * Detail routes that should always be displayed full-screen without navigation UI.
     * 
     * These routes are designed for immersive experiences and always hide all navigation
     * chrome regardless of screen size. Used for media viewing, storytelling, and other
     * content that benefits from full-screen presentation.
     * 
     * Examples: Rewind story details, journal reading mode, photo/video viewers.
     */
    data object FullscreenDetail : RouteClassification()
    
    /**
     * Routes that should be excluded from HomeScene entirely.
     * 
     * These routes are handled by other scene strategies (like SettingsSceneStrategy)
     * or by NavDisplay directly. They don't participate in the main app's adaptive
     * navigation patterns.
     * 
     * Examples: Onboarding flows, settings screens, entry editor, authentication flows.
     */
    data object Excluded : RouteClassification()
}

/**
 * Configuration for route classification and scene behavior.
 * 
 * This object contains the core logic for determining how different routes should be
 * displayed in the navigation system. It implements a hierarchical classification
 * algorithm that considers route types, context, and screen size capabilities.
 */
private object RouteConfig {
    /**
     * Classifies a route based on its NavKey and context using type-safe route matching.
     * 
     * This function implements a multi-stage classification algorithm:
     * 
     * Classification Algorithm Flow:
     * 
     * 1. Main Tab Check: Is this route one of the primary app tabs?
     *    - If yes → RouteClassification.MainTab
     * 
     * 2. Exclusion Check: Should this route be handled elsewhere?
     *    - Onboarding flows → RouteClassification.Excluded (handled by NavDisplay)
     *    - Settings flows → RouteClassification.Excluded (handled by SettingsSceneStrategy)
     *    - Editor flows → RouteClassification.Excluded (handled by NavDisplay)
     *    - System flows → RouteClassification.Excluded (handled by NavDisplay)
     * 
     * 3. Two-Pane Detail Check: Can this detail show alongside its parent?
     *    - Timeline details with Timeline parent → RouteClassification.TwoPaneDetail
     *    - Other supported detail types → RouteClassification.TwoPaneDetail
     * 
     * 4. Default Fallback: All remaining routes
     *    - All other details → RouteClassification.FullscreenDetail
     * 
     * Decision Factors:
     * 
     * - Route Type: The specific NavKey class determines base behavior
     * - Previous Route Context: Parent route influences detail classification
     * - Content Requirements: Some content (rewinds, journals) always needs fullscreen
     * - UX Patterns: Consistent behavior across similar content types
     * 
     * @param routeKey The route to classify
     * @param previousRouteKey The previous route in the back stack (for context)
     * @return The appropriate RouteClassification for this route
     */
    fun classifyRoute(routeKey: NavKey, previousRouteKey: NavKey? = null): RouteClassification {
        // Debug logging for route classification
        Napier.v("RouteConfig: Classifying route $routeKey (previous: $previousRouteKey)")
        
        // Check if it's a main tab first
        HomeTab.entries.find { it.route == routeKey }?.let { tab ->
            Napier.v("RouteConfig: Route $routeKey classified as MainTab(${tab.title})")
            return RouteClassification.MainTab(tab)
        }
        
        // Check for excluded routes (should render without ANY navigation UI - not even fullscreen scenes)
        // Use type-safe matching instead of string comparison
        when (routeKey) {
            // Core navigation and startup
            is NavigationStart -> {
                Napier.v("RouteConfig: Route $routeKey classified as Excluded (NavigationStart)")
                return RouteClassification.Excluded
            }
            
            // Onboarding flows - all should be excluded (handled by NavDisplay directly)
            is OnboardingStart,
            is OnboardingSignIn, 
            is OnboardingEntryRoute,
            is OnboardingImportRoute,
            is OnboardingCompleteRoute,
            is OnboardingWelcomeBackRoute -> {
                Napier.v("RouteConfig: Route $routeKey classified as Excluded (Onboarding flow)")
                return RouteClassification.Excluded
            }
            
            // Settings flows - all should be excluded (handled by SettingsSceneStrategy)
            is SettingsOverviewRoute,
            is AccountSettingsRoute,
            is PrivacySettingsRoute,
            is DataSettingsRoute,
            is DangerZoneSettingsRoute,
            is BirthdaySettingsRoute,
            is LocationSettingsRoute -> {
                Napier.v("RouteConfig: Route $routeKey classified as Excluded (Settings flow)")
                return RouteClassification.Excluded
            }
            
            // Cloud account setup flows - all should be excluded
            is CloudAccountIntroRoute,
            is UsernameSelectionRoute,
            is DisplayNameSelectionRoute,
            is PasskeyCreationRoute,
            is AccountCreationCompletionRoute -> {
                Napier.v("RouteConfig: Route $routeKey classified as Excluded (Cloud account flow)")
                return RouteClassification.Excluded
            }
            
            // Editor flows - always excluded (handled by NavDisplay directly)
            is EntryEditor -> {
                Napier.v("RouteConfig: Route $routeKey classified as Excluded (Editor flow)")
                return RouteClassification.Excluded
            }
            
            // Default case for any other route not explicitly handled
            else -> { /* Continue to detail route classification */ }
        }
        
        // Check for detail routes that support two-pane mode
        when (routeKey) {
            is TimelineDetail -> {
                if (previousRouteKey == TimelineListRoute) {
                    val timelineTab = HomeTab.entries.first { it.route == TimelineListRoute }
                    Napier.v("RouteConfig: Route $routeKey classified as TwoPaneDetail for ${timelineTab.title}")
                    return RouteClassification.TwoPaneDetail(timelineTab)
                }
            }
        }
        
        // Check for routes that should have fullscreen detail experience
        // These are routes that need to be immersive but still part of the app's main flow
        val routeString = routeKey.toString()
        when {
            routeString.contains("RewindDetailRoute") -> {
                Napier.v("RouteConfig: Route $routeKey classified as FullscreenDetail (Rewind detail)")
                return RouteClassification.FullscreenDetail
            }
            routeString.startsWith("JournalDetail") -> {
                Napier.v("RouteConfig: Route $routeKey classified as FullscreenDetail (Journal detail)")
                return RouteClassification.FullscreenDetail
            }
            else -> {
                // All other detail routes are full-screen
                Napier.v("RouteConfig: Route $routeKey classified as FullscreenDetail (default)")
                return RouteClassification.FullscreenDetail
            }
        }
    }
    
    /**
     * Determines if a route should always be full-screen regardless of screen size.
     */
    fun isAlwaysFullscreen(routeKey: NavKey): Boolean {
        val routeString = routeKey.toString()
        return routeString.contains("RewindDetailRoute") || 
               routeString.startsWith("JournalDetail")
    }
}

/**
 * Factory for creating HomeScene instances with consistent configuration.
 * 
 * This factory encapsulates the creation logic for different scene types, ensuring
 * consistent key generation, entry management, and parameter passing. It serves as
 * the single source of truth for scene creation patterns.
 * 
 * ## Scene Creation Patterns
 * 
 * ### Scene Keys
 * All scenes use composite keys to ensure uniqueness and enable proper scene lifecycle:
 * - **Main Tab Scenes**: `Pair("HomeScene", entryKey)`
 * - **Two-Pane Scenes**: `Triple("HomeScene", mainEntryKey, detailEntryKey)`
 * - **Fullscreen Scenes**: `Pair("FullscreenScene", entryKey)`
 * 
 * ### Entry Management
 * The factory carefully manages which entries are included in each scene:
 * - **Single Entry Scenes**: Only the primary entry is included
 * - **Two-Pane Scenes**: Both main and detail entries are included
 * - **Fullscreen Scenes**: Only the content entry is included
 * 
 * ### Lifecycle Considerations
 * - Scenes are created fresh each time the back stack changes
 * - Scene keys enable Navigation 3 to detect when scenes can be reused
 * - Entry references are maintained to support proper navigation transitions
 */
private object HomeSceneFactory {
    /**
     * Creates a HomeScene for a main tab.
     */
    fun <T : Any> createMainTabScene(
        entry: NavEntry<T>,
        previousEntries: List<NavEntry<T>>,
        tab: HomeTab,
        onTabSelected: (HomeTab) -> Unit,
        onNewEntry: () -> Unit,
    ): HomeScene<T> = HomeScene(
        key = Pair("HomeScene", entry.key),
        previousEntries = previousEntries,
        mainEntry = entry,
        detailEntry = null,
        onTabSelected = onTabSelected,
        onNewEntry = onNewEntry,
        selectedTab = tab
    )
    
    /**
     * Creates a HomeScene for a two-pane detail view.
     */
    fun <T : Any> createTwoPaneScene(
        mainEntry: NavEntry<T>,
        detailEntry: NavEntry<T>,
        previousEntries: List<NavEntry<T>>,
        tab: HomeTab,
        onTabSelected: (HomeTab) -> Unit,
        onNewEntry: () -> Unit,
    ): HomeScene<T> = HomeScene(
        key = Triple("HomeScene", mainEntry.key, detailEntry.key),
        previousEntries = previousEntries,
        mainEntry = mainEntry,
        detailEntry = detailEntry,
        onTabSelected = onTabSelected,
        onNewEntry = onNewEntry,
        selectedTab = tab
    )
    
    /**
     * Creates a HomeScene for a full-screen detail view.
     */
    fun <T : Any> createFullscreenScene(
        entry: NavEntry<T>,
        previousEntries: List<NavEntry<T>>,
        tab: HomeTab,
        onTabSelected: (HomeTab) -> Unit,
        onNewEntry: () -> Unit,
    ): HomeScene<T> = HomeScene(
        key = Pair("HomeScene", entry.key),
        previousEntries = previousEntries,
        mainEntry = entry,
        detailEntry = null,
        onTabSelected = onTabSelected,
        onNewEntry = onNewEntry,
        selectedTab = tab
    )
    
    /**
     * Creates a FullscreenScene for truly immersive content without any navigation chrome.
     */
    fun <T : Any> createFullscreenDetailScene(
        entry: NavEntry<T>,
        previousEntries: List<NavEntry<T>>,
    ): FullscreenScene<T> = FullscreenScene(
        key = Pair("FullscreenScene", entry.key),
        previousEntries = previousEntries,
        entry = entry
    )
}

/**
 * Represents the different tabs in the HomeScene
 */
enum class HomeTab(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val route: NavKey,
) {
    TIMELINE("Timeline", Icons.Filled.History, Icons.Outlined.History, TimelineListRoute),
    JOURNALS("Journals", Icons.Filled.Book, Icons.Outlined.Book, JournalList),
    REWIND("Rewind", Icons.Filled.DateRange, Icons.Outlined.DateRange, RewindList)
}

/**
 * A custom [Scene] that implements an adaptive navigation pattern with the following features:
 *
 * 1. Navigation UI:
 *    - On smaller screens (<600dp): Bottom navigation bar
 *    - On larger screens (≥600dp): Side navigation rail
 *
 * 2. Content Layout:
 *    - On smaller screens (<600dp): Single-pane content that shows either:
 *      a) Main content (list/overview screens) when no detail is selected
 *      b) Full-screen detail content when a detail item is selected
 *    - On larger screens (≥600dp and <1240dp): Side rail + single content pane
 *    - On extra-large screens (≥1240dp): Side rail + two content panes (main + detail)
 *
 * 3. Special Cases:
 *    - Journal details always show in full-screen mode regardless of screen size
 *      (This is handled in the HomeSceneStrategy, not here)
 *    - FAB (Floating Action Button) placement adapts to the navigation style:
 *      a) For bottom navigation: Standard bottom-right position
 *      b) For side rail: At the bottom of the rail
 *
 * The Scene takes the following parameters:
 * @param key A unique identifier for this scene instance
 * @param previousEntries The entries that precede this scene in the navigation stack
 * @param mainEntry The primary entry to display (e.g., timeline list)
 * @param detailEntry Optional secondary entry to display (e.g., day detail)
 * @param onTabSelected Callback when a tab is selected
 * @param onNewEntry Callback when the "new entry" button is pressed
 * @param selectedTab The currently selected tab
 */
class HomeScene<T : Any>(
    override val key: Any,
    override val previousEntries: List<NavEntry<T>>,
    val mainEntry: NavEntry<T>,
    val detailEntry: NavEntry<T>? = null,
    val onTabSelected: (HomeTab) -> Unit,
    val onNewEntry: () -> Unit,
    val selectedTab: HomeTab,
) : Scene<T> {
    override val entries: List<NavEntry<T>> = if (detailEntry != null) {
        listOf(mainEntry, detailEntry)
    } else {
        listOf(mainEntry)
    }

    /**
     * The main content composable that renders the entire HomeScene layout.
     *
     * This method implements adaptive layouts based on screen size:
     *
     * 1. Screen size detection:
     *    - Uses WindowSizeClass to detect current screen dimensions
     *    - Sets flags for navigation rail and two-pane content based on breakpoints
     *
     * 2. Layout configurations:
     *    - Small screens (<600dp width):
     *      * Bottom navigation bar (only shown on main screens, hidden on detail screens)
     *      * Single content pane (either main or detail, never both)
     *      * FAB in standard bottom-right position (only shown on main screens)
     *      * Detail views replace main views completely
     *
     *    - Medium screens (≥600dp width, <1240dp width):
     *      * Side navigation rail (only shown on main screens, hidden on detail screens)
     *      * Single content pane
     *      * FAB at bottom of navigation rail
     *      * Detail views replace main views
     *
     *    - Large screens (≥1240dp width):
     *      * Side navigation rail (always shown)
     *      * Two content panes side-by-side (main + detail)
     *      * FAB at bottom of navigation rail
     *      * Main and detail views visible simultaneously
     *
     * 3. Navigation visibility rules:
     *    - Navigation controls (rail/bar) are hidden when showing detail-only views
     *    - This creates a cleaner, more focused detail experience
     *    - Exception: In two-pane mode, navigation is always visible
     *
     * 4. Special handling for detail views:
     *    When not in two-pane mode but a detail is being displayed:
     *    - The detail view takes over the entire content area
     *    - Main view is hidden completely (important for back navigation)
     *    - Navigation controls are hidden
     *    - This creates a fullscreen detail experience on smaller devices
     */
    override val content: @Composable (() -> Unit) = {
        // Wrap the entire content with AnimatedVisibility to provide AnimatedVisibilityScope
        AnimatedVisibility(visible = true) {
            CompositionLocalProvider(LocalAnimatedVisibilityScope provides this@AnimatedVisibility) {
                HomeSceneContent()
            }
        }
    }
    
    @Composable
    private fun HomeSceneContent() {
        val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
        val useNavigationRail =
            windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_MEDIUM_LOWER_BOUND)
        val useTwoPane =
            windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_EXPANDED_LOWER_BOUND) && detailEntry != null

        // Check if we're on a detail-only view (no main entry showing)
        val isDetailOnlyView = !useTwoPane && detailEntry != null

        // Create a SnackbarHostState to be used by the ResponsiveScaffold
        val snackbarHostState = rememberSnackbarHostState()

        ResponsiveScaffold(
            showNavigationRail = useNavigationRail && !isDetailOnlyView,
            showBottomNavigation = !useNavigationRail && !isDetailOnlyView,
            navigationRail = {
                LogDateNavigationRail(
                    selectedTab = selectedTab,
                    onTabSelected = onTabSelected,
                    headerContent = {
                        SharedElementFAB(
                            onClick = onNewEntry,
                            contentDescription = "New Entry"
                        )
                    }
                )
            },
            bottomNavigation = {
                LogDateBottomNavigationBar(
                    selectedTab = selectedTab,
                    onTabSelected = onTabSelected
                )
            },
            snackbarHost = {
                ResponsiveScaffoldDefaults.SnackbarHost(hostState = snackbarHostState)
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .padding(paddingValues)
                    .fillMaxSize()
            ) {
                if (useTwoPane) {
                    // Two-pane layout (large screens with both main and detail content)
                    Row(modifier = Modifier.fillMaxSize()) {
                        // Left content
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .widthIn(max = 360.dp)
                                .fillMaxHeight()
                        ) {
                            mainEntry.content.invoke(mainEntry.key)
                        }

                        // Detail pane
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            detailEntry?.content?.invoke(detailEntry.key)
                        }
                    }
                } else {
                    // Single-pane layout (smaller screens or journal details)
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (detailEntry != null) {
                            // If there's a detail entry but we're in single pane mode,
                            // show only the detail entry (fullscreen)
                            detailEntry.content.invoke(detailEntry.key)
                        } else {
                            // Otherwise show the main entry
                            mainEntry.content.invoke(mainEntry.key)
                        }

                        // TODO: Create a more robust FAB display system for all screen sizes
                        // Currently FAB is only visible in nav rail mode but not in bottom nav mode
                        // Need to implement consistent FAB visibility across all layouts

                        // Show FAB for bottom navigation (smaller screens)
                        // Only show when navigation is visible (not in detail-only view)
                        // and not using navigation rail
                        if (!useNavigationRail && !isDetailOnlyView) {
                            SharedElementFAB(
                                onClick = onNewEntry,
                                contentDescription = "New Entry",
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    companion object {
        private const val HOME_SCENE_KEY = "HomeScene"

        /**
         * Helper function to add metadata to a [NavEntry] indicating it can be displayed
         * in the home scene.
         */
        fun homeScene() = mapOf(HOME_SCENE_KEY to true)
    }
}

/**
 * A [Scene] that renders content in fullscreen mode without any navigation chrome.
 * 
 * This scene is designed for immersive experiences like rewind details, photo/video viewers,
 * or any content that should take over the entire screen without navigation UI.
 * 
 * ## Key Characteristics
 * - **No Navigation Chrome**: No navigation bars, rails, FABs, or app bars
 * - **Full Screen Content**: Content fills the entire screen including system UI areas
 * - **Immersive Experience**: Optimized for media consumption and storytelling
 * - **System UI Responsibility**: Content must handle its own system UI (status bars, etc.)
 * 
 * ## Scene Lifecycle
 * 
 * ### Creation
 * 1. Created by `HomeSceneStrategy` when route is classified as `FullscreenDetail`
 * 2. Scene key generated as `Pair("FullscreenScene", entryKey)` for uniqueness
 * 3. Only the target entry is included in the scene's entry list
 * 
 * ### Content Rendering
 * 1. Scene's content composable is invoked by Navigation 3
 * 2. Content composable directly calls `entry.content.invoke(entry.key)`
 * 3. No additional UI layers or decorations are applied
 * 
 * ### Destruction
 * 1. Scene is destroyed when user navigates back or to a different route
 * 2. Navigation 3 handles cleanup of the scene and its entries
 * 3. Back navigation returns to the previous scene in the stack
 * 
 * ## Integration Requirements
 * 
 * ### Content Expectations
 * Content displayed in FullscreenScene must:
 * - Handle its own system UI visibility (status bars, navigation bars)
 * - Provide its own back navigation UI or gestures
 * - Manage its own loading and error states
 * - Be responsive to different screen sizes and orientations
 * 
 * ### Navigation Integration
 * - Uses standard Navigation 3 back handling
 * - Supports shared element transitions when content is designed for it
 * - Integrates with the app's transition specifications
 * 
 * ## Use Cases
 * - **Story-style content**: Rewind experiences, photo carousels
 * - **Media viewers**: Full-screen photo and video viewing
 * - **Reading modes**: Journal entries in immersive reading mode
 * - **Game-like experiences**: Interactive content that needs full control
 * 
 * @param key A unique identifier for this scene instance (typically from factory)
 * @param previousEntries The entries that precede this scene in the navigation stack
 * @param entry The entry to display in fullscreen mode
 */
class FullscreenScene<T : Any>(
    override val key: Any,
    override val previousEntries: List<NavEntry<T>>,
    val entry: NavEntry<T>,
) : Scene<T> {
    override val entries: List<NavEntry<T>> = listOf(entry)
    
    override val content: @Composable (() -> Unit) = {
        // Render content directly without any navigation chrome or scaffolding
        // The content composable is responsible for its own layout and system UI handling
        entry.content.invoke(entry.key)
    }
}

/**
 * A [SceneStrategy] that determines when and how to activate scenes based on navigation state.
 *
 * This strategy implements the core logic for constructing adaptive layouts in the app,
 * serving as the bridge between Navigation 3's route management and the app's scene system.
 * 
 * ## Strategy Responsibilities
 *
 * ### 1. Back Stack Analysis
 * - Examines the current navigation back stack entries
 * - Identifies the current route and its relationship to previous routes
 * - Determines the appropriate scene type based on route classification
 *
 * ### 2. Scene Selection Logic
 * The strategy follows a hierarchical decision process:
 * 
 * ```
 * Navigation Entries → Route Classification → Scene Type Selection → Scene Creation
 * ```
 * 
 * - **MainTab routes** → `HomeScene` with navigation chrome
 * - **TwoPaneDetail routes** → `HomeScene` with adaptive layout (single/dual pane)
 * - **FullscreenDetail routes** → `FullscreenScene` without navigation chrome
 * - **Excluded routes** → `null` (handled by other strategies or NavDisplay)
 *
 * ### 3. Adaptive Layout Decisions
 * - **Small screens** (<600dp): Bottom navigation, single-pane content
 * - **Medium screens** (600dp-1240dp): Side rail, single-pane content
 * - **Large screens** (≥1240dp): Side rail, two-pane content (where supported)
 *
 * ### 4. Entry Management Patterns
 * Different scene configurations require different entry management:
 * - **Single-pane main tabs**: Only the main entry
 * - **Two-pane detail views**: Both main and detail entries
 * - **Fullscreen detail views**: Only the detail entry
 * - **Excluded routes**: No entries (handled elsewhere)
 *
 * ## Integration with Navigation 3
 * 
 * ### Strategy Lifecycle
 * 1. **Invocation**: Called by NavDisplay when back stack changes
 * 2. **Analysis**: Examines entries to determine current navigation context
 * 3. **Classification**: Routes are classified using RouteConfig logic
 * 4. **Creation**: Appropriate scene is created via HomeSceneFactory
 * 5. **Return**: Scene or null is returned to NavDisplay
 * 
 * ### Strategy Chaining
 * This strategy works as part of a chain in MainNavigationRoot:
 * ```kotlin
 * SettingsSceneStrategy → HomeSceneStrategy → NavDisplay Default
 * ```
 * 
 * ### State Management
 * - **Tab Selection**: Maintains selected tab state across navigation changes
 * - **Navigation Context**: Preserves user's place in the navigation hierarchy
 * - **Transition Context**: Provides appropriate scene keys for smooth transitions
 * 
 * ## Scene Creation Patterns
 * 
 * ### Main Tab Scenes
 * ```kotlin
 * TimelineListRoute → RouteClassification.MainTab → HomeScene (single pane)
 * ```
 * 
 * ### Two-Pane Detail Scenes  
 * ```kotlin
 * Timeline → TimelineDetail → RouteClassification.TwoPaneDetail → HomeScene (adaptive)
 * ```
 * 
 * ### Fullscreen Detail Scenes
 * ```kotlin
 * RewindList → RewindDetail → RouteClassification.FullscreenDetail → FullscreenScene
 * ```
 * 
 * ## Performance Considerations
 * - Scenes are created on-demand when back stack changes
 * - Scene keys enable Navigation 3 to reuse scenes when possible
 * - Entry references are maintained efficiently to support transitions
 * - Route classification logic is optimized for common navigation patterns
 * 
 * This strategy works with MainNavigationRoot's scene setup to provide a cohesive,
 * adaptive navigation experience that scales from phones to tablets to desktop.
 */
class HomeSceneStrategy<T : Any>(
    private val onTabSelected: (HomeTab) -> Unit,
    private val onNewEntry: () -> Unit,
    private val getSelectedTab: () -> HomeTab,
) : SceneStrategy<T> {
    /**
     * Determines if a HomeScene should be created for the current navigation state.
     *
     * This method analyzes the navigation back stack and makes decisions about:
     * 1. Whether to create a HomeScene (returns null if not applicable)
     * 2. How to configure the HomeScene (single pane, two pane, which entries to include)
     *
     * Decision flow:
     *
     * 1. Empty check:
     *    - If entries list is empty, return null (no scene to create)
     *
     * 2. Main tab check:
     *    - If the last entry is a main tab (Timeline, Rewind, Journals):
     *      * Create a HomeScene with just that entry
     *      * This represents the initial/base state of each tab
     *
     * 3. Detail screen handling:
     *    - If last entry is NOT a main tab (it's a detail view):
     *      * Check if previous entry is a main tab
     *      * Special case: For Journal details, ALWAYS use full-screen mode
     *        (mainEntry = detail, detailEntry = null)
     *      * For other tabs (Timeline, Rewind): Use main+detail in a HomeScene
     *        which will either render as two-pane or single-pane based on screen size
     *
     * @param entries The current navigation back stack entries
     * @param onBack Callback for when back navigation occurs
     * @return A HomeScene if appropriate for the current navigation state, or null
     */
    @Composable
    override fun calculateScene(
        entries: List<NavEntry<T>>,
        onBack: (Int) -> Unit,
    ): Scene<T>? {
        if (entries.isEmpty()) {
            Napier.v("HomeSceneStrategy: No entries, returning null")
            return null
        }

        val lastEntry = entries.last()
        val previousEntry = entries.getOrNull(entries.size - 2)
        
        Napier.v("HomeSceneStrategy: Processing ${entries.size} entries, last: ${lastEntry.key}, previous: ${previousEntry?.key}")
        
        // Classify the current route
        val classification = RouteConfig.classifyRoute(lastEntry.key as NavKey, previousEntry?.key as NavKey?)
        
        return when (classification) {
            is RouteClassification.MainTab -> {
                Napier.v("HomeSceneStrategy: Creating MainTab scene for ${classification.tab.title}")
                HomeSceneFactory.createMainTabScene(
                    entry = lastEntry,
                    previousEntries = entries.dropLast(1),
                    tab = classification.tab,
                    onTabSelected = onTabSelected,
                    onNewEntry = onNewEntry
                )
            }
            
            is RouteClassification.TwoPaneDetail -> {
                if (previousEntry != null && !RouteConfig.isAlwaysFullscreen(lastEntry.key as NavKey)) {
                    Napier.v("HomeSceneStrategy: Creating TwoPane scene for ${classification.parentTab.title}")
                    // Create two-pane layout for supported detail views
                    HomeSceneFactory.createTwoPaneScene(
                        mainEntry = previousEntry,
                        detailEntry = lastEntry,
                        previousEntries = entries.dropLast(2),
                        tab = classification.parentTab,
                        onTabSelected = onTabSelected,
                        onNewEntry = onNewEntry
                    )
                } else {
                    Napier.v("HomeSceneStrategy: Creating Fullscreen scene for ${classification.parentTab.title} (TwoPane fallback)")
                    // Fall back to fullscreen if conditions aren't met
                    HomeSceneFactory.createFullscreenScene(
                        entry = lastEntry,
                        previousEntries = entries.dropLast(1),
                        tab = classification.parentTab,
                        onTabSelected = onTabSelected,
                        onNewEntry = onNewEntry
                    )
                }
            }
            
            is RouteClassification.FullscreenDetail -> {
                Napier.v("HomeSceneStrategy: Creating FullscreenDetailScene (no navigation chrome)")
                HomeSceneFactory.createFullscreenDetailScene(
                    entry = lastEntry,
                    previousEntries = entries.dropLast(1)
                )
            }
            
            RouteClassification.Excluded -> {
                Napier.v("HomeSceneStrategy: Route excluded, returning null (no home navigation UI)")
                // Return null to let NavDisplay handle these routes without home navigation UI
                null
            }
        }
    }
}

/**
 * A shared element FAB that participates in the FAB-to-editor transition.
 * This composable encapsulates the shared element logic for consistent use
 * across different FAB instances (navigation rail and bottom navigation).
 */
@Composable
private fun SharedElementFAB(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalAnimatedVisibilityScope.current
    
    FloatingActionButton(
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = when {
            sharedTransitionScope != null && animatedVisibilityScope != null -> {
                // TODO: Fix shared element API compatibility
                // with(sharedTransitionScope) {
                //     modifier.sharedElement(
                //         rememberSharedContentState(key = FAB_TO_EDITOR_SHARED_ELEMENT_KEY),
                //         animatedVisibilityScope = animatedVisibilityScope
                //     )
                // }
                modifier
            }
            else -> modifier
        }
    ) {
        Icon(
            Icons.Filled.Add,
            contentDescription = contentDescription,
            modifier = Modifier.size(24.dp)
        )
    }
}