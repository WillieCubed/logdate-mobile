package app.logdate.feature.postcards.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.logdate.ui.common.focusableWithRing

private const val TOOL_SURFACE_ALPHA = 0.92f
private const val TOOL_RAIL_WIDTH = 64

/**
 * Horizontal tool bar for compact (phone) layouts.
 */
@Composable
internal fun EditorToolPalette(
    activeTool: CanvasTool,
    onToolSelected: (CanvasTool) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = TOOL_SURFACE_ALPHA))
                .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        for (tool in CanvasTool.entries) {
            ToolButton(
                icon = tool.icon,
                label = tool.label,
                isActive = activeTool == tool,
                onClick = { onToolSelected(tool) },
                shortcut = tool.shortcut,
            )
        }
    }
}

/**
 * Vertical tool rail for expanded (tablet/desktop) layouts.
 */
@Composable
internal fun EditorToolRail(
    activeTool: CanvasTool,
    onToolSelected: (CanvasTool) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .width(TOOL_RAIL_WIDTH.dp)
                .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = TOOL_SURFACE_ALPHA))
                .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        for (tool in CanvasTool.entries) {
            ToolButton(
                icon = tool.icon,
                label = tool.label,
                isActive = activeTool == tool,
                onClick = { onToolSelected(tool) },
                shortcut = tool.shortcut,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToolButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit,
    shortcut: String? = null,
) {
    val tint =
        if (isActive) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }

    val tooltipText = if (shortcut != null) "$label ($shortcut)" else label

    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(tooltipText) } },
        state = rememberTooltipState(),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier =
                Modifier
                    .heightIn(min = 48.dp)
                    .widthIn(min = 48.dp)
                    .focusableWithRing()
                    .clickable(onClick = onClick),
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(24.dp),
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = tint,
            )
        }
    }
}

internal val CanvasTool.icon: ImageVector
    get() =
        when (this) {
            CanvasTool.SELECT -> Icons.Filled.NearMe
            CanvasTool.INK -> Icons.Filled.Create
            CanvasTool.SHAPE -> Icons.Filled.CropSquare
            CanvasTool.TEXT -> Icons.Filled.TextFields
            CanvasTool.STICKER -> Icons.Filled.Circle
        }

internal val CanvasTool.label: String
    get() =
        when (this) {
            CanvasTool.SELECT -> "Select"
            CanvasTool.INK -> "Ink"
            CanvasTool.SHAPE -> "Shape"
            CanvasTool.TEXT -> "Text"
            CanvasTool.STICKER -> "Sticker"
        }

internal val CanvasTool.shortcut: String
    get() =
        when (this) {
            CanvasTool.SELECT -> "1"
            CanvasTool.INK -> "2"
            CanvasTool.SHAPE -> "3"
            CanvasTool.TEXT -> "4"
            CanvasTool.STICKER -> "5"
        }
