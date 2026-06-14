package app.logdate.navigation.scenes

import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import app.logdate.ui.foldable.FoldableSplitLayout
import app.logdate.ui.navigation.routeClass

/**
 * Activates a two-pane [ListDetailHomeScene] when the user opens an eligible detail on top of
 * a list-style source (HomeRoute or one of the overview routes) on a screen wide enough for
 * dual panes. Otherwise returns `null`, letting `NavDisplay` fall back to its default scene
 * (the top entry rendered fullscreen, which is correct for HomeRoute, Settings, the editor,
 * and rewind playback).
 */
class HomeSceneStrategy<T : NavKey>(
    private val supportsDualPane: () -> Boolean,
    private val foldableSplitLayout: () -> FoldableSplitLayout = { FoldableSplitLayout.None },
) : SceneStrategy<T> {
    override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? {
        if (entries.size < 2) return null

        val detailEntry = entries.last()
        val sourceEntry = entries[entries.size - 2]

        val detailClass = detailEntry.routeClass()
        val sourceClass = sourceEntry.routeClass()

        if (isAlwaysFullscreen(detailClass)) return null
        if (!isTwoPaneEligibleDetail(detailClass)) return null
        if (!isPaneSource(sourceClass)) return null
        if (!supportsDualPane()) return null

        return createTwoPaneHomeScene(
            mainEntry = sourceEntry,
            detailEntry = detailEntry,
            previousEntries = entries.dropLast(1),
            foldableSplitLayout = foldableSplitLayout(),
        )
    }
}
