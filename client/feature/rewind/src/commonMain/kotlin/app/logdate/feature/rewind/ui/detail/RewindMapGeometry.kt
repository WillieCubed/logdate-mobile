package app.logdate.feature.rewind.ui.detail

import app.logdate.feature.rewind.ui.MapPanelPoint
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

internal data class RewindMapGeometry(
    val points: List<MapPanelPoint>,
    val framePoints: List<MapPanelPoint>,
    val center: MapPanelPoint?,
    val latitudeSpan: Double,
    val longitudeSpan: Double,
) {
    val hasLocationsOutsideFrame: Boolean get() = framePoints.size < points.size
}

internal fun rewindMapGeometry(points: List<MapPanelPoint>): RewindMapGeometry {
    val valid =
        points
            .filter {
                it.latitude.isFinite() &&
                    it.longitude.isFinite() &&
                    it.latitude in -85.0..85.0 &&
                    it.longitude in -180.0..180.0
            }.distinct()
    if (valid.isEmpty()) return RewindMapGeometry(emptyList(), emptyList(), null, 0.0, 0.0)

    // Fit the densest local area. A bad sample or a distant trip must not turn
    // a neighborhood story into an unreadable world view. Retain every marker,
    // and disclose when some locations fall outside the automatic frame.
    val anchor = valid.maxBy { candidate -> valid.count { distanceKm(candidate, it) <= 250.0 } }
    val frame = valid.filter { distanceKm(anchor, it) <= 250.0 }
    val latitudes = frame.map { it.latitude }
    val longitudes = frame.map { anchor.longitude + longitudeDelta(it.longitude - anchor.longitude) }
    val minLat = latitudes.min()
    val maxLat = latitudes.max()
    val minLon = longitudes.min()
    val maxLon = longitudes.max()
    return RewindMapGeometry(
        points = valid,
        framePoints = frame,
        center = MapPanelPoint((minLat + maxLat) / 2, longitudeDelta((minLon + maxLon) / 2)),
        latitudeSpan = max((maxLat - minLat) * 1.35, 0.02),
        longitudeSpan = max((maxLon - minLon) * 1.35, 0.02).coerceAtMost(360.0),
    )
}

private fun longitudeDelta(value: Double): Double = ((value + 540.0) % 360.0) - 180.0

internal fun RewindMapGeometry.zoomForSize(
    width: Int,
    height: Int,
    padding: Int,
    density: Float = 1f,
): Float {
    val latitude = center?.latitude ?: return 1f

    fun mercator(value: Double) = ln(tan(kotlin.math.PI / 4 + value.coerceIn(-85.0, 85.0) * kotlin.math.PI / 360))
    val latFraction = (mercator(latitude + latitudeSpan / 2) - mercator(latitude - latitudeSpan / 2)) / (2 * kotlin.math.PI)
    // Camera zoom uses a 256dp world; bitmap dimensions remain in physical pixels.
    val tileSize = 256 * density.coerceAtLeast(0.1f)
    val xScale = (width - padding * 2).coerceAtLeast(1) / (tileSize * longitudeSpan / 360)
    val yScale = (height - padding * 2).coerceAtLeast(1) / (tileSize * latFraction)
    return (ln(minOf(xScale, yScale)) / ln(2.0)).coerceIn(1.0, 18.0).toFloat()
}

private fun distanceKm(
    first: MapPanelPoint,
    second: MapPanelPoint,
): Double {
    val radians = kotlin.math.PI / 180.0
    val lat = (second.latitude - first.latitude) * radians / 2
    val lon = longitudeDelta(second.longitude - first.longitude) * radians / 2
    val haversine = sin(lat) * sin(lat) + cos(first.latitude * radians) * cos(second.latitude * radians) * sin(lon) * sin(lon)
    return 6371.0 * 2 * asin(sqrt(haversine.coerceIn(0.0, 1.0)))
}
