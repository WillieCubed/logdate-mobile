package app.logdate.navigation.scenes

import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import app.logdate.ui.navigation.ActivityPresentation
import app.logdate.ui.navigation.activityPresentation

/**
 * Composes entries only when their registrations declare matching content context.
 * Replacement activities and Home remain single entries regardless of their stack neighbors.
 */
class RelatedContentSceneStrategy<T : NavKey>(
    private val supportsDualPane: () -> Boolean,
) : SceneStrategy<T> {
    override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? {
        if (entries.size < 2) return null

        val detailEntry = entries.last()
        val sourceEntry = entries[entries.size - 2]

        val detail = detailEntry.activityPresentation() as? ActivityPresentation.Detail ?: return null
        val sourceContext =
            when (val source = sourceEntry.activityPresentation()) {
                is ActivityPresentation.Collection -> source.context
                is ActivityPresentation.Detail -> source.childContext
                else -> null
            }
        if (sourceContext != detail.context) return null
        if (!supportsDualPane()) return null

        return createRelatedContentScene(
            mainEntry = sourceEntry,
            detailEntry = detailEntry,
            previousEntries = entries.dropLast(1),
        )
    }
}
