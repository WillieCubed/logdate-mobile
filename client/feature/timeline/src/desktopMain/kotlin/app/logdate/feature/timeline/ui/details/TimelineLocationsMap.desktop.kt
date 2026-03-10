@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.timeline.ui.details

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import logdate.client.feature.timeline.generated.resources.Res
import logdate.client.feature.timeline.generated.resources.map_preview_available_on_android
import org.jetbrains.compose.resources.stringResource

@Composable
internal actual fun TimelineLocationsMap(
    locations: List<DayLocation>,
    modifier: Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(Res.string.map_preview_available_on_android),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
