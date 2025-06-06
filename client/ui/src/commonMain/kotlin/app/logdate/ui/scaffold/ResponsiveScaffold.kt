package app.logdate.ui.scaffold

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * A responsive scaffold layout that adapts to different screen sizes with animations.
 *
 * This scaffold supports:
 * - A side navigation rail for larger screens
 * - Bottom navigation for smaller screens
 * - Snackbar hosting for notifications
 * - Content area for the main UI
 * - Animated transitions between different layout states
 * - Respect for window insets (status bar, display cutout, etc.)
 *
 * @param modifier The modifier to be applied to the scaffold
 * @param showNavigationRail Whether to show the navigation rail
 * @param navigationRailWidth Width of the navigation rail when shown
 * @param showBottomNavigation Whether to show the bottom navigation
 * @param navigationRail The content to display in the navigation rail
 * @param bottomNavigation The content to display in the bottom navigation
 * @param snackbarHost The content to host snackbars, typically a [SnackbarHost]
 * @param containerColor The background color of the scaffold
 * @param windowInsets Window insets to be applied to the content
 * @param content The main content of the scaffold
 */
@Composable
fun ResponsiveScaffold(
    modifier: Modifier = Modifier,
    showNavigationRail: Boolean = false,
    navigationRailWidth: Int = 88, // Standard Material Design navigation rail width
    showBottomNavigation: Boolean = false,
    navigationRail: @Composable () -> Unit = {},
    bottomNavigation: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    containerColor: Color = Color.Transparent,
    windowInsets: WindowInsets = WindowInsets.safeDrawing,
    content: @Composable (PaddingValues) -> Unit,
) {
    // Remember the previous state to animate transitions
    val previousNavigationRailVisible = rememberSaveable { mutableStateOf(showNavigationRail) }
    val previousBottomNavigationVisible = rememberSaveable { mutableStateOf(showBottomNavigation) }

    // Calculate animation states
    val navigationRailVisibilityState = remember(showNavigationRail) {
        MutableTransitionState(previousNavigationRailVisible.value).apply {
            targetState = showNavigationRail
            previousNavigationRailVisible.value = showNavigationRail
        }
    }

    val bottomNavigationVisibilityState = remember(showBottomNavigation) {
        MutableTransitionState(previousBottomNavigationVisible.value).apply {
            targetState = showBottomNavigation
            previousBottomNavigationVisible.value = showBottomNavigation
        }
    }

    // Animated width for navigation rail
    val navigationRailWidthDp by animateDpAsState(
        targetValue = if (showNavigationRail) navigationRailWidth.dp else 0.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "navigationRailWidth"
    )

    // Get the status bar insets to exclude from content
    val statusBarInsets = WindowInsets.statusBars

    // Get insets for the navigation rail to respect system UI like cutouts
    val safeDrawingInsets = WindowInsets.safeDrawing
    val navigationInsetsPadding = safeDrawingInsets.asPaddingValues()

    Surface(
        modifier = modifier.fillMaxSize(),
        color = containerColor
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Main layout structure with navigation rail and content
            Row(modifier = Modifier.fillMaxSize()) {
                // Navigation rail with animation
                AnimatedVisibility(
                    visibleState = navigationRailVisibilityState,
                    enter = ResponsiveScaffoldDefaults.railEnterTransition,
                    exit = ResponsiveScaffoldDefaults.railExitTransition,
//                    modifier = Modifier.padding(navigationInsetsPadding) // TODO: Determine whether this is causing problems
                ) {
                    // Allocate fixed width for the navigation rail and apply window insets
                    // The width is constrained here, and NavigationRail itself shouldn't need
                    // to specify fillMaxWidth which was causing layout issues
                    Box(
                        modifier = Modifier
//                            .width(navigationRailWidthDp)
                            .fillMaxHeight()
                        // Apply padding to avoid display cutout for the navigation rail

                    ) {
                        navigationRail()
                    }
                }

                // Main content area and bottom navigation
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    // Content takes up all available space
                    Box(modifier = Modifier.weight(1f)) {
                        // Pass window insets as padding to content
                        val contentWindowInsets = windowInsets.exclude(statusBarInsets).exclude(WindowInsets.systemBars)
                        content(contentWindowInsets.asPaddingValues())
                    }

                    // Bottom navigation with animation
                    AnimatedVisibility(
                        visibleState = bottomNavigationVisibilityState,
                        enter = ResponsiveScaffoldDefaults.bottomNavEnterTransition,
                        exit = ResponsiveScaffoldDefaults.bottomNavExitTransition
                    ) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            bottomNavigation()
                        }
                    }
                }
            }

            // TODO: Figure out why render is causing recursion issues
            // Snackbar host - positioned at the bottom of the screen
//            Box(
//                modifier = Modifier.fillMaxSize(),
//                contentAlignment = Alignment.BottomCenter
//            ) {
//                snackbarHost()
//            }
        }
    }
}

/**
 * Creates a default [SnackbarHost] with the given [SnackbarHostState].
 *
 * @param hostState The state of the snackbar host
 * @param modifier The modifier to be applied to the snackbar host
 */
@Composable
fun ResponsiveScaffoldDefaults.SnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    androidx.compose.material3.SnackbarHost(
        hostState = hostState,
        modifier = modifier
    )
}

/**
 * Defaults for [ResponsiveScaffold].
 */
object ResponsiveScaffoldDefaults {
    /**
     * Creates and remembers a [SnackbarHostState].
     */
    @Composable
    fun rememberSnackbarHostState(): SnackbarHostState {
        return remember { SnackbarHostState() }
    }

    // Animation defaults
    val enterTransitionDuration = 300
    val exitTransitionDuration = 300

    /**
     * Default rail enter transition - slides in from left with fade
     */
    val railEnterTransition: EnterTransition = slideInHorizontally(
        initialOffsetX = { -it },
        animationSpec = tween(durationMillis = enterTransitionDuration)
    ) + fadeIn(animationSpec = tween(durationMillis = enterTransitionDuration))

    /**
     * Default rail exit transition - slides out to left with fade
     */
    val railExitTransition: ExitTransition = slideOutHorizontally(
        targetOffsetX = { -it },
        animationSpec = tween(durationMillis = exitTransitionDuration)
    ) + fadeOut(animationSpec = tween(durationMillis = exitTransitionDuration))

    /**
     * Default bottom navigation enter transition - expands from bottom with fade
     */
    val bottomNavEnterTransition: EnterTransition = slideInVertically(
        initialOffsetY = { it },
        animationSpec = tween(durationMillis = enterTransitionDuration)
    ) + fadeIn(animationSpec = tween(durationMillis = enterTransitionDuration))

    /**
     * Default bottom navigation exit transition - shrinks to bottom with fade
     */
    val bottomNavExitTransition: ExitTransition = slideOutVertically(
        targetOffsetY = { it },
        animationSpec = tween(durationMillis = exitTransitionDuration)
    ) + fadeOut(animationSpec = tween(durationMillis = exitTransitionDuration))
}