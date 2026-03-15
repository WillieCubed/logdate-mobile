package app.logdate.navigation.scenes

import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import app.logdate.navigation.routes.routeClass

/**
 * A [SceneStrategy] that determines when and how to activate scenes based on navigation state.
 *
 * This strategy analyzes the navigation back stack, classifies routes, and creates
 * the appropriate scene type (HomeScene, ListDetailHomeScene, or FullscreenScene).
 */
class HomeSceneStrategy<T : NavKey>(
    private val onTabSelected: (HomeTab) -> Unit,
    private val onNewEntry: () -> Unit,
    private val getSelectedTab: () -> HomeTab,
    private val getVisibleTabs: () -> List<HomeTab> = { HomeTab.visibleEntries },
) : SceneStrategy<T> {
    override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? {
        if (entries.isEmpty()) return null

        val lastEntry = entries.last()
        val previousEntry = entries.getOrNull(entries.size - 2)

        val classification =
            RouteConfig.classifyRoute(
                lastEntry.routeClass(),
                previousEntry?.routeClass(),
            )

        return when (classification) {
            is RouteClassification.MainTab -> {
                createMainTabHomeScene(
                    entry = lastEntry,
                    previousEntries = entries.dropLast(1),
                    tab = classification.tab,
                    onTabSelected = onTabSelected,
                    onNewEntry = onNewEntry,
                    visibleTabs = getVisibleTabs(),
                )
            }

            is RouteClassification.TwoPaneDetail -> {
                if (previousEntry != null && !RouteConfig.isAlwaysFullscreen(lastEntry.routeClass())) {
                    createTwoPaneHomeScene(
                        mainEntry = previousEntry,
                        detailEntry = lastEntry,
                        previousEntries = entries.dropLast(1),
                        tab = classification.parentTab,
                        onTabSelected = onTabSelected,
                        onNewEntry = onNewEntry,
                        visibleTabs = getVisibleTabs(),
                    )
                } else {
                    createFullscreenHomeScene(
                        entry = lastEntry,
                        previousEntries = entries.dropLast(1),
                        tab = classification.parentTab,
                        onTabSelected = onTabSelected,
                        onNewEntry = onNewEntry,
                        visibleTabs = getVisibleTabs(),
                    )
                }
            }

            is RouteClassification.FullscreenDetail -> {
                createFullscreenDetailScene(
                    entry = lastEntry,
                    previousEntries = entries.dropLast(1),
                )
            }

            RouteClassification.Excluded -> null
        }
    }
}
