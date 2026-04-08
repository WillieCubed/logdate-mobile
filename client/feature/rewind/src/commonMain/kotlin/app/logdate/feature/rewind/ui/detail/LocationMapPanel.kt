@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.rewind.ui.detail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.logdate.feature.rewind.ui.MapPanelPoint
import kotlin.math.abs
import kotlin.math.max

/**
 * Renders the user's actual movement across the rewind's period as a schematic map.
 *
 * No tile sources, no map renderer, no API keys: this is a Compose `Canvas` with a
 * naive equirectangular projection from the points' bounding box into the canvas.
 * Distances inside one rewind are small enough (a week of life) that the projection
 * distortion isn't visible at this scale, and the simplicity is the point — the panel
 * is supposed to read as "the shape your week traced", not as a literal navigational
 * map that competes with Google Maps.
 *
 * The first point is drawn larger than the rest as the start anchor; the last is
 * highlighted at the end. A connecting polyline runs through every point in
 * chronological order. Background uses the rewind accent so each map reads visually
 * grounded in the rewind it belongs to.
 */
@Composable
fun LocationMapPanel(
    points: List<MapPanelPoint>,
    title: String,
    subtitle: String,
    accentSeed: Int,
    modifier: Modifier = Modifier,
) {
    val backgroundColor = panelAccentBackground(accentSeed)
    Box(
        modifier = modifier.fillMaxSize().background(backgroundColor),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp, vertical = 80.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp, bottom = 32.dp),
            )
            Canvas(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .padding(8.dp),
            ) {
                if (points.size < 2) return@Canvas
                val bounds = points.boundingBox()
                val padded = bounds.padded(BOUNDING_BOX_PADDING)

                fun project(point: MapPanelPoint): Offset {
                    val xFraction = ((point.longitude - padded.minLon) / padded.lonSpan).toFloat()
                    // Y is inverted because canvas Y grows downward and latitude grows upward.
                    val yFraction = 1f - ((point.latitude - padded.minLat) / padded.latSpan).toFloat()
                    return Offset(xFraction * size.width, yFraction * size.height)
                }

                val projectedPoints = points.map(::project)

                // Polyline first, so the dots draw on top of it.
                val path = Path()
                projectedPoints.forEachIndexed { index, offset ->
                    if (index == 0) path.moveTo(offset.x, offset.y) else path.lineTo(offset.x, offset.y)
                }
                drawPath(
                    path = path,
                    color = Color.White.copy(alpha = 0.55f),
                    style = Stroke(width = 2.5f, cap = StrokeCap.Round),
                )

                // Dots: emphasize first and last for orientation.
                projectedPoints.forEachIndexed { index, offset ->
                    val isAnchor = index == 0 || index == projectedPoints.lastIndex
                    drawCircle(
                        color = if (isAnchor) Color.White else Color.White.copy(alpha = 0.7f),
                        radius = if (isAnchor) 7f else 3.5f,
                        center = offset,
                    )
                }
            }
        }
    }
}

private data class BoundingBox(
    val minLat: Double,
    val maxLat: Double,
    val minLon: Double,
    val maxLon: Double,
) {
    val latSpan: Double = max(maxLat - minLat, MIN_SPAN)
    val lonSpan: Double = max(maxLon - minLon, MIN_SPAN)

    fun padded(fraction: Double): BoundingBox {
        val latPad = latSpan * fraction
        val lonPad = lonSpan * fraction
        return BoundingBox(
            minLat = minLat - latPad,
            maxLat = maxLat + latPad,
            minLon = minLon - lonPad,
            maxLon = maxLon + lonPad,
        )
    }

    private companion object {
        // Floor on the projected span so a single-stop "path" doesn't divide by zero
        // and a tightly-clustered week still draws all its dots near the canvas center
        // instead of all on top of each other.
        const val MIN_SPAN = 0.0005
    }
}

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
 * The fraction of each axis to add as breathing room around the data points so the
 * polyline never hugs the canvas edge.
 */
private const val BOUNDING_BOX_PADDING = 0.10

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
