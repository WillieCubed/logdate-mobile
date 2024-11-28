package app.logdate.feature.timeline.ui.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.logdate.ui.theme.Spacing

//import com.google.maps.android.compose.GoogleMap
//import com.google.maps.android.compose.rememberCameraPositionState

/**
 * A map that displays all the visited locations on a given day.
 */
@Composable
internal fun LocationsSection(
    locations: List<DayLocation>,
    startingPosition: DayLocation,
    modifier: Modifier = Modifier,
) {
    val locationsVisited = locations.size
//    val cameraPositionState = rememberCameraPositionState()
    Column(
        modifier = modifier.padding(horizontal = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text("Locations Visited", style = MaterialTheme.typography.titleSmall)
            Text(
                "$locationsVisited places visited",
                style = MaterialTheme.typography.labelMedium,
            )
        }
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4 / 3f)
        ) {
            // TODO: Re-enable map once we find multiplatform solution
//            GoogleMap(
//                modifier = Modifier.fillMaxSize(),
//            )
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
        val Origin = DayLocation(
            locationId = "origin",
            name = "Origin",
            latitude = 0.0,
            longitude = 0.0,
        )
    }
}