package app.logdate.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * A centered AssistChip with an icon and label.
 * 
 * This is a convenience component that creates a centered row with a single AssistChip.
 * It's useful for actions that need to be prominently displayed.
 * 
 * @param onClick The callback to be invoked when the chip is clicked
 * @param label The text to display on the chip
 * @param icon The icon to display at the start of the chip
 * @param modifier Modifier to be applied to the chip's container
 */
@Composable
fun SingleAssistChip(
    onClick: () -> Unit,
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        AssistChip(
            onClick = onClick,
            label = { Text(label) },
            leadingIcon = { 
                Icon(
                    imageVector = icon,
                    contentDescription = null
                )
            },
            colors = AssistChipDefaults.assistChipColors(),
        )
    }
}