@file:Suppress("ktlint:standard:function-naming")

package app.logdate.navigation.scenes

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.Scene
import app.logdate.ui.adaptive.ActivityPanelConstraints
import app.logdate.ui.adaptive.ActivityRouteFrame
import app.logdate.ui.workspace.AdaptiveWorkspaceLayout

/** Related collection and reading content; neither entry supplies Home navigation. */
class RelatedContentScene<T : NavKey>(
    override val key: Any,
    override val previousEntries: List<NavEntry<T>>,
    val mainEntry: NavEntry<T>,
    val detailEntry: NavEntry<T>,
) : Scene<T> {
    override val entries: List<NavEntry<T>> = listOf(mainEntry, detailEntry)

    override val content: @Composable (() -> Unit) = {
        ActivityRouteFrame(composePanels = false) {
            val textScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
            AdaptiveWorkspaceLayout(
                modifier = Modifier.fillMaxSize(),
                browseOnStart = true,
                browseConstraints = ActivityPanelConstraints.relatedBrowse(textScale),
                focusConstraints = ActivityPanelConstraints.relatedFocus(textScale),
                browse = { mainEntry.Content() },
                focus = { detailEntry.Content() },
            )
        }
    }
}

internal fun <T : NavKey> createRelatedContentScene(
    mainEntry: NavEntry<T>,
    detailEntry: NavEntry<T>,
    previousEntries: List<NavEntry<T>>,
): RelatedContentScene<T> =
    RelatedContentScene(
        key = Triple("RelatedContentScene", mainEntry.contentKey, detailEntry.contentKey),
        previousEntries = previousEntries,
        mainEntry = mainEntry,
        detailEntry = detailEntry,
    )
