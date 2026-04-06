@file:OptIn(ExperimentalSharedTransitionApi::class)

package app.logdate.navigation.scenes

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
 * A [Scene] for content with navigation chrome (bottom nav bar or side rail).
 *
 * Used for main tab views (Timeline, Journals, Rewind, Location) and detail-only fallback views.
 * Two-pane list-detail layouts with a selected detail are handled by [ListDetailHomeScene].
 *
 * On wider screens (600dp+), wraps content in a rounded panel Surface. Individual screens
 * are responsible for their own internal responsive layout.
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
        val snackbarHostState = remember { SnackbarHostState() }

        NavigationShell(
            selectedTab = selectedTab,
            onTabSelected = onTabSelected,
            isDetailOnlyView = false,
            snackbarHostState = snackbarHostState,
            visibleTabs = visibleTabs,
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val showPanel = maxWidth >= 600.dp
                val panelShape = MaterialTheme.shapes.extraLarge

                when {
                    showPanel -> {
                        Surface(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .statusBarsPadding()
                                    .padding(top = Spacing.sm)
                                    .padding(horizontal = 8.dp)
                                    .padding(bottom = 8.dp),
                            shape = panelShape,
                            color = MaterialTheme.colorScheme.surface,
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                mainEntry.Content()

                                SharedElementFAB(
                                    onClick = onNewEntry,
                                    contentDescriptionText = stringResource(Res.string.new_entry),
                                    modifier =
                                        Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(16.dp),
                                )
                            }
                        }
                    }
                    else -> {
                        Box(modifier = Modifier.fillMaxSize()) {
                            mainEntry.Content()

                            SharedElementFAB(
                                onClick = onNewEntry,
                                contentDescriptionText = stringResource(Res.string.new_entry),
                                modifier =
                                    Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(16.dp),
                            )
                        }
                    }
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
