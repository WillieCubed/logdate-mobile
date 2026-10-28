@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.rewind.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.logdate.feature.rewind.ui.MapPanelPoint
import logdate.client.feature.rewind.generated.resources.Res
import logdate.client.feature.rewind.generated.resources.rewind_map_main_area
import logdate.client.feature.rewind.generated.resources.rewind_map_no_locations
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs
import kotlin.math.max

/** A non-interactive geographic backdrop. Story navigation keeps ownership of gestures. */
@Composable
fun LocationMapPanel(
    points: List<MapPanelPoint>,
    title: String,
    subtitle: String,
    accentSeed: Int,
    modifier: Modifier = Modifier,
) {
    val geometry = remember(points) { rewindMapGeometry(points) }
    BoxWithConstraints(modifier.fillMaxSize().background(panelAccentBackground(accentSeed))) {
        if (maxHeight < 480.dp) {
            Row(
                Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 80.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(
                    Modifier.weight(0.4f).fillMaxHeight().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MapHeading(title, subtitle, compact = true)
                    MainAreaNotice(geometry)
                }
                MapBackdrop(geometry, Modifier.weight(0.6f).fillMaxHeight())
            }
        } else {
            Column(
                Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp, top = 100.dp, bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MapHeading(title, subtitle, compact = false)
                MapBackdrop(geometry, Modifier.fillMaxWidth().weight(1f))
                MainAreaNotice(geometry)
            }
        }
    }
}

@Composable
private fun MapHeading(
    title: String,
    subtitle: String,
    compact: Boolean,
) {
    Text(
        title,
        style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium,
        color = Color.White,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center,
    )
    Text(
        subtitle,
        style = MaterialTheme.typography.bodyMedium,
        color = Color.White.copy(alpha = 0.7f),
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun MainAreaNotice(geometry: RewindMapGeometry) {
    if (geometry.hasLocationsOutsideFrame) {
        Text(
            stringResource(Res.string.rewind_map_main_area),
            color = Color.White.copy(alpha = 0.8f),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun MapBackdrop(
    geometry: RewindMapGeometry,
    modifier: Modifier,
) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val side = minOf(maxWidth, maxHeight, 600.dp)
        Box(Modifier.size(side).clip(RoundedCornerShape(16.dp))) {
            if (geometry.points.isEmpty()) {
                RewindMapMessage(stringResource(Res.string.rewind_map_no_locations), Modifier.align(Alignment.Center))
            } else {
                RewindGeographicMap(geometry, Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
internal expect fun RewindGeographicMap(
    geometry: RewindMapGeometry,
    modifier: Modifier,
)

@Composable
internal fun RewindMapMessage(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text,
        modifier.padding(12.dp).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp)).padding(12.dp),
        color = MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.bodySmall,
        textAlign = TextAlign.Center,
    )
}

private data class BoundingBox(
    val minLat: Double,
    val maxLat: Double,
    val minLon: Double,
    val maxLon: Double,
)

private fun List<MapPanelPoint>.boundingBox(): BoundingBox {
    var minLat = first().latitude
    var maxLat = first().latitude
    var minLon = first().longitude
    var maxLon = first().longitude
    forEach { point ->
        if (point.latitude < minLat) minLat = point.latitude
        if (point.latitude > maxLat) maxLat = point.latitude
        if (point.longitude < minLon) minLon = point.longitude
        if (point.longitude > maxLon) maxLon = point.longitude
    }
    return BoundingBox(minLat = minLat, maxLat = maxLat, minLon = minLon, maxLon = maxLon)
}

/**
 * Roughly converts a lat/lon bounding-box span into kilometers using the back-of-the-
 * envelope "1 degree of latitude is ~111 km, longitude varies but the average for
 * mid-latitudes is close enough" rule. The eligibility check inside [qualifiesForMapPanel]
 * uses this to decide whether the rewind's path is geographically meaningful or just
 * GPS jitter inside one apartment.
 */
internal fun List<MapPanelPoint>.approximateBoundingKilometers(): Double {
    if (size < 2) return 0.0
    val bounds = boundingBox()
    val latKm = abs(bounds.maxLat - bounds.minLat) * KM_PER_DEGREE_LAT
    val lonKm = abs(bounds.maxLon - bounds.minLon) * KM_PER_DEGREE_LAT
    return max(latKm, lonKm)
}

private const val KM_PER_DEGREE_LAT = 111.0

/**
 * Decides whether the rewind has a path interesting enough to render the map panel.
 *
 * The bar is conservative on purpose: a week spent in one apartment shouldn't get a
 * map panel that says "you barely moved", and a week with two GPS samples on the same
 * couch shouldn't get one either. The user has to actually have moved.
 */
internal fun List<MapPanelPoint>.qualifiesForMapPanel(): Boolean {
    if (size < MIN_POINTS_FOR_MAP_PANEL) return false
    return approximateBoundingKilometers() >= MIN_BOUNDING_KM
}

private const val MIN_POINTS_FOR_MAP_PANEL = 3
private const val MIN_BOUNDING_KM = 1.0
