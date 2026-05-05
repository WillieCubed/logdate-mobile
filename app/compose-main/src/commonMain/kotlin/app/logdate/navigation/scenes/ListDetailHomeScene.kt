package app.logdate.navigation.scenes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.Scene
import app.logdate.ui.theme.Spacing

/**
 * Two-pane Surface row that places the main (list-style) entry on the left and the detail
 * entry on the right. `HomeRoute` and the overview routes already render their own navigation
 * chrome, so this scene only handles the side-by-side framing.
 */
class ListDetailHomeScene<T : NavKey>(
    override val key: Any,
    override val previousEntries: List<NavEntry<T>>,
    val mainEntry: NavEntry<T>,
    val detailEntry: NavEntry<T>,
) : Scene<T> {
    override val entries: List<NavEntry<T>> = listOf(mainEntry, detailEntry)

    override val content: @Composable (() -> Unit) = {
        val panelShape = MaterialTheme.shapes.extraLarge
        Row(
            modifier =
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(top = Spacing.sm)
                    .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                modifier =
                    Modifier
                        .weight(1f)
                        .widthIn(min = 320.dp, max = 420.dp)
                        .fillMaxHeight()
                        .padding(bottom = 8.dp),
                shape = panelShape,
                color = MaterialTheme.colorScheme.surface,
            ) {
                mainEntry.Content()
            }

            Surface(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(bottom = 8.dp),
                shape = panelShape,
                color = MaterialTheme.colorScheme.surface,
            ) {
                detailEntry.Content()
            }
        }
    }
}

internal fun <T : NavKey> createTwoPaneHomeScene(
    mainEntry: NavEntry<T>,
    detailEntry: NavEntry<T>,
    previousEntries: List<NavEntry<T>>,
): ListDetailHomeScene<T> =
    ListDetailHomeScene(
        key = Triple("ListDetailHomeScene", mainEntry.contentKey, detailEntry.contentKey),
        previousEntries = previousEntries,
        mainEntry = mainEntry,
        detailEntry = detailEntry,
    )
