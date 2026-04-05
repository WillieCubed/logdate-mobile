@file:OptIn(ExperimentalSharedTransitionApi::class)

package app.logdate.navigation.scenes

import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.window.core.layout.WindowSizeClass.Companion.HEIGHT_DP_MEDIUM_LOWER_BOUND
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_MEDIUM_LOWER_BOUND
import app.logdate.navigation.LocalBottomNavVisible
import app.logdate.navigation.LocalSharedTransitionScope
import app.logdate.ui.common.transitions.TransitionKeys
import logdate.app.composemain.generated.resources.Res
import logdate.app.composemain.generated.resources.select_an_entry_to_view_details
import org.jetbrains.compose.resources.stringResource

// Front-loads the bounds movement so the shape rounding is visible early in the transition
// rather than only at the very end when the bounds approach FAB size.
internal val FabEditorBoundsTransform =
    BoundsTransform { _, _ ->
        tween(durationMillis = 350, easing = FastOutSlowInEasing)
    }

/**
 * Shared navigation shell that wraps content with the outer Scaffold and NavigationSuiteScaffold.
 *
 * Handles:
 * - Outer Scaffold with `surfaceContainer` background, snackbar host, zero insets
 * - NavigationSuiteScaffold with adaptive nav bar/rail (transparent colors)
 * - Tab items for all [HomeTab] entries
 */
@Suppress("ktlint:standard:function-naming")
@Composable
internal fun NavigationShell(
    selectedTab: HomeTab,
    onTabSelected: (HomeTab) -> Unit,
    isDetailOnlyView: Boolean,
    snackbarHostState: SnackbarHostState,
    visibleTabs: List<HomeTab> = HomeTab.visibleEntries,
    content: @Composable () -> Unit,
) {
    val adaptiveInfo = currentWindowAdaptiveInfo()
    val windowSizeClass = adaptiveInfo.windowSizeClass

    val isLandscapeCompact =
        !windowSizeClass.isHeightAtLeastBreakpoint(HEIGHT_DP_MEDIUM_LOWER_BOUND) &&
            windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_MEDIUM_LOWER_BOUND)

    val effectiveLayoutType =
        if (isDetailOnlyView) {
            NavigationSuiteType.None
        } else if (isLandscapeCompact) {
            NavigationSuiteType.NavigationRail
        } else {
            NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(adaptiveInfo)
        }

    val hasBottomNav = effectiveLayoutType == NavigationSuiteType.NavigationBar

    CompositionLocalProvider(LocalBottomNavVisible provides hasBottomNav) {
        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
        ) {
            NavigationSuiteScaffold(
                containerColor = Color.Transparent,
                navigationSuiteColors =
                    NavigationSuiteDefaults.colors(
                        navigationRailContainerColor = Color.Transparent,
                        navigationBarContainerColor = Color.Transparent,
                    ),
                layoutType = effectiveLayoutType,
                navigationSuiteItems = {
                    visibleTabs.forEach { tab ->
                        item(
                            selected = selectedTab == tab,
                            onClick = { onTabSelected(tab) },
                            icon = {
                                Icon(
                                    imageVector = if (selectedTab == tab) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = tab.title,
                                    modifier =
                                        Modifier.semantics {
                                            contentDescription = "${tab.title}|logdate_home_tab_${tab.name.lowercase()}"
                                        },
                                )
                            },
                            label = { Text(tab.title) },
                        )
                    }
                },
            ) {
                content()
            }
        }
    }
}

/**
 * A shared element FAB that participates in the FAB-to-editor transition.
 */
@Suppress("ktlint:standard:function-naming")
@Composable
internal fun SharedElementFAB(
    onClick: () -> Unit,
    contentDescriptionText: String,
    modifier: Modifier = Modifier,
) {
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedContentScope = LocalNavAnimatedContentScope.current

    FloatingActionButton(
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier =
            if (sharedTransitionScope != null) {
                with(sharedTransitionScope) {
                    modifier
                        .shadow(elevation = 6.dp, shape = MaterialTheme.shapes.large, clip = false)
                        .sharedBounds(
                            rememberSharedContentState(key = TransitionKeys.FAB_TO_EDITOR_TRANSITION),
                            animatedVisibilityScope = animatedContentScope,
                            boundsTransform = FabEditorBoundsTransform,
                            clipInOverlayDuringTransition = OverlayClip(MaterialTheme.shapes.large),
                        )
                }
            } else {
                modifier
            }.semantics {
                contentDescription = "$contentDescriptionText|$HOME_NEW_ENTRY_FAB_TAG"
            },
    ) {
        Icon(
            Icons.Filled.Add,
            contentDescription = contentDescriptionText,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
internal fun DetailPlaceholder() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(Res.string.select_an_entry_to_view_details),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private const val HOME_NEW_ENTRY_FAB_TAG = "logdate_home_new_entry"
