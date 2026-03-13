package app.logdate.navigation.scenes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.Scene
import androidx.window.core.layout.WindowSizeClass.Companion.HEIGHT_DP_MEDIUM_LOWER_BOUND
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_EXPANDED_LOWER_BOUND
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_MEDIUM_LOWER_BOUND
import app.logdate.ui.theme.Spacing

/**
 * A [Scene] for two-pane list-detail layouts, used when a detail entry is selected
 * alongside a parent list (e.g., timeline list + day detail).
 *
 * Adaptive behavior:
 * - Expanded/landscape screens: Side-by-side Surface panels with nav rail
 * - Compact/medium screens: Detail-only fullscreen, navigation hidden
 */
class ListDetailHomeScene<T : NavKey>(
    override val key: Any,
    override val previousEntries: List<NavEntry<T>>,
    val mainEntry: NavEntry<T>,
    val detailEntry: NavEntry<T>,
    val onTabSelected: (HomeTab) -> Unit,
    val onNewEntry: () -> Unit,
    val selectedTab: HomeTab,
) : Scene<T> {
    override val entries: List<NavEntry<T>> = listOf(mainEntry, detailEntry)

    override val content: @Composable (() -> Unit) = {
        ListDetailContent()
    }

    @Suppress("ktlint:standard:function-naming")
    @Composable
    private fun ListDetailContent() {
        val adaptiveInfo = currentWindowAdaptiveInfo()
        val windowSizeClass = adaptiveInfo.windowSizeClass

        val isLandscapeCompact =
            !windowSizeClass.isHeightAtLeastBreakpoint(HEIGHT_DP_MEDIUM_LOWER_BOUND) &&
                windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_MEDIUM_LOWER_BOUND)

        val showTwoPane =
            windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_EXPANDED_LOWER_BOUND) || isLandscapeCompact

        val isDetailOnlyView = !showTwoPane

        val snackbarHostState = remember { SnackbarHostState() }

        NavigationShell(
            selectedTab = selectedTab,
            onTabSelected = onTabSelected,
            isDetailOnlyView = isDetailOnlyView,
            snackbarHostState = snackbarHostState,
        ) {
            if (showTwoPane) {
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
            } else {
                // Compact/medium: detail takes over fullscreen
                detailEntry.Content()
            }
        }
    }
}

/**
 * Creates a ListDetailHomeScene for a two-pane detail view.
 */
internal fun <T : NavKey> createTwoPaneHomeScene(
    mainEntry: NavEntry<T>,
    detailEntry: NavEntry<T>,
    previousEntries: List<NavEntry<T>>,
    tab: HomeTab,
    onTabSelected: (HomeTab) -> Unit,
    onNewEntry: () -> Unit,
): ListDetailHomeScene<T> =
    ListDetailHomeScene(
        key = Triple("ListDetailHomeScene", mainEntry.contentKey, detailEntry.contentKey),
        previousEntries = previousEntries,
        mainEntry = mainEntry,
        detailEntry = detailEntry,
        onTabSelected = onTabSelected,
        onNewEntry = onNewEntry,
        selectedTab = tab,
    )
