@file:Suppress("ktlint:standard:function-naming")
@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package app.logdate.ui.workspace

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuOpen
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalWideNavigationRail
import androidx.compose.material3.NavigationItemIconPosition
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarArrangement
import androidx.compose.material3.ShortNavigationBarDefaults
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.material3.WideNavigationRail
import androidx.compose.material3.WideNavigationRailDefaults
import androidx.compose.material3.WideNavigationRailItem
import androidx.compose.material3.WideNavigationRailValue
import androidx.compose.material3.rememberWideNavigationRailState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.logdate.ui.LocalNavAnimatedVisibilityScope
import app.logdate.ui.LocalSharedTransitionScope
import app.logdate.ui.common.transitions.TransitionKeys
import app.logdate.ui.foldable.rememberFoldableLayoutInfo
import app.logdate.ui.platform.PlatformIcons
import app.logdate.ui.platform.rememberSystemReduceMotion
import app.logdate.ui.theme.Spacing
import app.logdate.ui.theme.WorkspaceSurface
import app.logdate.ui.theme.workspaceContainer
import kotlinx.coroutines.launch
import logdate.client.ui.generated.resources.Res
import logdate.client.ui.generated.resources.app_name
import logdate.client.ui.generated.resources.workspace_create_label
import logdate.client.ui.generated.resources.workspace_navigation_collapse
import logdate.client.ui.generated.resources.workspace_navigation_expand
import org.jetbrains.compose.resources.stringResource

/** Labels are one or two words and must never wrap, so the bar stops following the system text size here. */
private const val MAX_BAR_FONT_SCALE = 1.3f

private const val NAVIGATION_MOTION_MS = 300

/** Wide rail items start 20dp from the edge; the header lines up with them. */
private val RAIL_ITEM_INSET = 20.dp

/** One motion for every navigation change as a window resizes, so nothing jumps; reduced motion snaps. */
private fun <T> navigationMotion(reduceMotion: Boolean): FiniteAnimationSpec<T> =
    if (reduceMotion) snap() else tween(NAVIGATION_MOTION_MS, easing = FastOutSlowInEasing)

data class WorkspaceDestination(
    val key: String,
    val title: String,
    val icon: ImageVector,
    val unselectedIcon: ImageVector = icon,
)

/** The only Home owner of navigation, system insets, destination header and creation placement. */
@Composable
fun WorkspaceScaffold(
    destinations: List<WorkspaceDestination>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    onCreate: (() -> Unit)? = null,
    createLabel: String = stringResource(Res.string.workspace_create_label),
    createEntryTransition: Boolean = true,
    onSearch: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    status: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    bottomEdge: WorkspaceBottomEdge? = null,
    content: @Composable () -> Unit,
) {
    val selected = destinations.firstOrNull { it.key == selectedKey } ?: destinations.firstOrNull() ?: return
    val holder = rememberSaveableStateHolder()
    val posture = rememberFoldableLayoutInfo().posture
    BoxWithConstraints(modifier.fillMaxSize()) {
        val navigation = workspaceNavigationMode(maxWidth, maxHeight, posture)
        val rail = !navigation.isBar
        val largeText = LocalDensity.current.fontScale >= 1.5f
        val headerCreate =
            !rail && (largeText || maxWidth <= 440.dp || navigation == WorkspaceNavigationMode.ShortBarHorizontal)
        val canvas = MaterialTheme.colorScheme.workspaceContainer(WorkspaceSurface.Canvas)
        val edge = bottomEdge ?: rememberWorkspaceBottomEdge(selected.key)
        val reduceMotion = rememberSystemReduceMotion().value
        val barColor by animateColorAsState(
            MaterialTheme.colorScheme.workspaceContainer(if (edge.reached) WorkspaceSurface.Panel else WorkspaceSurface.Canvas),
            animationSpec = navigationMotion(reduceMotion),
            label = "workspaceBarColor",
        )
        // In a desktop window the app bar moves into the window's caption bar, between the system's
        // own controls, unless the text is too large to fit its height.
        val captionBar = if (onSearch != null) LocalWorkspaceCaptionBar.current ?: rememberPlatformCaptionBar(canvas) else null
        val appBarInCaption = captionBar?.takeIf { it.height >= MIN_CAPTION_APP_BAR_HEIGHT && !largeText }
        val headerActions: @Composable RowScope.() -> Unit = {
            onCreate?.let {
                AnimatedVisibility(
                    headerCreate,
                    enter = fadeIn(navigationMotion(reduceMotion)) + expandHorizontally(navigationMotion(reduceMotion)),
                    exit = fadeOut(navigationMotion(reduceMotion)) + shrinkHorizontally(navigationMotion(reduceMotion)),
                ) {
                    WorkspaceCreateAction(it, createLabel, createEntryTransition && headerCreate, compact = true)
                }
            }
            actions()
        }
        val railCreate: (@Composable (Boolean) -> Unit)? =
            onCreate?.let { create ->
                { extended ->
                    WorkspaceCreateAction(create, createLabel, createEntryTransition && rail, extended = extended)
                }
            }
        Scaffold(
            snackbarHost = snackbarHost,
            containerColor = canvas,
            contentWindowInsets =
                if (appBarInCaption != null) WindowInsets.safeDrawing.exclude(WindowInsets.captionBar) else WindowInsets.safeDrawing,
            bottomBar = {
                AnimatedVisibility(
                    !rail,
                    enter = fadeIn(navigationMotion(reduceMotion)) + slideInVertically(navigationMotion(reduceMotion)) { it },
                    exit = fadeOut(navigationMotion(reduceMotion)) + slideOutVertically(navigationMotion(reduceMotion)) { it },
                ) {
                    val horizontal = navigation == WorkspaceNavigationMode.ShortBarHorizontal
                    val iconPosition = if (horizontal) NavigationItemIconPosition.Start else NavigationItemIconPosition.Top
                    val arrangement =
                        if (horizontal) ShortNavigationBarArrangement.Centered else ShortNavigationBarDefaults.arrangement
                    val density = LocalDensity.current
                    val barDensity = Density(density.density, minOf(density.fontScale, MAX_BAR_FONT_SCALE))
                    CompositionLocalProvider(LocalDensity provides barDensity) {
                        ShortNavigationBar(
                            containerColor = barColor,
                            arrangement = arrangement,
                            modifier = Modifier.animateContentSize(navigationMotion(reduceMotion)),
                        ) {
                            destinations.forEach { destination ->
                                val isSelected = selected.key == destination.key
                                ShortNavigationBarItem(
                                    selected = isSelected,
                                    onClick = { onSelect(destination.key) },
                                    iconPosition = iconPosition,
                                    icon = { Icon(if (isSelected) destination.icon else destination.unselectedIcon, null) },
                                    label = { Text(destination.title) },
                                )
                            }
                        }
                    }
                }
            },
            floatingActionButton = {
                val floating = !rail && !headerCreate
                if (onCreate != null) {
                    AnimatedVisibility(
                        floating,
                        enter = fadeIn(navigationMotion(reduceMotion)) + scaleIn(navigationMotion(reduceMotion)),
                        exit = fadeOut(navigationMotion(reduceMotion)) + scaleOut(navigationMotion(reduceMotion)),
                    ) {
                        WorkspaceCreateAction(onCreate, createLabel, createEntryTransition && floating)
                    }
                }
            },
        ) { insets ->
            WorkspaceSearchHost {
                Column(Modifier.fillMaxSize().padding(insets)) {
                    if (appBarInCaption != null && onSearch != null) {
                        WorkspaceCaptionAppBar(selected.title, appBarInCaption, onSearch, headerActions)
                    }
                    Row(Modifier.fillMaxWidth().weight(1f)) {
                        AnimatedVisibility(
                            rail,
                            enter = fadeIn(navigationMotion(reduceMotion)) + expandHorizontally(navigationMotion(reduceMotion)),
                            exit = fadeOut(navigationMotion(reduceMotion)) + shrinkHorizontally(navigationMotion(reduceMotion)),
                        ) {
                            WorkspaceNavigationRail(
                                destinations = destinations,
                                selectedKey = selected.key,
                                onSelect = onSelect,
                                defaultExpanded = navigation == WorkspaceNavigationMode.Sidebar,
                                modal = navigation == WorkspaceNavigationMode.Rail,
                                containerColor = canvas,
                                showAppName = appBarInCaption == null,
                                createAction = railCreate,
                            )
                        }
                        Column(Modifier.weight(1f).fillMaxHeight()) {
                            if (appBarInCaption == null) {
                                if (onSearch != null) {
                                    WorkspaceAppBar(selected.title, onSearch, actions = headerActions)
                                } else {
                                    PanelHeader(selected.title, role = PanelHeaderRole.Destination, actions = headerActions)
                                }
                            }
                            status()
                            Box(Modifier.fillMaxWidth().weight(1f).nestedScroll(edge.connection)) {
                                CompositionLocalProvider(LocalWorkspaceEnabled provides true, LocalWorkspaceHosted provides true) {
                                    holder.SaveableStateProvider(selected.key) { content() }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A caption bar shorter than this cannot hold the search field and account button. */
private val MIN_CAPTION_APP_BAR_HEIGHT = 40.dp

@Composable
private fun WorkspaceCreateAction(
    onClick: () -> Unit,
    label: String,
    createEntryTransition: Boolean,
    compact: Boolean = false,
    extended: Boolean? = null,
) {
    val sharedScope = LocalSharedTransitionScope.current
    val visibilityScope = LocalNavAnimatedVisibilityScope.current
    val transitionModifier =
        if (createEntryTransition && sharedScope != null && visibilityScope != null) {
            with(sharedScope) {
                Modifier.sharedBounds(
                    rememberSharedContentState(TransitionKeys.FAB_TO_EDITOR_TRANSITION),
                    animatedVisibilityScope = visibilityScope,
                    boundsTransform = BoundsTransform { _, _ -> tween(350, easing = FastOutSlowInEasing) },
                    clipInOverlayDuringTransition = OverlayClip(MaterialTheme.shapes.large),
                )
            }
        } else {
            Modifier
        }
    if (extended != null) {
        ExtendedFloatingActionButton(
            text = { Text(label, Modifier.clearAndSetSemantics {}) },
            icon = { Icon(PlatformIcons.newEntry(), label) },
            onClick = onClick,
            expanded = extended,
            modifier = transitionModifier,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    } else if (compact) {
        IconButton(onClick = onClick, modifier = transitionModifier) {
            Icon(PlatformIcons.newEntry(), label, tint = MaterialTheme.colorScheme.primary)
        }
    } else {
        FloatingActionButton(
            onClick,
            modifier = transitionModifier,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Icon(PlatformIcons.newEntry(), label)
        }
    }
}

/**
 * The wide rail with the menu button Material puts in its header. It starts expanded on wide windows and
 * collapsed otherwise; the button expands or collapses it, and on medium windows the expanded rail
 * overlays the content instead of pushing it aside.
 */
@Composable
private fun WorkspaceNavigationRail(
    destinations: List<WorkspaceDestination>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    defaultExpanded: Boolean,
    modal: Boolean,
    containerColor: Color,
    showAppName: Boolean,
    createAction: (@Composable (extended: Boolean) -> Unit)?,
) {
    val state =
        rememberWideNavigationRailState(if (defaultExpanded) WideNavigationRailValue.Expanded else WideNavigationRailValue.Collapsed)
    val scope = rememberCoroutineScope()
    LaunchedEffect(defaultExpanded) {
        if (defaultExpanded) state.expand() else state.collapse()
    }
    val railExpanded = state.targetValue == WideNavigationRailValue.Expanded
    val toggleLabel =
        stringResource(if (railExpanded) Res.string.workspace_navigation_collapse else Res.string.workspace_navigation_expand)
    val header: @Composable () -> Unit = {
        Column(Modifier.padding(horizontal = RAIL_ITEM_INSET), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            IconButton(onClick = { scope.launch { state.toggle() } }) {
                Icon(if (railExpanded) Icons.AutoMirrored.Filled.MenuOpen else Icons.Default.Menu, toggleLabel)
            }
            // A desktop window's caption bar already names the app.
            if (railExpanded && showAppName) {
                Text(
                    stringResource(Res.string.app_name),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(vertical = Spacing.lg),
                )
            }
            createAction?.invoke(railExpanded)
        }
    }
    val items: @Composable () -> Unit = {
        destinations.forEach { destination ->
            val isSelected = selectedKey == destination.key
            WideNavigationRailItem(
                railExpanded = railExpanded,
                selected = isSelected,
                onClick = {
                    onSelect(destination.key)
                    if (modal) scope.launch { state.collapse() }
                },
                icon = { Icon(if (isSelected) destination.icon else destination.unselectedIcon, null) },
                label = { Text(destination.title) },
            )
        }
    }
    if (modal) {
        ModalWideNavigationRail(
            state = state,
            colors = WideNavigationRailDefaults.colors(containerColor = containerColor),
            windowInsets = WindowInsets(0, 0, 0, 0),
            header = header,
            modifier = Modifier.fillMaxHeight(),
            content = items,
        )
    } else {
        WideNavigationRail(
            state = state,
            colors = WideNavigationRailDefaults.colors(containerColor = containerColor),
            windowInsets = WindowInsets(0, 0, 0, 0),
            header = header,
            modifier = Modifier.fillMaxHeight(),
            content = items,
        )
    }
}
