@file:Suppress("ktlint:standard:function-naming")

package app.logdate.ui.step

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
}

/**
 * Full-screen layout for one step of a linear flow: a back affordance, a title, optional supporting
 * copy and content, and a set of actions.
 *
 * The actions are a **required** parameter rather than an optional pane, and every posture this
 * scaffold can render routes through them. Omitting them is a compile error, not a screen the user
 * cannot leave. That distinction matters: cloud account creation and sign-in were both unreachable
 * for a period because each screen hand-rolled its own adaptive layout and the compact branch
 * silently dropped the buttons.
 *
 * Callers supply only content. Posture handling — compact, book-posture foldable, tabletop-posture
 * foldable — is internal to this file so that no individual screen can get it wrong.
 *
 * @param title Headline for the step, in sentence case.
 * @param onBack Invoked by the back affordance, or `null` to omit it (for example on the first step
 *   of a flow the user cannot reverse out of).
 * @param actions Buttons for this step, stacked vertically. The primary action comes first.
 * @param supportingText Optional paragraph shown beneath the title.
 * @param progress Optional position within the flow.
 * @param footer Optional low-emphasis element pinned below the actions, such as a server switcher.
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
    contentMaxWidth: Dp = StepScaffoldDefaults.ContentMaxWidth,
    foldableLayoutInfo: FoldableLayoutInfo = rememberFoldableLayoutInfo(),
    content: @Composable ColumnScope.() -> Unit = {},
) {
    FoldableTabletopLayout(
        modifier = modifier.fillMaxSize(),
        foldableLayoutInfo = foldableLayoutInfo,
        minPaneHeight = StepScaffoldDefaults.MinActionPaneHeight,
        topPane = {
            StepBody(
                title = title,
                onBack = onBack,
                supportingText = supportingText,
                progress = progress,
                contentMaxWidth = contentMaxWidth,
                modifier = Modifier.fillMaxSize(),
                content = content,
            )
        },
        bottomPane = {
            StepActions(
                actions = actions,
                footer = footer,
                contentMaxWidth = contentMaxWidth,
                scrollable = true,
                modifier = Modifier.fillMaxSize(),
            )
        },
        standardContent = {
            FoldableBookLayout(
                modifier = Modifier.fillMaxSize(),
                foldableLayoutInfo = foldableLayoutInfo,
                minPaneWidth = StepScaffoldDefaults.MinBookPaneWidth,
                startPane = {
                    StepBody(
                        title = title,
                        onBack = onBack,
                        supportingText = supportingText,
                        progress = progress,
                        contentMaxWidth = contentMaxWidth,
                        modifier = Modifier.fillMaxSize(),
                        content = content,
                    )
                },
                endPane = {
                    StepActions(
                        actions = actions,
                        footer = footer,
                        contentMaxWidth = contentMaxWidth,
                        scrollable = true,
                        modifier = Modifier.fillMaxSize(),
                    )
                },
                standardContent = {
                    // Compact: the body fills and scrolls, the actions stay pinned to the bottom.
                    // They are siblings in the same Box rather than a conditional branch, so the
                    // actions cannot be scrolled out of reach or dropped by a posture check.
                    Box(modifier = Modifier.fillMaxSize()) {
                        StepBody(
                            title = title,
                            onBack = onBack,
                            supportingText = supportingText,
                            progress = progress,
                            contentMaxWidth = contentMaxWidth,
                            modifier = Modifier.fillMaxSize(),
                            content = content,
                        )
                        StepActions(
                            actions = actions,
                            footer = footer,
                            contentMaxWidth = contentMaxWidth,
                            scrollable = false,
                            modifier =
                                Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth(),
                        )
                    }
                },
            )
        },
    )
}

@Composable
private fun StepBody(
    title: String,
    onBack: (() -> Unit)?,
    supportingText: String?,
    progress: StepProgress?,
    contentMaxWidth: Dp,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.widthIn(max = contentMaxWidth).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(Res.string.common_back),
                    )
                }
            }
            if (progress != null) {
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

        Column(
            modifier = Modifier.widthIn(max = contentMaxWidth).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge,
            )
            if (supportingText != null) {
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            content()
        }
    }
}

@Composable
private fun StepActions(
    actions: @Composable ColumnScope.() -> Unit,
    footer: (@Composable () -> Unit)?,
    contentMaxWidth: Dp,
    scrollable: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .then(
                    if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier,
                ).padding(Spacing.lg),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.widthIn(max = contentMaxWidth).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            actions()
            if (footer != null) {
                footer()
            }
        }
    }
}
