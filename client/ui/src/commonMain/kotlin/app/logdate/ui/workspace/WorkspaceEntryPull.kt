@file:Suppress("ktlint:standard:function-naming")

package app.logdate.ui.workspace

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import app.logdate.ui.creation.LocalEntryCreationLauncher
import app.logdate.ui.creation.entryCreationBounds
import app.logdate.ui.creation.entryCreationMotionSpec
import app.logdate.ui.creation.rememberEntryCreationSource
import app.logdate.ui.platform.PlatformIcons
import app.logdate.ui.platform.rememberSystemReduceMotion
import logdate.client.ui.generated.resources.Res
import logdate.client.ui.generated.resources.workspace_entry_pull_hint
import logdate.client.ui.generated.resources.workspace_entry_pull_new
import logdate.client.ui.generated.resources.workspace_entry_pull_release
import org.jetbrains.compose.resources.stringResource

/** Home-only source preview. It never mounts an editor or VM before release commitment. */
@Composable
internal fun WorkspaceEntryPull(
    onCreate: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val threshold = with(LocalDensity.current) { 112.dp.toPx() }
    val state = rememberSaveable(threshold, saver = WorkspaceEntryPullState.saver(threshold)) { WorkspaceEntryPullState(threshold) }
    val source = rememberEntryCreationSource()
    val sourceId = source.id
    val launcher = LocalEntryCreationLauncher.current
    val currentCreate by rememberUpdatedState(onCreate)
    val reduceMotion = rememberSystemReduceMotion().value
    SideEffect { state.updateEnabled(enabled && source.canLaunch) }
    LaunchedEffect(sourceId) { state.bindSource(sourceId) }
    DisposableEffect(state) { onDispose { state.dispose() } }
    val distance =
        animateFloatAsState(
            state.distance,
            if (state.dragging) snap() else entryCreationMotionSpec(reduceMotion),
            label = "homeEntryPullSettle",
        )
    val connection =
        remember(state, source, sourceId, launcher) {
            object : NestedScrollConnection {
                override fun onPreScroll(
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset =
                    if (source == NestedScrollSource.UserInput &&
                        state.distance > 0f
                    ) {
                        Offset(0f, state.drag(available.y, distance.value))
                    } else {
                        Offset.Zero
                    }

                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset =
                    if (source == NestedScrollSource.UserInput &&
                        available.y > 0f
                    ) {
                        Offset(0f, state.drag(available.y, distance.value))
                    } else {
                        Offset.Zero
                    }

                override suspend fun onPreFling(available: Velocity): Velocity {
                    if (state.distance == 0f) return Velocity.Zero
                    if (state.release()) {
                        try {
                            if (!source.launch(sourceId, launcher, currentCreate)) state.cancel()
                        } catch (error: Throwable) {
                            state.cancel()
                            throw error
                        }
                    }
                    return available
                }
            }
        }

    val reveal = (distance.value / threshold).coerceIn(0f, 1f)
    val hint = stringResource(if (state.armed) Res.string.workspace_entry_pull_release else Res.string.workspace_entry_pull_hint)
    Box(
        modifier
            .fillMaxSize()
            .clipToBounds()
            .then(PullCancellationElement(state))
            .nestedScroll(connection),
    ) {
        if (distance.value > 0f) {
            Surface(
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .widthIn(max = 640.dp)
                        .fillMaxWidth()
                        .padding(16.dp)
                        .graphicsLayer { alpha = reveal }
                        .entryCreationBounds(sourceId)
                        .testTag("workspace_entry_pull_source")
                        .semantics {
                            contentDescription = hint
                            liveRegion = LiveRegionMode.Polite
                        },
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Row(
                    Modifier.heightIn(min = 48.dp).padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(PlatformIcons.newEntry(), null, Modifier.size(24.dp))
                    Text(stringResource(Res.string.workspace_entry_pull_new), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        Box(Modifier.fillMaxSize().graphicsLayer { translationY = distance.value }) { content() }
    }
}

/** Observe cancellation only: no pointer consumption, forwarding or replacement gesture detector. */
private data class PullCancellationElement(
    val state: WorkspaceEntryPullState,
) : ModifierNodeElement<PullCancellationNode>() {
    override fun create() = PullCancellationNode(state)

    override fun update(node: PullCancellationNode) {
        node.state = state
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "workspaceEntryPullCancellation"
    }
}

private class PullCancellationNode(
    var state: WorkspaceEntryPullState,
) : Modifier.Node(),
    PointerInputModifierNode {
    override fun onPointerEvent(
        pointerEvent: PointerEvent,
        pass: PointerEventPass,
        bounds: IntSize,
    ) = Unit

    override fun onCancelPointerInput() {
        state.abortGesture()
    }
}
