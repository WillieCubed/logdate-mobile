@file:OptIn(ExperimentalSharedTransitionApi::class)

package app.logdate.navigation.scenes

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.ui.Scene
import androidx.navigation3.ui.SceneStrategy
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_EXPANDED_LOWER_BOUND
import app.logdate.feature.core.settings.ui.LocalSettingsLayoutInfo
import app.logdate.feature.core.settings.ui.SettingsLayoutInfo
import app.logdate.navigation.routes.core.AccountSettingsRoute
import app.logdate.navigation.routes.core.AdvancedSettingsRoute
import app.logdate.navigation.routes.core.BirthdaySettingsRoute
import app.logdate.navigation.routes.core.DangerZoneSettingsRoute
import app.logdate.navigation.routes.core.DataSettingsRoute
import app.logdate.navigation.routes.core.DevicesSettingsRoute
import app.logdate.navigation.routes.core.LocationSettingsRoute
import app.logdate.navigation.routes.core.PrivacySettingsRoute
import app.logdate.navigation.routes.core.SettingsOverviewRoute

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
        return when (routeKey) {
            is SettingsOverviewRoute -> SettingsRouteClassification.SettingsList

            // Detail settings screens
            is AccountSettingsRoute,
            is PrivacySettingsRoute,
            is DataSettingsRoute,
            is DevicesSettingsRoute,
            is LocationSettingsRoute,
            is BirthdaySettingsRoute,
            is DangerZoneSettingsRoute,
            is AdvancedSettingsRoute -> SettingsRouteClassification.SettingsDetail

            else -> SettingsRouteClassification.Excluded
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

private fun resolveSelectedDetail(routeKey: NavKey?): String? {
    return when (routeKey) {
        is AccountSettingsRoute -> "account"
        is PrivacySettingsRoute -> "privacy"
        is DataSettingsRoute -> "data"
        is DevicesSettingsRoute -> "devices"
        is LocationSettingsRoute -> "location"
        is DangerZoneSettingsRoute -> "danger"
        is BirthdaySettingsRoute -> "birthday"
        is AdvancedSettingsRoute -> "advanced"
        else -> null
    }
}

/**
 * Creates a SettingsScene for the settings list only.
 */
private fun <T : Any> createListOnlySettingsScene(
    entry: NavEntry<T>,
    previousEntries: List<NavEntry<T>>,
    onBack: (Int) -> Unit,
): SettingsScene<T> = SettingsScene(
    key = Pair("SettingsScene", entry.key),
    previousEntries = previousEntries,
    listEntry = entry,
    detailEntry = null,
    onBack = onBack
)

/**
 * Creates a SettingsScene for list-detail layout.
 */
private fun <T : Any> createListDetailSettingsScene(
    listEntry: NavEntry<T>,
    detailEntry: NavEntry<T>,
    previousEntries: List<NavEntry<T>>,
    onBack: (Int) -> Unit,
): SettingsScene<T> = SettingsScene(
    key = Triple("SettingsScene", listEntry.key, detailEntry.key),
    previousEntries = previousEntries,
    listEntry = listEntry,
    detailEntry = detailEntry,
    onBack = onBack
)

/**
 * Creates a SettingsScene for full-screen detail view.
 */
private fun <T : Any> createDetailOnlySettingsScene(
    entry: NavEntry<T>,
    previousEntries: List<NavEntry<T>>,
    onBack: (Int) -> Unit,
): SettingsScene<T> = SettingsScene(
    key = Pair("SettingsScene", entry.key),
    previousEntries = previousEntries,
    listEntry = entry,
    detailEntry = null,
    onBack = onBack
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
    private val onBack: (Int) -> Unit,
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
        val hasListDetailContext = detailEntry != null && listEntry.key is SettingsOverviewRoute
        val adaptiveInfo = currentWindowAdaptiveInfo()
        val windowSizeClass = adaptiveInfo.windowSizeClass
        val isInTwoPaneMode = windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_EXPANDED_LOWER_BOUND) &&
            listEntry.key is SettingsOverviewRoute
        val selectedDetail = resolveSelectedDetail(detailEntry?.key as? NavKey)

        // Navigate to detail pane when we have a detail entry
        LaunchedEffect(detailEntry) {
            if (detailEntry != null) {
                navigator.navigateTo(ListDetailPaneScaffoldRole.Detail)
            }
        }

        // Ensure back navigation pops detail first when we have list+detail on the stack.
        BackHandler(enabled = hasListDetailContext) {
            onBack(1)
        }

        ListDetailPaneScaffold(
            directive = navigator.scaffoldDirective,
            value = navigator.scaffoldValue,
            listPane = {
                AnimatedPane {
                    CompositionLocalProvider(
                        LocalSettingsLayoutInfo provides SettingsLayoutInfo(
                            isInTwoPaneMode = isInTwoPaneMode,
                            selectedDetail = selectedDetail,
                            isDetailPane = false
                        )
                    ) {
                        listEntry.content.invoke(listEntry.key)
                    }
                }
            },
            detailPane = {
                AnimatedPane {
                    CompositionLocalProvider(
                        LocalSettingsLayoutInfo provides SettingsLayoutInfo(
                            isInTwoPaneMode = isInTwoPaneMode,
                            selectedDetail = selectedDetail,
                            isDetailPane = true
                        )
                    ) {
                        detailEntry?.content?.invoke(detailEntry.key)
                            ?: SettingsEmptyDetailPane()
                    }
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
        if (entries.isEmpty()) return null

        val lastEntry = entries.last()
        val previousEntry = entries.getOrNull(entries.size - 2)

        // Classify the current route
        val classification = SettingsRouteConfig.classifyRoute(lastEntry.key as NavKey)

        return when (classification) {
            SettingsRouteClassification.SettingsList -> {
                createListOnlySettingsScene(
                    entry = lastEntry,
                    previousEntries = entries.dropLast(1),
                    onBack = onBack
                )
            }

            SettingsRouteClassification.SettingsDetail -> {
                // Check if we have a list entry as the previous entry
                val listEntry = previousEntry?.takeIf {
                    SettingsRouteConfig.classifyRoute(it.key as NavKey) == SettingsRouteClassification.SettingsList
                }

                // Always create list-detail scene when we have both entries.
                // The Scene's content uses ListDetailPaneScaffold which handles
                // adaptive layout internally based on screen size.
                if (listEntry != null && !SettingsRouteConfig.isAlwaysFullscreen(lastEntry.key as NavKey)) {
                    createListDetailSettingsScene(
                        listEntry = listEntry,
                        detailEntry = lastEntry,
                        previousEntries = entries.dropLast(2),
                        onBack = onBack
                    )
                } else {
                    createDetailOnlySettingsScene(
                        entry = lastEntry,
                        previousEntries = entries.dropLast(1),
                        onBack = onBack
                    )
                }
            }

            SettingsRouteClassification.Excluded -> null
        }
    }
}
