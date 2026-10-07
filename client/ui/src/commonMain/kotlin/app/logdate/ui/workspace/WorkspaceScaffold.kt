@file:Suppress("ktlint:standard:function-naming")
@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package app.logdate.ui.workspace

import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.logdate.ui.LocalNavAnimatedVisibilityScope
import app.logdate.ui.LocalSharedTransitionScope
import app.logdate.ui.common.transitions.TransitionKeys
import app.logdate.ui.foldable.rememberFoldableLayoutInfo
import app.logdate.ui.platform.PlatformIcons
import app.logdate.ui.theme.Spacing
import app.logdate.ui.theme.WorkspaceSurface
import app.logdate.ui.theme.workspaceContainer

data class WorkspaceDestination(
    val key: String,
    val title: String,
    val icon: ImageVector,
)

/** The only Home owner of navigation, system insets, destination header and creation placement. */
@Composable
fun WorkspaceScaffold(
    destinations: List<WorkspaceDestination>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    onCreate: (() -> Unit)? = null,
    createLabel: String = "Add a memory",
    createEntryTransition: Boolean = true,
    onSearch: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    status: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val selected = destinations.firstOrNull { it.key == selectedKey } ?: destinations.firstOrNull() ?: return
    val holder = rememberSaveableStateHolder()
    val posture = rememberFoldableLayoutInfo().posture
    BoxWithConstraints(modifier.fillMaxSize()) {
        val navigation = workspaceNavigationMode(maxWidth, posture)
        val rail = navigation != WorkspaceNavigationMode.Bottom
        val largeText = LocalDensity.current.fontScale >= 1.5f
        val canvas = MaterialTheme.colorScheme.workspaceContainer(WorkspaceSurface.Canvas)
        Scaffold(
            containerColor = canvas,
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                if (!rail) {
                    NavigationBar(containerColor = canvas) {
                        destinations.forEach { destination ->
                            NavigationBarItem(
                                selected.key == destination.key,
                                { onSelect(destination.key) },
                                icon = { Icon(destination.icon, destination.title) },
                                label =
                                    if (largeText) {
                                        null
                                    } else {
                                        { Text(destination.title, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                    },
                            )
                        }
                    }
                }
            },
            floatingActionButton = {
                if (!rail && onCreate != null) WorkspaceCreateAction(onCreate, createLabel, createEntryTransition)
            },
        ) { insets ->
            Row(Modifier.fillMaxSize().padding(insets)) {
                if (navigation == WorkspaceNavigationMode.Sidebar) {
                    Column(
                        Modifier
                            .width(240.dp)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(Spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        Text("LogDate", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(Spacing.lg))
                        onCreate?.let { WorkspaceCreateAction(it, createLabel, createEntryTransition) }
                        destinations.forEach { destination ->
                            NavigationDrawerItem(
                                label = { Text(destination.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                selected = selected.key == destination.key,
                                onClick = { onSelect(destination.key) },
                                icon = { Icon(destination.icon, null) },
                            )
                        }
                    }
                } else if (rail) {
                    NavigationRail(
                        containerColor = canvas,
                        windowInsets = WindowInsets(0, 0, 0, 0),
                        header = { onCreate?.let { WorkspaceCreateAction(it, createLabel, createEntryTransition) } },
                        modifier = Modifier.fillMaxHeight(),
                    ) {
                        destinations.forEach { destination ->
                            NavigationRailItem(
                                selected.key == destination.key,
                                { onSelect(destination.key) },
                                icon = { Icon(destination.icon, destination.title) },
                                label =
                                    if (largeText) {
                                        null
                                    } else {
                                        { Text(destination.title, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                    },
                            )
                        }
                    }
                }
                WorkspaceSearchHost {
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        if (onSearch != null) {
                            WorkspaceAppBar(selected.title, onSearch, actions = actions)
                        } else {
                            PanelHeader(selected.title, role = PanelHeaderRole.Destination, actions = actions)
                        }
                        status()
                        Box(Modifier.fillMaxWidth().weight(1f)) {
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

@Composable
private fun WorkspaceCreateAction(
    onClick: () -> Unit,
    label: String,
    createEntryTransition: Boolean,
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
    FloatingActionButton(
        onClick,
        modifier = transitionModifier,
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Icon(PlatformIcons.newEntry(), label)
    }
}
