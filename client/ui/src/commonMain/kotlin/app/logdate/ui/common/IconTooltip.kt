@file:Suppress("ktlint:standard:function-naming")

package app.logdate.ui.common

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.logdate.ui.common.TooltipBox

/**
 * Names an icon-only control in a tooltip: on hover with a mouse or trackpad, and on long-press with
 * touch. Wrap every icon button that has no visible label, so pointer users can tell what it does.
 *
 * The tooltip draws nothing until it shows, so wrapping a control does not change its layout.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IconTooltip(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(positioning = TooltipAnchorPosition.Below),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
        modifier = modifier,
        content = content,
    )
}
