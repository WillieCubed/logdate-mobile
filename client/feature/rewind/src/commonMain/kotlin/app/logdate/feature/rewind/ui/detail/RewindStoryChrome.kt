@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.rewind.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import app.logdate.feature.rewind.ui.ReflectionPromptRewindPanelUiState
import app.logdate.feature.rewind.ui.RewindPanelUiState
import app.logdate.ui.platform.PlatformIcons
import logdate.client.feature.rewind.generated.resources.Res
import logdate.client.feature.rewind.generated.resources.close_rewind
import logdate.client.feature.rewind.generated.resources.delete_rewind
import logdate.client.feature.rewind.generated.resources.reflection_prompt_reply
import logdate.client.feature.rewind.generated.resources.reflection_prompt_reply_edit
import logdate.client.feature.rewind.generated.resources.rewind_more_actions
import logdate.client.feature.rewind.generated.resources.share_rewind_panel
import logdate.client.feature.rewind.generated.resources.share_rewind_stats
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun RewindStoryChrome(
    activePanel: RewindPanelUiState,
    totalPanels: Int,
    currentPanelIndex: Int,
    currentPanelProgress: Float,
    accentColor: Color,
    onPause: () -> Unit,
    onExit: () -> Unit,
    onMenuPauseChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onSharePanel: ((RewindPanelUiState) -> Unit)? = null,
    onShareRewindStats: (() -> Unit)? = null,
    onReplyToPrompt: ((ReflectionPromptRewindPanelUiState) -> Unit)? = null,
    onDeleteRewind: (() -> Unit)? = null,
) {
    val actions = mutableListOf<RewindChromeAction>()
    if (onReplyToPrompt != null && activePanel is ReflectionPromptRewindPanelUiState && activePanel.repliesAllowed) {
        actions +=
            RewindChromeAction(
                stringResource(
                    if (activePanel.existingResponse !=
                        null
                    ) {
                        Res.string.reflection_prompt_reply_edit
                    } else {
                        Res.string.reflection_prompt_reply
                    },
                ),
                { PlatformIcons.reply() },
            ) {
                onPause()
                onReplyToPrompt(activePanel)
            }
    }
    if (onShareRewindStats != null) {
        actions +=
            RewindChromeAction(stringResource(Res.string.share_rewind_stats), { PlatformIcons.photoLibrary() }) {
                onPause()
                onShareRewindStats()
            }
    }
    if (onSharePanel != null) {
        actions +=
            RewindChromeAction(stringResource(Res.string.share_rewind_panel), { PlatformIcons.share() }) {
                onPause()
                onSharePanel(activePanel)
            }
    }
    if (onDeleteRewind != null) {
        actions +=
            RewindChromeAction(stringResource(Res.string.delete_rewind), { PlatformIcons.delete() }) {
                onPause()
                onDeleteRewind()
            }
    }
    BoxWithConstraints(modifier = modifier) {
        val useOverflow = actions.isNotEmpty() && 48.dp * (actions.size + 1) > maxWidth
        var menuExpanded by remember { mutableStateOf(false) }
        LaunchedEffect(menuExpanded, useOverflow) {
            if (!useOverflow) menuExpanded = false
            onMenuPauseChange(menuExpanded && useOverflow)
        }
        DisposableEffect(Unit) { onDispose { onMenuPauseChange(false) } }
        Column(Modifier.fillMaxWidth()) {
            StoryProgressIndicators(
                totalPanels = totalPanels,
                currentPanelIndex = currentPanelIndex,
                currentPanelProgress = currentPanelProgress,
                color = accentColor,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (useOverflow) {
                    Box {
                        IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(48.dp)) {
                            Icon(PlatformIcons.more(), stringResource(Res.string.rewind_more_actions), tint = Color.White)
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            actions.forEach { action ->
                                DropdownMenuItem(
                                    text = { Text(action.label) },
                                    leadingIcon = { Icon(action.icon(), contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        action.onClick()
                                    },
                                )
                            }
                        }
                    }
                } else {
                    actions.forEach { action ->
                        IconButton(onClick = action.onClick, modifier = Modifier.size(48.dp)) {
                            Icon(action.icon(), action.label, tint = Color.White)
                        }
                    }
                }
                IconButton(onClick = onExit, modifier = Modifier.size(48.dp)) {
                    Icon(PlatformIcons.close(), stringResource(Res.string.close_rewind), tint = Color.White)
                }
            }
        }
    }
}

private data class RewindChromeAction(
    val label: String,
    val icon: @Composable () -> Painter,
    val onClick: () -> Unit,
)

/**
 * Progress indicators showing the current position in the story sequence.
 *
 * Displays a row of progress bars, one for each panel in the story. The current
 * panel shows an animated progress bar, completed panels are filled, and future
 * panels remain empty.
 *
 * ## Visual Design:
 * - **Completed panels**: Fully filled white progress bars
 * - **Current panel**: Animated progress bar showing auto-advance timing
 * - **Future panels**: Empty/unfilled progress bars
 * - **Spacing**: 2dp gaps between progress bars for clarity
 *
 * @param totalPanels Total number of panels in the story
 * @param currentPanelIndex Zero-based index of the currently displayed panel
 * @param currentPanelProgress Progress of current panel (0.0 to 1.0)
 * @param modifier Modifier for customizing the progress indicators container
 */
@Composable
private fun StoryProgressIndicators(
    totalPanels: Int,
    currentPanelIndex: Int,
    currentPanelProgress: Float,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        repeat(totalPanels) { index ->
            val progress =
                when {
                    index < currentPanelIndex -> 1f
                    index == currentPanelIndex -> currentPanelProgress
                    else -> 0f
                }

            LinearProgressIndicator(
                progress = { progress },
                modifier =
                    Modifier
                        .weight(1f)
                        .height(3.dp)
                        .clip(RoundedCornerShape(1.5.dp)),
                color = color,
                trackColor = color.copy(alpha = 0.3f),
            )
        }
    }
}
