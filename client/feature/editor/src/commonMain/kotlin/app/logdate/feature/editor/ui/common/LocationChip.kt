package app.logdate.feature.editor.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.logdate.ui.platform.PlatformIcons
import app.logdate.ui.theme.Spacing
import logdate.client.feature.editor.generated.resources.Res
import logdate.client.feature.editor.generated.resources.editor_cd_location_chip
import org.jetbrains.compose.resources.stringResource

@Suppress("ktlint:standard:function-naming")
@Composable
fun LocationChip(
    location: String,
    enabled: Boolean = true,
    onClick: () -> Unit = {},
) {
    Row(
        modifier =
            Modifier
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.tertiaryContainer)
                .border(2.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.large)
                .clickable(onClick = onClick)
                .padding(
                    horizontal = Spacing.sm,
                    vertical = Spacing.xs,
                ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        val icon = if (enabled) PlatformIcons.location() else PlatformIcons.locationOff()
        Icon(
            painter = icon,
            contentDescription = stringResource(Res.string.editor_cd_location_chip),
            modifier = Modifier.size(20.dp),
        )
        if (enabled) {
            Text(location, style = MaterialTheme.typography.labelSmall)
        }
    }
}
