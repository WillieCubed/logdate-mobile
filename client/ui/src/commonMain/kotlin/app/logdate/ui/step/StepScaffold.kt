@file:Suppress("ktlint:standard:function-naming")

package app.logdate.ui.step

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.logdate.ui.adaptive.FoldableBookLayout
import app.logdate.ui.adaptive.FoldableTabletopLayout
import app.logdate.ui.foldable.FoldableLayoutInfo
import app.logdate.ui.foldable.rememberFoldableLayoutInfo
import app.logdate.ui.theme.Spacing
import logdate.client.ui.generated.resources.Res
import logdate.client.ui.generated.resources.common_back
import org.jetbrains.compose.resources.stringResource

/**
 * Position of a step within a multi-step flow, rendered as a progress bar and a "2 of 5" label.
 *
 * @param current 1-based index of the step being shown.
 * @param total Total number of steps in the flow.
 */
@Immutable
data class StepProgress(
    val current: Int,
    val total: Int,
) {
    init {
        require(total > 0) { "total must be positive, was $total" }
        require(current in 1..total) { "current must be within 1..$total, was $current" }
    }

    val fraction: Float
        get() = current.toFloat() / total.toFloat()
}

object StepScaffoldDefaults {
    /**
     * Widest the body and actions are allowed to grow. Keeps line lengths readable and the primary
     * action within thumb reach on large screens instead of stretching edge to edge.
     */
    val ContentMaxWidth: Dp = 444.dp

    /** Minimum height of the action pane when a tabletop-posture foldable splits the screen. */
    val MinActionPaneHeight: Dp = 220.dp

    /** Minimum width of each pane when a book-posture foldable splits the screen. */
    val MinBookPaneWidth: Dp = 320.dp

    /** An unfolded window at least this wide has room to put the details beside the header. */
    val SideBySideMinWidth: Dp = 840.dp

    /**
     * A window this wide but shorter than [ShortWindowMaxHeight] -- a phone in landscape -- also
     * goes side by side, because stacking would leave the body a sliver above the actions.
     */
    val ShortWindowSideBySideMinWidth: Dp = 600.dp

    val ShortWindowMaxHeight: Dp = 480.dp
}

/**
 * Full-screen layout for one step of a linear flow: a back affordance, an optional hero, a title,
 * optional supporting copy and content, and a set of actions.
 *
 * The actions are a **required** parameter rather than an optional pane, and every posture this
 * scaffold can render routes through them. Omitting them is a compile error, not a screen the user
 * cannot leave. That distinction matters: cloud account creation and sign-in were both unreachable
 * for a period because each screen hand-rolled its own adaptive layout and the compact branch
 * silently dropped the buttons.
 *
 * Callers supply only content. Posture handling is internal to this file so that no individual
 * screen can get it wrong:
 * - **Compact:** the body is centered in the space above the actions and scrolls when it doesn't
 *   fit; the actions sit below it, never on top of it.
 * - **Wide, book posture, or a phone in landscape:** the header on one side, the content and
 *   actions on the other. A wide window with no content keeps the actions under the header.
 * - **Tabletop posture:** the header above the hinge, the content and actions below it. Half a
 *   folded screen is too short for a header and its content together, and splitting them this way
 *   matches book posture.
 *
 * Any region that can scroll further fades out at that edge, so content that continues past a pane
 * boundary reads as more to scroll to, not as text sliced off mid-line.
 *
 * Window insets are applied per pane, inside the foldable layouts, because hinge bounds are in
 * window coordinates and padding the container would shift the split off the hinge.
 *
 * @param title Headline for the step, in sentence case.
 * @param onBack Invoked by the back affordance, or `null` to omit it (for example on the first step
 *   of a flow the user cannot reverse out of).
 * @param actions Buttons for this step, stacked vertically. The primary action comes first.
 * @param supportingText Optional paragraph shown beneath the title.
 * @param progress Optional position within the flow.
 * @param footer Optional low-emphasis element below the actions, such as a server switcher.
 * @param hero Optional visual shown above the title, such as [StepHeroIcon].
 * @param headerAlignment Alignment of the hero, title and supporting text. Content stays full width.
 * @param containerColor Fill behind the whole step. Opaque by default, like a Scaffold, so steps
 *   sliding past each other during a transition never show through one another.
 * @param content Optional body content shown between the supporting text and the actions.
 */
@Composable
fun StepScaffold(
    title: String,
    onBack: (() -> Unit)?,
    actions: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    progress: StepProgress? = null,
    footer: (@Composable () -> Unit)? = null,
    hero: (@Composable () -> Unit)? = null,
    headerAlignment: Alignment.Horizontal = Alignment.Start,
    containerColor: Color = MaterialTheme.colorScheme.background,
    contentMaxWidth: Dp = StepScaffoldDefaults.ContentMaxWidth,
    foldableLayoutInfo: FoldableLayoutInfo = rememberFoldableLayoutInfo(),
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val slots =
        StepSlots(
            title = title,
            onBack = onBack,
            actions = actions,
            supportingText = supportingText,
            progress = progress,
            footer = footer,
            hero = hero,
            headerAlignment = headerAlignment,
            contentMaxWidth = contentMaxWidth,
            content = content,
        )

    val safeDrawing = WindowInsets.safeDrawing
    FoldableTabletopLayout(
        modifier = modifier.fillMaxSize().background(containerColor),
        foldableLayoutInfo = foldableLayoutInfo,
        minPaneHeight = StepScaffoldDefaults.MinActionPaneHeight,
        topPane = {
            StepHeaderPane(
                slots = slots,
                includeContent = false,
                modifier = Modifier.fillMaxSize().windowInsetsPadding(safeDrawing.only(TabletopTopPaneSides)),
            )
        },
        bottomPane = {
            StepDetailPane(
                slots = slots,
                modifier = Modifier.fillMaxSize().windowInsetsPadding(safeDrawing.only(TabletopBottomPaneSides)),
            )
        },
        standardContent = {
            FoldableBookLayout(
                modifier = Modifier.fillMaxSize(),
                foldableLayoutInfo = foldableLayoutInfo,
                minPaneWidth = StepScaffoldDefaults.MinBookPaneWidth,
                startPane = {
                    StepHeaderPane(
                        slots = slots,
                        includeContent = false,
                        modifier = Modifier.fillMaxSize().windowInsetsPadding(safeDrawing.only(BookStartPaneSides)),
                    )
                },
                endPane = {
                    StepDetailPane(
                        slots = slots,
                        modifier = Modifier.fillMaxSize().windowInsetsPadding(safeDrawing.only(BookEndPaneSides)),
                    )
                },
                standardContent = {
                    StandardStep(slots = slots)
                },
            )
        },
    )
}

private val TabletopTopPaneSides = WindowInsetsSides.Top + WindowInsetsSides.Horizontal
private val TabletopBottomPaneSides = WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal
private val BookStartPaneSides = WindowInsetsSides.Vertical + WindowInsetsSides.Start
private val BookEndPaneSides = WindowInsetsSides.Vertical + WindowInsetsSides.End

private class StepSlots(
    val title: String,
    val onBack: (() -> Unit)?,
    val actions: @Composable ColumnScope.() -> Unit,
    val supportingText: String?,
    val progress: StepProgress?,
    val footer: (@Composable () -> Unit)?,
    val hero: (@Composable () -> Unit)?,
    val headerAlignment: Alignment.Horizontal,
    val contentMaxWidth: Dp,
    val content: (@Composable ColumnScope.() -> Unit)?,
)

@Composable
private fun StandardStep(slots: StepSlots) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        val wide =
            maxWidth >= StepScaffoldDefaults.SideBySideMinWidth ||
                (
                    maxWidth >= StepScaffoldDefaults.ShortWindowSideBySideMinWidth &&
                        maxHeight < StepScaffoldDefaults.ShortWindowMaxHeight
                )

        // With nothing but actions to put beside the header, a split would leave a lone button
        // floating in an empty half of the window. Keep them together under the header instead.
        if (wide && slots.content == null) {
            Column(modifier = Modifier.fillMaxSize()) {
                StepTopBar(onBack = slots.onBack, progress = slots.progress, contentMaxWidth = slots.contentMaxWidth)
                CenteredScrollColumn(
                    contentMaxWidth = slots.contentMaxWidth,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                ) {
                    StepHeader(slots)
                    Spacer(modifier = Modifier.height(Spacing.xxl))
                    StepActionColumn(slots)
                }
            }
            return@BoxWithConstraints
        }

        if (wide) {
            Row(modifier = Modifier.fillMaxSize()) {
                StepHeaderPane(
                    slots = slots,
                    includeContent = false,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                StepDetailPane(
                    slots = slots,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
            return@BoxWithConstraints
        }

        Column(modifier = Modifier.fillMaxSize()) {
            StepHeaderPane(
                slots = slots,
                includeContent = true,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.md, bottom = Spacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                StepActionColumn(slots)
            }
        }
    }
}

/**
 * The back row pinned at the top, then the header -- and, where there is no separate detail pane,
 * the content -- centered in the remaining space and scrolling when it doesn't fit.
 */
@Composable
private fun StepHeaderPane(
    slots: StepSlots,
    includeContent: Boolean,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    Column(modifier = modifier) {
        StepTopBar(onBack = slots.onBack, progress = slots.progress, contentMaxWidth = slots.contentMaxWidth)
        CenteredScrollColumn(
            contentMaxWidth = slots.contentMaxWidth,
            scrollState = scrollState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            StepHeader(slots)
            val content = slots.content
            if (includeContent && content != null) {
                Spacer(modifier = Modifier.height(Spacing.xl))
                StepContent(content)
            }
        }
    }
}

/**
 * The content and actions together, centered; the content scrolls and the actions stay put. A pane
 * wide enough for two columns -- the lower half of a tabletop-posture foldable -- puts them side by
 * side instead, since stacking them there wastes the width and forces the content to scroll.
 */
@Composable
private fun StepDetailPane(
    slots: StepSlots,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.padding(Spacing.lg), contentAlignment = Alignment.Center) {
        val content = slots.content
        val twoColumns = content != null && maxWidth >= slots.contentMaxWidth * 2 + Spacing.xxl
        if (twoColumns) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xxl, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ScrollingStepContent(content = content, modifier = Modifier.width(slots.contentMaxWidth))
                Box(modifier = Modifier.width(slots.contentMaxWidth)) {
                    StepActionColumn(slots)
                }
            }
            return@BoxWithConstraints
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (content != null) {
                ScrollingStepContent(
                    content = content,
                    modifier = Modifier.weight(1f, fill = false).widthIn(max = slots.contentMaxWidth),
                )
                Spacer(modifier = Modifier.height(Spacing.xl))
            }
            StepActionColumn(slots)
        }
    }
}

@Composable
private fun ScrollingStepContent(
    content: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .fadingEdges(scrollState)
                .verticalScroll(scrollState),
    ) {
        StepContent(content)
    }
}

/**
 * Back affordance at the leading edge, and progress capped to the content column and centered on it,
 * so on a wide window the bar lines up with the step instead of spanning the whole screen.
 */
@Composable
private fun StepTopBar(
    onBack: (() -> Unit)?,
    progress: StepProgress?,
    contentMaxWidth: Dp,
) {
    if (onBack == null && progress == null) return

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.xs, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(TopBarSlotSize), contentAlignment = Alignment.Center) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(Res.string.common_back),
                    )
                }
            }
        }
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            if (progress != null) {
                Row(
                    modifier = Modifier.widthIn(max = contentMaxWidth).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    LinearProgressIndicator(
                        progress = { progress.fraction },
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "${progress.current}/${progress.total}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        // Mirrors the back slot so the progress stays centered on the content column.
        Spacer(modifier = Modifier.size(TopBarSlotSize))
    }
}

private val TopBarSlotSize = 48.dp

@Composable
private fun StepHeader(slots: StepSlots) {
    val textAlign = if (slots.headerAlignment == Alignment.CenterHorizontally) TextAlign.Center else TextAlign.Start
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = slots.headerAlignment,
    ) {
        val hero = slots.hero
        if (hero != null) {
            hero()
            Spacer(modifier = Modifier.height(Spacing.xl))
        }
        Text(
            text = slots.title,
            style = MaterialTheme.typography.headlineLarge.copy(lineBreak = LineBreak.Heading),
            textAlign = textAlign,
            modifier = Modifier.fillMaxWidth(),
        )
        val supportingText = slots.supportingText
        if (supportingText != null) {
            Spacer(modifier = Modifier.height(Spacing.md))
            Text(
                text = supportingText,
                style = MaterialTheme.typography.bodyLarge.copy(lineBreak = LineBreak.Paragraph),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = textAlign,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun StepContent(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
        content = content,
    )
}

@Composable
private fun StepActionColumn(slots: StepSlots) {
    Column(
        modifier = Modifier.widthIn(max = slots.contentMaxWidth).fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        slots.actions(this)
        slots.footer?.invoke()
    }
}

/**
 * A column capped at [contentMaxWidth] that centers its children vertically when they fit and
 * scrolls from the top when they don't.
 */
@Composable
private fun CenteredScrollColumn(
    contentMaxWidth: Dp,
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
    content: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(modifier = modifier) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .fadingEdges(scrollState)
                    .verticalScroll(scrollState)
                    .heightIn(min = maxHeight)
                    .padding(horizontal = Spacing.lg, vertical = Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Column(
                modifier = Modifier.widthIn(max = contentMaxWidth).fillMaxWidth(),
                content = content,
            )
        }
    }
}

private val FadingEdgeLength = 32.dp

/**
 * Fades content out toward whichever edge it can still scroll past. Drawn over the viewport, before
 * [verticalScroll] in the chain, so the fade stays put while the content moves under it.
 */
private fun Modifier.fadingEdges(scrollState: ScrollState): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val length = FadingEdgeLength.toPx().coerceAtMost(size.height / 2)
            if (scrollState.canScrollBackward) {
                drawRect(
                    brush = Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black, startY = 0f, endY = length),
                    size = Size(size.width, length),
                    blendMode = BlendMode.DstIn,
                )
            }
            if (scrollState.canScrollForward) {
                val top = size.height - length
                drawRect(
                    brush = Brush.verticalGradient(0f to Color.Black, 1f to Color.Transparent, startY = top, endY = size.height),
                    topLeft = Offset(0f, top),
                    size = Size(size.width, length),
                    blendMode = BlendMode.DstIn,
                )
            }
        }
