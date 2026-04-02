@file:OptIn(ExperimentalSharedTransitionApi::class)

package app.logdate.navigation.scenes

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.Scene
import app.logdate.ui.theme.Spacing
import logdate.app.composemain.generated.resources.Res
import logdate.app.composemain.generated.resources.new_entry
import org.jetbrains.compose.resources.stringResource

/**
 * A [Scene] for single-pane content with navigation chrome (bottom nav bar or side rail).
 *
 * Used for main tab views (Timeline, Journals, Rewind, Location) and detail-only fallback views.
 * Two-pane list-detail layouts with a selected detail are handled by [ListDetailHomeScene].
 *
 * Special case: on the timeline tab in landscape/expanded mode, this scene shows a two-pane
 * layout with a placeholder in the detail pane, so the user sees the list-detail structure
 * before selecting an entry.
 */
class HomeScene<T : NavKey>(
    override val key: Any,
    override val previousEntries: List<NavEntry<T>>,
    val mainEntry: NavEntry<T>,
    val onTabSelected: (HomeTab) -> Unit,
    val onNewEntry: () -> Unit,
    val selectedTab: HomeTab,
    val visibleTabs: List<HomeTab> = HomeTab.visibleEntries,
) : Scene<T> {
    override val entries: List<NavEntry<T>> = listOf(mainEntry)

    override val content: @Composable (() -> Unit) = {
        HomeSceneContent()
    }

    @Suppress("ktlint:standard:function-naming")
    @Composable
    private fun HomeSceneContent() {
        val adaptiveInfo = currentWindowAdaptiveInfo()
        val showTwoPaneWithPlaceholder =
            selectedTab == HomeTab.TIMELINE &&
                adaptiveInfo.windowSizeClass.supportsDualPaneHomeScene()

        val snackbarHostState = remember { SnackbarHostState() }

        NavigationShell(
            selectedTab = selectedTab,
            onTabSelected = onTabSelected,
            isDetailOnlyView = false,
            snackbarHostState = snackbarHostState,
            visibleTabs = visibleTabs,
        ) {
            if (showTwoPaneWithPlaceholder) {
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
                        DetailPlaceholder()
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    mainEntry.Content()

                    SharedElementFAB(
                        onClick = onNewEntry,
                        contentDescription = stringResource(Res.string.new_entry),
                        modifier =
                            Modifier
                                .align(Alignment.BottomEnd)
                                .padding(16.dp),
                    )
                }
            }
        }
    }

    companion object {
        private const val HOME_SCENE_KEY = "HomeScene"

        /**
         * Helper function to add metadata to a [NavEntry] indicating it can be displayed
         * in the home scene.
         */
        fun homeScene() = mapOf(HOME_SCENE_KEY to true)
    }
}

/**
 * Creates a HomeScene for a main tab.
 */
internal fun <T : NavKey> createMainTabHomeScene(
    entry: NavEntry<T>,
    previousEntries: List<NavEntry<T>>,
    tab: HomeTab,
    onTabSelected: (HomeTab) -> Unit,
    onNewEntry: () -> Unit,
    visibleTabs: List<HomeTab> = HomeTab.visibleEntries,
): HomeScene<T> =
    HomeScene(
        key = Pair("HomeScene", entry.contentKey),
        previousEntries = previousEntries,
        mainEntry = entry,
        onTabSelected = onTabSelected,
        onNewEntry = onNewEntry,
        selectedTab = tab,
        visibleTabs = visibleTabs,
    )

/**
 * Creates a HomeScene for a full-screen detail view.
 */
internal fun <T : NavKey> createFullscreenHomeScene(
    entry: NavEntry<T>,
    previousEntries: List<NavEntry<T>>,
    tab: HomeTab,
    onTabSelected: (HomeTab) -> Unit,
    onNewEntry: () -> Unit,
    visibleTabs: List<HomeTab> = HomeTab.visibleEntries,
): HomeScene<T> =
    HomeScene(
        key = Pair("HomeScene", entry.contentKey),
        previousEntries = previousEntries,
        mainEntry = entry,
        onTabSelected = onTabSelected,
        onNewEntry = onNewEntry,
        selectedTab = tab,
        visibleTabs = visibleTabs,
    )
