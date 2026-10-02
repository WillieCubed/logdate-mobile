@file:Suppress("ktlint:standard:function-naming")

package app.logdate.ui.workspace

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.logdate.ui.common.PlatformBackHandler
import app.logdate.ui.foldable.rememberFoldableLayoutInfo
import app.logdate.ui.foldable.rememberWindowOrigin
import app.logdate.ui.platform.PlatformIcons
import app.logdate.ui.theme.Spacing

val LocalSupportingOverlap = staticCompositionLocalOf { 0.dp }

/** Keeps the visual focus mounted while the same supporting panel changes extent or placement. */
@Composable
fun WorkspaceSupportingSheet(
    summary: String,
    modifier: Modifier = Modifier,
    summaryDetail: String? = null,
    initialExtent: SupportingExtent = SupportingExtent.Peek,
    supportingContextKey: String? = null,
    supportingContainment: PanelContainment = PanelContainment.Working,
    header: (@Composable () -> Unit)? = null,
    headerActions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
    focus: @Composable () -> Unit,
    supporting: @Composable (SupportingExtent) -> Unit,
) {
    var extent by rememberSaveable { mutableStateOf(if (supportingContextKey != null) SupportingExtent.Browsing else initialExtent) }
    var previousExtent by rememberSaveable { mutableStateOf(initialExtent) }
    var previousContext by rememberSaveable { mutableStateOf(supportingContextKey) }
    LaunchedEffect(supportingContextKey) {
        if (supportingContextKey != null && previousContext == null) {
            previousExtent = extent
            extent = SupportingExtent.Browsing
        } else if (supportingContextKey == null && previousContext != null) {
            extent = previousExtent
        }
        previousContext = supportingContextKey
    }
    val parentPlacement = LocalPanelLayoutInfo.current?.placement
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val foldable = rememberFoldableLayoutInfo()
    val (origin, originModifier) = rememberWindowOrigin()
    BoxWithConstraints(modifier.fillMaxSize().then(originModifier), contentAlignment = AbsoluteAlignment.TopLeft) {
        val composition =
            resolveSupportingWorkspaceComposition(
                maxWidth,
                maxHeight,
                foldable = foldable,
                origin = origin,
                layoutDirection = direction,
            )
        val mapBounds = composition.focus
        val anchors = supportingSheetAnchors(mapBounds.height, 160.dp * density.fontScale.coerceAtMost(2f))
        val side = composition.browse
        val focused = extent == SupportingExtent.Focused || (side == null && !anchors.canOverlay && extent != SupportingExtent.Peek)
        val sheetHeight =
            if (side != null) {
                side.height
            } else {
                when {
                    focused -> mapBounds.height
                    !anchors.canOverlay -> 64.dp.coerceAtMost(mapBounds.height)
                    extent == SupportingExtent.Peek -> anchors.peek
                    extent == SupportingExtent.Browsing -> anchors.browsing
                    else -> anchors.expanded
                }
            }
        val mapPlacement =
            if (mapBounds.x > 0.dp ||
                parentPlacement == PanelPlacement.Floating
            ) {
                PanelPlacement.Floating
            } else {
                PanelPlacement.EdgeAttached
            }
        val overlap = if (side == null) sheetHeight.coerceAtMost((mapBounds.height - 180.dp).coerceAtLeast(0.dp)) else 0.dp
        Box(
            Modifier.absoluteOffset { IntOffset(mapBounds.x.roundToPx(), mapBounds.y.roundToPx()) }.size(mapBounds.width, mapBounds.height),
        ) {
            CompositionLocalProvider(LocalSupportingOverlap provides overlap) {
                WorkspacePanel(
                    Modifier.fillMaxSize(),
                    placement = mapPlacement,
                ) { focus() }
            }
        }
        val sheetX = side?.x ?: mapBounds.x
        val sheetY = side?.y ?: (mapBounds.y + mapBounds.height - sheetHeight)
        val sheetWidth = side?.width ?: mapBounds.width
        PlatformBackHandler(enabled = side == null && extent != SupportingExtent.Peek && supportingContextKey == null) {
            extent =
                SupportingExtent.Peek
        }
        Box(Modifier.absoluteOffset { IntOffset(sheetX.roundToPx(), sheetY.roundToPx()) }.size(sheetWidth, sheetHeight)) {
            WorkspacePanel(
                Modifier.fillMaxSize(),
                role = PanelRole.Browse,
                containment = if (side == null) PanelContainment.Working else supportingContainment,
                placement = if (side == null && mapPlacement != PanelPlacement.Floating) PanelPlacement.Lower else PanelPlacement.Floating,
            ) {
                Column(Modifier.fillMaxSize()) {
                    if (side == null || header != null) {
                        var drag by remember { mutableStateOf(0f) }
                        FlowRow(
                            Modifier
                                .fillMaxWidth()
                                .semantics {
                                    if (side == null) {
                                        stateDescription = extent.name
                                        expand {
                                            extent = if (anchors.canOverlay) SupportingExtent.Expanded else SupportingExtent.Focused
                                            true
                                        }
                                        collapse {
                                            extent = SupportingExtent.Peek
                                            true
                                        }
                                    }
                                }.pointerInput(extent, anchors, side) {
                                    if (side != null) return@pointerInput
                                    detectVerticalDragGestures(onDragStart = { drag = 0f }, onDragEnd = {
                                        if (drag < -24.dp.toPx()) {
                                            extent =
                                                when (extent) {
                                                    SupportingExtent.Peek ->
                                                        if (anchors.canOverlay) SupportingExtent.Browsing else SupportingExtent.Focused
                                                    else -> SupportingExtent.Expanded
                                                }
                                        }
                                        if (drag > 24.dp.toPx()) {
                                            extent =
                                                when (extent) {
                                                    SupportingExtent.Focused, SupportingExtent.Expanded -> SupportingExtent.Browsing
                                                    else -> SupportingExtent.Peek
                                                }
                                        }
                                    }) { change, amount ->
                                        change.consume()
                                        drag += amount
                                    }
                                }.padding(horizontal = Spacing.lg),
                        ) {
                            if (header != null) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.weight(1f)) { header() }
                                    if (side == null) {
                                        val expandLabel =
                                            when (extent) {
                                                SupportingExtent.Peek -> "Browse"
                                                SupportingExtent.Browsing -> "Expand"
                                                else -> "Collapse"
                                            }
                                        IconButton(onClick = {
                                            extent =
                                                when (extent) {
                                                    SupportingExtent.Peek ->
                                                        if (anchors.canOverlay) SupportingExtent.Browsing else SupportingExtent.Focused
                                                    SupportingExtent.Browsing -> SupportingExtent.Expanded
                                                    else -> SupportingExtent.Peek
                                                }
                                        }) {
                                            Icon(
                                                PlatformIcons.expandMore(),
                                                expandLabel,
                                                Modifier.then(
                                                    if ((extent == SupportingExtent.Peek || extent == SupportingExtent.Browsing)) {
                                                        Modifier.graphicsLayer {
                                                            rotationZ =
                                                                180f
                                                        }
                                                    } else {
                                                        Modifier
                                                    },
                                                ),
                                            )
                                        }
                                        IconButton(onClick = {
                                            extent =
                                                if (focused) {
                                                    if (anchors.canOverlay) SupportingExtent.Browsing else SupportingExtent.Peek
                                                } else {
                                                    SupportingExtent.Focused
                                                }
                                        }) { Icon(PlatformIcons.openInNew(), if (focused) "Show map" else "Full view") }
                                    }
                                    headerActions()
                                }
                            } else {
                                TextButton(onClick = {
                                    extent =
                                        if (extent ==
                                            SupportingExtent.Peek
                                        ) {
                                            if (anchors.canOverlay) SupportingExtent.Browsing else SupportingExtent.Focused
                                        } else {
                                            SupportingExtent.Peek
                                        }
                                }) {
                                    Text(if (extent == SupportingExtent.Peek) "Browse" else "Collapse")
                                }
                                if (extent ==
                                    SupportingExtent.Browsing
                                ) {
                                    TextButton(onClick = { extent = SupportingExtent.Expanded }) { Text("Expand") }
                                }
                                TextButton(onClick = {
                                    extent =
                                        if (focused) {
                                            if (anchors.canOverlay) SupportingExtent.Browsing else SupportingExtent.Peek
                                        } else {
                                            SupportingExtent.Focused
                                        }
                                }) {
                                    Text(if (focused) "Show map" else "Full view")
                                }
                            }
                        }
                    }
                    if (side == null && extent == SupportingExtent.Peek) {
                        if (anchors.canOverlay) PanelHeader(summary, subtitle = summaryDetail)
                    } else {
                        Box(Modifier.weight(1f)) { supporting(if (side != null) SupportingExtent.Expanded else extent) }
                    }
                }
            }
        }
    }
}
