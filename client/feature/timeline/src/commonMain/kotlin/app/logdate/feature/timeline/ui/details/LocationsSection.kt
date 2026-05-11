@file:Suppress("ktlint:standard:function-naming", "ktlint:standard:no-wildcard-imports")

package app.logdate.feature.timeline.ui.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.logdate.ui.common.AspectRatios
import app.logdate.ui.theme.Spacing
import logdate.client.feature.timeline.generated.resources.*
import logdate.client.feature.timeline.generated.resources.Res
import org.jetbrains.compose.resources.stringResource

/**
 * A map that displays all the visited locations on a given day.
 */
@Composable
internal fun LocationsSection(
    locations: List<DayLocation>,
    onOpenLocations: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val locationsVisited = locations.size
    val locationSummary =
        when {
            locations.isEmpty() -> ""
            locations.size == 1 -> locations.first().name
            locations.size == 2 -> locations.joinToString(" • ") { it.name }
            else -> "${locations[0].name} • ${locations[1].name} +${locations.size - 2}"
        }

    Column(
        modifier = modifier.padding(horizontal = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    Text(stringResource(Res.string.locations_visited), style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(Res.string.places_visited_count, locationsVisited),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }

                onOpenLocations?.let { openLocations ->
                    TextButton(onClick = openLocations) {
                        Text(text = stringResource(Res.string.view_all_locations))
                    }
                }
            }
        }
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(8.dp),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(AspectRatios.TRADITIONAL),
        ) {
            TimelineLocationsMap(
                locations = locations,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (locationSummary.isNotBlank()) {
            Text(
                text = locationSummary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

data class DayLocation(
    val locationId: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
) {
    companion object {
        val Origin =
            DayLocation(
                locationId = "origin",
                name = "Origin",
                latitude = 0.0,
                longitude = 0.0,
            )
    }
}
