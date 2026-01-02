@file:OptIn(ExperimentalSharedTransitionApi::class)

package app.logdate.navigation.scenes

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.ui.Scene
import androidx.navigation3.ui.SceneStrategy
import app.logdate.navigation.routes.core.AccountSettingsRoute
import app.logdate.navigation.routes.core.BirthdaySettingsRoute
import app.logdate.navigation.routes.core.DangerZoneSettingsRoute
import app.logdate.navigation.routes.core.DataSettingsRoute
import app.logdate.navigation.routes.core.LocationSettingsRoute
import app.logdate.navigation.routes.core.PrivacySettingsRoute
import app.logdate.navigation.routes.core.SettingsOverviewRoute
import io.github.aakira.napier.Napier

/**
 * CompositionLocal for providing AnimatedVisibilityScope throughout the settings scene.
 */
val LocalSettingsAnimatedVisibilityScope = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * Defines how different settings route types should be handled by the SettingsSceneStrategy.
 */
sealed class SettingsRouteClassification {
    /**
     * The main settings overview/list that should be displayed as the list pane.
     */
    data object SettingsList : SettingsRouteClassification()
    
    /**
     * Detail routes that should be displayed in the detail pane alongside the list.
     */
    data object SettingsDetail : SettingsRouteClassification()
    
    /**
     * Routes that should be excluded from SettingsScene entirely.
     */
    data object Excluded : SettingsRouteClassification()
}

/**
 * Configuration for settings route classification and scene behavior.
 */
private object SettingsRouteConfig {
    /**
     * Classifies a settings route based on its NavKey.
     */
    fun classifyRoute(routeKey: NavKey): SettingsRouteClassification {
        Napier.v("SettingsRouteConfig: Classifying route $routeKey")
        
        return when (routeKey) {
            is SettingsOverviewRoute -> {
                Napier.v("SettingsRouteConfig: Route $routeKey classified as SettingsList")
                SettingsRouteClassification.SettingsList
            }
            
            // Detail settings screens
            is AccountSettingsRoute,
            is PrivacySettingsRoute,
            is DataSettingsRoute,
            is LocationSettingsRoute,
            is BirthdaySettingsRoute,
            is DangerZoneSettingsRoute -> {
                Napier.v("SettingsRouteConfig: Route $routeKey classified as SettingsDetail")
                SettingsRouteClassification.SettingsDetail
            }
            
            else -> {
                Napier.v("SettingsRouteConfig: Route $routeKey classified as Excluded")
                SettingsRouteClassification.Excluded
            }
        }
    }
    
    /**
     * Determines if a route should always be full-screen regardless of screen size.
     */
    fun isAlwaysFullscreen(routeKey: NavKey): Boolean {
        // For now, only birthday settings should be fullscreen due to its specialized UI
        return routeKey is BirthdaySettingsRoute
    }
}

/**
 * Creates a SettingsScene for the settings list only.
 */
private fun <T : Any> createListOnlySettingsScene(
    entry: NavEntry<T>,
    previousEntries: List<NavEntry<T>>,
): SettingsScene<T> = SettingsScene(
    key = Pair("SettingsScene", entry.key),
    previousEntries = previousEntries,
    listEntry = entry,
    detailEntry = null
)

/**
 * Creates a SettingsScene for list-detail layout.
 */
private fun <T : Any> createListDetailSettingsScene(
    listEntry: NavEntry<T>,
    detailEntry: NavEntry<T>,
    previousEntries: List<NavEntry<T>>,
): SettingsScene<T> = SettingsScene(
    key = Triple("SettingsScene", listEntry.key, detailEntry.key),
    previousEntries = previousEntries,
    listEntry = listEntry,
    detailEntry = detailEntry
)

/**
 * Creates a SettingsScene for full-screen detail view.
 */
private fun <T : Any> createDetailOnlySettingsScene(
    entry: NavEntry<T>,
    previousEntries: List<NavEntry<T>>,
): SettingsScene<T> = SettingsScene(
    key = Pair("SettingsScene", entry.key),
    previousEntries = previousEntries,
    listEntry = entry,
    detailEntry = null
)

/**
 * A custom [Scene] that implements a list-detail adaptive navigation pattern for settings.
 *
 * This scene provides the following adaptive behavior:
 *
 * 1. Layout Behavior:
 *    - Small screens (<840dp): Single-pane showing either list OR detail
 *    - Large screens (≥840dp): Two-pane showing list AND detail side-by-side
 *
 * 2. Content Management:
 *    - List pane: Always shows SettingsOverviewScreen when in two-pane mode
 *    - Detail pane: Shows the selected settings detail screen
 *    - Empty state: Shows placeholder when no detail is selected on large screens
 *
 * 3. Navigation:
 *    - Maintains standard back navigation behavior
 *    - Handles transitions between list and detail appropriately per screen size
 *
 * @param key A unique identifier for this scene instance
 * @param previousEntries The entries that precede this scene in the navigation stack
 * @param listEntry The settings overview/list entry
 * @param detailEntry Optional detail entry for two-pane layouts
 */
class SettingsScene<T : Any>(
    override val key: Any,
    override val previousEntries: List<NavEntry<T>>,
    val listEntry: NavEntry<T>,
    val detailEntry: NavEntry<T>? = null,
) : Scene<T> {
    override val entries: List<NavEntry<T>> = if (detailEntry != null) {
        listOf(listEntry, detailEntry)
    } else {
        listOf(listEntry)
    }

    override val content: @Composable (() -> Unit) = {
        // Wrap the entire content with AnimatedVisibility to provide AnimatedVisibilityScope
        AnimatedVisibility(visible = true) {
            CompositionLocalProvider(LocalSettingsAnimatedVisibilityScope provides this@AnimatedVisibility) {
                SettingsSceneContent()
            }
        }
    }
    
    @OptIn(ExperimentalMaterial3AdaptiveApi::class)
    @Composable
    private fun SettingsSceneContent() {
        val navigator = rememberListDetailPaneScaffoldNavigator<Nothing>()
        val coroutineScope = rememberCoroutineScope()

        // Navigate to detail pane when we have a detail entry
        LaunchedEffect(detailEntry) {
            if (detailEntry != null) {
                navigator.navigateTo(ListDetailPaneScaffoldRole.Detail)
            }
        }

        ListDetailPaneScaffold(
            directive = navigator.scaffoldDirective,
            value = navigator.scaffoldValue,
            listPane = {
                AnimatedPane {
                    listEntry.content.invoke(listEntry.key)
                }
            },
            detailPane = {
                AnimatedPane {
                    detailEntry?.content?.invoke(detailEntry.key)
                        ?: SettingsEmptyDetailPane()
                }
            },
        )
    }

    companion object {
        private const val SETTINGS_SCENE_KEY = "SettingsScene"

        /**
         * Helper function to add metadata to a [NavEntry] indicating it can be displayed
         * in the settings scene.
         */
        fun settingsScene() = mapOf(SETTINGS_SCENE_KEY to true)
    }
}

/**
 * A placeholder composable shown in the detail pane when no specific setting is selected.
 */
@Composable
private fun SettingsEmptyDetailPane() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        androidx.compose.material3.Text(
            text = "Select a setting to configure",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * A [SceneStrategy] that determines when and how to activate a [SettingsScene] for adaptive settings navigation.
 *
 * This strategy implements the core logic for list-detail adaptive layouts in settings:
 *
 * 1. Route Analysis:
 *    - Identifies settings overview (list) vs settings detail routes
 *    - Determines appropriate scene configuration based on navigation state
 *
 * 2. Adaptive Layout Logic:
 *    - Single settings route: Shows list-only scene
 *    - List + Detail routes: Creates list-detail scene for large screens, detail-only for small screens
 *    - Fullscreen routes: Forces single-pane mode regardless of screen size
 *
 * 3. Entry Management:
 *    - Carefully manages which entries are included in scene's entries list
 *    - Handles transitions between different settings screens appropriately
 */
class SettingsSceneStrategy<T : Any> : SceneStrategy<T> {
    
    @Composable
    override fun calculateScene(
        entries: List<NavEntry<T>>,
        onBack: (Int) -> Unit,
    ): Scene<T>? {
        if (entries.isEmpty()) {
            Napier.v("SettingsSceneStrategy: No entries, returning null")
            return null
        }

        val lastEntry = entries.last()
        val previousEntry = entries.getOrNull(entries.size - 2)
        
        Napier.v("SettingsSceneStrategy: Processing ${entries.size} entries, last: ${lastEntry.key}, previous: ${previousEntry?.key}")
        
        // Classify the current route
        val classification = SettingsRouteConfig.classifyRoute(lastEntry.key as NavKey)
        
        return when (classification) {
            SettingsRouteClassification.SettingsList -> {
                Napier.v("SettingsSceneStrategy: Creating list-only scene")
                createListOnlySettingsScene(
                    entry = lastEntry,
                    previousEntries = entries.dropLast(1)
                )
            }
            
            SettingsRouteClassification.SettingsDetail -> {
                // Check if we have a list entry as the previous entry
                val listEntry = previousEntry?.takeIf { 
                    SettingsRouteConfig.classifyRoute(it.key as NavKey) == SettingsRouteClassification.SettingsList 
                }
                
                if (listEntry != null && !SettingsRouteConfig.isAlwaysFullscreen(lastEntry.key as NavKey)) {
                    Napier.v("SettingsSceneStrategy: Creating list-detail scene")
                    // Create list-detail scene for supported layouts
                    createListDetailSettingsScene(
                        listEntry = listEntry,
                        detailEntry = lastEntry,
                        previousEntries = entries.dropLast(2)
                    )
                } else {
                    Napier.v("SettingsSceneStrategy: Creating detail-only scene (fullscreen or no list context)")
                    // Fall back to detail-only for fullscreen routes or when no list context
                    createDetailOnlySettingsScene(
                        entry = lastEntry,
                        previousEntries = entries.dropLast(1)
                    )
                }
            }
            
            SettingsRouteClassification.Excluded -> {
                Napier.v("SettingsSceneStrategy: Route excluded, returning null")
                // Return null to let NavDisplay handle these routes without settings scene UI
                null
            }
        }
    }
}