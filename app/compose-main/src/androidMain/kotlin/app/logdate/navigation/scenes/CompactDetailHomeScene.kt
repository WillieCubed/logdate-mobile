package app.logdate.navigation.scenes

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.Scene

/**
 * A [Scene] for compact detail-only content that still belongs to a home tab.
 *
 * Unlike [ListDetailHomeScene], this scene hides navigation chrome entirely and renders
 * only the detail content. It preserves the selected tab context so transition logic can
 * distinguish compact full-screen detail from true two-pane activation.
 */
class CompactDetailHomeScene<T : NavKey>(
    override val key: Any,
    override val previousEntries: List<NavEntry<T>>,
    val detailEntry: NavEntry<T>,
    val onTabSelected: (HomeTab) -> Unit,
    val selectedTab: HomeTab,
    val visibleTabs: List<HomeTab> = HomeTab.visibleEntries,
) : Scene<T> {
    override val entries: List<NavEntry<T>> = listOf(detailEntry)

    override val content: @Composable (() -> Unit) = {
        val snackbarHostState = remember { SnackbarHostState() }

        NavigationShell(
            selectedTab = selectedTab,
            onTabSelected = onTabSelected,
            isDetailOnlyView = true,
            snackbarHostState = snackbarHostState,
            visibleTabs = visibleTabs,
        ) {
            detailEntry.Content()
        }
    }
}

internal fun <T : NavKey> createCompactDetailHomeScene(
    detailEntry: NavEntry<T>,
    previousEntries: List<NavEntry<T>>,
    tab: HomeTab,
    onTabSelected: (HomeTab) -> Unit,
    visibleTabs: List<HomeTab> = HomeTab.visibleEntries,
): CompactDetailHomeScene<T> =
    CompactDetailHomeScene(
        key = Pair("CompactDetailHomeScene", detailEntry.contentKey),
        previousEntries = previousEntries,
        detailEntry = detailEntry,
        onTabSelected = onTabSelected,
        selectedTab = tab,
        visibleTabs = visibleTabs,
    )
