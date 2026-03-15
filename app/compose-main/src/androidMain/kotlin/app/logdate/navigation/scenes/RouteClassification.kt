package app.logdate.navigation.scenes

import androidx.navigation3.runtime.NavKey
import app.logdate.navigation.routes.CloudAccountSetupFlowRoute
import app.logdate.navigation.routes.core.AccountSettingsRoute
import app.logdate.navigation.routes.core.AdvancedSettingsRoute
import app.logdate.navigation.routes.core.DangerZoneSettingsRoute
import app.logdate.navigation.routes.core.DataSettingsRoute
import app.logdate.navigation.routes.core.DevicesSettingsRoute
import app.logdate.navigation.routes.core.EntryEditor
import app.logdate.navigation.routes.core.ExportSettingsRoute
import app.logdate.navigation.routes.core.JournalDetail
import app.logdate.navigation.routes.core.JournalList
import app.logdate.navigation.routes.core.LibraryListRoute
import app.logdate.navigation.routes.core.LibraryMediaDetailRoute
import app.logdate.navigation.routes.core.LocationAdvancedRoute
import app.logdate.navigation.routes.core.LocationIntervalRoute
import app.logdate.navigation.routes.core.LocationSettingsRoute
import app.logdate.navigation.routes.core.LocationTrackingOptionsRoute
import app.logdate.navigation.routes.core.NavigationStart
import app.logdate.navigation.routes.core.OnboardingCompleteRoute
import app.logdate.navigation.routes.core.OnboardingEntryRoute
import app.logdate.navigation.routes.core.OnboardingImportRoute
import app.logdate.navigation.routes.core.OnboardingSignIn
import app.logdate.navigation.routes.core.OnboardingStart
import app.logdate.navigation.routes.core.OnboardingWelcomeBackRoute
import app.logdate.navigation.routes.core.PrivacySettingsRoute
import app.logdate.navigation.routes.core.RewindDetailRoute
import app.logdate.navigation.routes.core.SettingsOverviewRoute
import app.logdate.navigation.routes.core.SyncSettingsRoute
import app.logdate.navigation.routes.core.TimelineDetail
import app.logdate.navigation.routes.core.TimelineListRoute
import kotlin.reflect.KClass

/**
 * Defines how different route types should be handled by the [HomeSceneStrategy].
 */
sealed class RouteClassification {
    /**
     * Main tab routes that should be displayed with navigation UI.
     *
     * @param tab The specific tab this route represents
     */
    data class MainTab(
        val tab: HomeTab,
    ) : RouteClassification()

    /**
     * Detail routes that should be displayed in two-pane mode on large screens,
     * with both main and detail content visible.
     *
     * On smaller screens, these routes display as full-screen details with hidden navigation.
     * On expanded screens (>=840dp), they display side-by-side with their parent tab.
     *
     * @param parentTab The main tab that this detail belongs to
     */
    data class TwoPaneDetail(
        val parentTab: HomeTab,
    ) : RouteClassification()

    /**
     * Detail routes that should always be displayed full-screen without navigation UI.
     */
    data object FullscreenDetail : RouteClassification()

    /**
     * Routes that should be excluded from HomeScene entirely.
     *
     * These routes are handled by other scene strategies or by NavDisplay directly.
     */
    data object Excluded : RouteClassification()
}

/**
 * Configuration for route classification and scene behavior.
 */
object RouteConfig {
    /**
     * Classifies a route based on its NavKey type and context.
     *
     * @param routeClass The route to classify
     * @param previousRouteClass The previous route in the back stack (for context)
     * @return The appropriate RouteClassification for this route
     */
    fun classifyRoute(
        routeClass: KClass<out NavKey>?,
        previousRouteClass: KClass<out NavKey>? = null,
    ): RouteClassification {
        val route = routeClass ?: return RouteClassification.FullscreenDetail

        // Check if it's a main tab first
        HomeTab.entries.find { it.route::class == route }?.let { tab ->
            return RouteClassification.MainTab(tab)
        }

        // Check for excluded routes
        when (route) {
            NavigationStart::class -> return RouteClassification.MainTab(HomeTab.TIMELINE)

            // Onboarding flows
            OnboardingStart::class,
            OnboardingSignIn::class,
            OnboardingEntryRoute::class,
            OnboardingImportRoute::class,
            OnboardingCompleteRoute::class,
            OnboardingWelcomeBackRoute::class,
            -> return RouteClassification.Excluded

            // Settings flows
            SettingsOverviewRoute::class,
            AccountSettingsRoute::class,
            PrivacySettingsRoute::class,
            DataSettingsRoute::class,
            DevicesSettingsRoute::class,
            DangerZoneSettingsRoute::class,
            LocationSettingsRoute::class,
            LocationTrackingOptionsRoute::class,
            LocationIntervalRoute::class,
            LocationAdvancedRoute::class,
            AdvancedSettingsRoute::class,
            SyncSettingsRoute::class,
            ExportSettingsRoute::class,
            -> return RouteClassification.Excluded

            // Cloud account setup flow
            CloudAccountSetupFlowRoute::class,
            -> return RouteClassification.Excluded

            // Editor flows
            EntryEditor::class -> return RouteClassification.Excluded

            else -> { /* Continue to detail route classification */ }
        }

        // Check for detail routes that support two-pane mode
        when (route) {
            TimelineDetail::class -> {
                if (previousRouteClass == TimelineListRoute::class) {
                    val timelineTab = HomeTab.entries.first { it.route::class == TimelineListRoute::class }
                    return RouteClassification.TwoPaneDetail(timelineTab)
                }
            }
            JournalDetail::class -> {
                if (previousRouteClass == JournalList::class) {
                    val journalsTab = HomeTab.entries.first { it.route::class == JournalList::class }
                    return RouteClassification.TwoPaneDetail(journalsTab)
                }
            }
            LibraryMediaDetailRoute::class -> {
                if (previousRouteClass == LibraryListRoute::class) {
                    val libraryTab = HomeTab.entries.first { it.route::class == LibraryListRoute::class }
                    return RouteClassification.TwoPaneDetail(libraryTab)
                }
            }
        }

        // All other detail routes are full-screen
        return RouteClassification.FullscreenDetail
    }

    /**
     * Determines if a route should always be full-screen regardless of screen size.
     */
    fun isAlwaysFullscreen(routeClass: KClass<out NavKey>?): Boolean = routeClass == RewindDetailRoute::class
}
