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
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
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
import app.logdate.navigation.routes.routeClass
import kotlin.reflect.KClass
import org.jetbrains.compose.resources.stringResource
import logdate.app.composemain.generated.resources.*
import logdate.app.composemain.generated.resources.Res
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
    fun classifyRoute(routeClass: KClass<out NavKey>?): SettingsRouteClassification {
        return when (routeClass) {
            SettingsOverviewRoute::class -> SettingsRouteClassification.SettingsList

            // Detail settings screens
            AccountSettingsRoute::class,
            PrivacySettingsRoute::class,
            DataSettingsRoute::class,
            DevicesSettingsRoute::class,
            LocationSettingsRoute::class,
            BirthdaySettingsRoute::class,
            DangerZoneSettingsRoute::class,
            AdvancedSettingsRoute::class -> SettingsRouteClassification.SettingsDetail

            else -> SettingsRouteClassification.Excluded
        }
    }
    
    /**
     * Determines if a route should always be full-screen regardless of screen size.
     */
    fun isAlwaysFullscreen(routeClass: KClass<out NavKey>?): Boolean {
        // For now, only birthday settings should be fullscreen due to its specialized UI
        return routeClass == BirthdaySettingsRoute::class
    }
}

private fun resolveSelectedDetail(routeClass: KClass<out NavKey>?): String? {
    return when (routeClass) {
        AccountSettingsRoute::class -> "account"
        PrivacySettingsRoute::class -> "privacy"
        DataSettingsRoute::class -> "data"
        DevicesSettingsRoute::class -> "devices"
        LocationSettingsRoute::class -> "location"
        DangerZoneSettingsRoute::class -> "danger"
        BirthdaySettingsRoute::class -> "birthday"
        AdvancedSettingsRoute::class -> "advanced"
        else -> null
    }
}

/**
 * Creates a SettingsScene for the settings list only.
 */
private fun <T : NavKey> createListOnlySettingsScene(
    entry: NavEntry<T>,
    previousEntries: List<NavEntry<T>>,
    onBack: () -> Unit,
): SettingsScene<T> = SettingsScene(
    key = Pair("SettingsScene", entry.contentKey),
    previousEntries = previousEntries,
    listEntry = entry,
    detailEntry = null,
    onBack = onBack
)

/**
 * Creates a SettingsScene for list-detail layout.
 */
private fun <T : NavKey> createListDetailSettingsScene(
    listEntry: NavEntry<T>,
    detailEntry: NavEntry<T>,
    previousEntries: List<NavEntry<T>>,
    onBack: () -> Unit,
): SettingsScene<T> = SettingsScene(
    key = Triple("SettingsScene", listEntry.contentKey, detailEntry.contentKey),
    previousEntries = previousEntries,
    listEntry = listEntry,
    detailEntry = detailEntry,
    onBack = onBack
)

/**
 * Creates a SettingsScene for full-screen detail view.
 */
private fun <T : NavKey> createDetailOnlySettingsScene(
    entry: NavEntry<T>,
    previousEntries: List<NavEntry<T>>,
    onBack: () -> Unit,
): SettingsScene<T> = SettingsScene(
    key = Pair("SettingsScene", entry.contentKey),
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
class SettingsScene<T : NavKey>(
    override val key: Any,
    override val previousEntries: List<NavEntry<T>>,
    val listEntry: NavEntry<T>,
    val detailEntry: NavEntry<T>? = null,
    private val onBack: () -> Unit,
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
        val hasListDetailContext = detailEntry != null && listEntry.routeClass() == SettingsOverviewRoute::class
        val adaptiveInfo = currentWindowAdaptiveInfo()
        val windowSizeClass = adaptiveInfo.windowSizeClass
        val isInTwoPaneMode = windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_EXPANDED_LOWER_BOUND) &&
            listEntry.routeClass() == SettingsOverviewRoute::class
        val selectedDetail = resolveSelectedDetail(detailEntry?.routeClass())

        // Navigate to detail pane when we have a detail entry
        LaunchedEffect(detailEntry) {
            if (detailEntry != null) {
                navigator.navigateTo(ListDetailPaneScaffoldRole.Detail)
            }
        }

        // Ensure back navigation pops detail first when we have list+detail on the stack.
        BackHandler(enabled = hasListDetailContext) {
            onBack()
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
                        listEntry.Content()
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
                        detailEntry?.Content()
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
            text = stringResource(Res.string.select_a_setting_to_configure),
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
class SettingsSceneStrategy<T : NavKey> : SceneStrategy<T> {
    
    override fun SceneStrategyScope<T>.calculateScene(
        entries: List<NavEntry<T>>,
    ): Scene<T>? {
        if (entries.isEmpty()) return null

        val lastEntry = entries.last()
        val previousEntry = entries.getOrNull(entries.size - 2)

        // Classify the current route
        val classification = SettingsRouteConfig.classifyRoute(lastEntry.routeClass())

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
                    SettingsRouteConfig.classifyRoute(it.routeClass()) == SettingsRouteClassification.SettingsList
                }

                // Always create list-detail scene when we have both entries.
                // The Scene's content uses ListDetailPaneScaffold which handles
                // adaptive layout internally based on screen size.
                if (listEntry != null && !SettingsRouteConfig.isAlwaysFullscreen(lastEntry.routeClass())) {
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
