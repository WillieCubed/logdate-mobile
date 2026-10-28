package app.logdate.feature.rewind.ui.detail

import app.logdate.feature.rewind.ui.MapPanelPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RewindMapGeometryTest {
    @Test fun equivalentViewportsAtDifferentDensitiesKeepTheSameCameraScale() {
        val geometry = rewindMapGeometry(listOf(MapPanelPoint(36.17, -115.14)))
        assertEquals(geometry.zoomForSize(360, 360, 32, 1f), geometry.zoomForSize(1080, 1080, 96, 3f), 0.00001f)
    }

    @Test fun cameraZoomFramesANeighborhoodInsteadOfTheWorld() {
        val geometry = rewindMapGeometry(listOf(MapPanelPoint(36.17, -115.14)))
        assertTrue(geometry.zoomForSize(1000, 1000, 80) in 14f..17f)
    }

    @Test fun unsupportedPolarCoordinatesAreNotMovedToInventedLocations() {
        val points = listOf(-135.0, -45.0, 45.0, 135.0).map { MapPanelPoint(89.0, it) }
        val geometry = rewindMapGeometry(points)
        assertTrue(geometry.points.isEmpty())
        assertEquals(null, geometry.center)
    }

    @Test fun emptyAndInvalidCoordinatesDoNotInventAMapLocation() {
        val geometry = rewindMapGeometry(listOf(MapPanelPoint(Double.NaN, 0.0), MapPanelPoint(91.0, 0.0), MapPanelPoint(0.0, 181.0)))
        assertTrue(geometry.points.isEmpty())
        assertTrue(rewindMapGeometry(emptyList()).points.isEmpty())
    }

    @Test fun oneLocationHasANeighborhoodFrameAndOneMarker() {
        val point = MapPanelPoint(36.17, -115.14)
        val geometry = rewindMapGeometry(listOf(point, point))
        assertEquals(listOf(point), geometry.points)
        assertEquals(point.latitude, geometry.center!!.latitude, 0.0000001)
        assertEquals(point.longitude, geometry.center.longitude, 0.0000001)
        assertTrue(geometry.latitudeSpan >= 0.02)
        assertTrue(geometry.longitudeSpan >= 0.02)
        assertFalse(geometry.hasLocationsOutsideFrame)
    }

    @Test fun nearbyLocationsAreFramedTogether() {
        val points = listOf(MapPanelPoint(36.17, -115.14), MapPanelPoint(36.20, -115.10), MapPanelPoint(36.12, -115.17))
        val geometry = rewindMapGeometry(points)
        assertEquals(points, geometry.framePoints)
        assertTrue(geometry.latitudeSpan > 0.08)
        assertFalse(geometry.hasLocationsOutsideFrame)
    }

    @Test fun distantOutlierCannotZoomTheInitialViewOutToTheWorld() {
        val local = listOf(MapPanelPoint(36.17, -115.14), MapPanelPoint(36.20, -115.10), MapPanelPoint(36.12, -115.17))
        val outlier = MapPanelPoint(-33.86, 151.21)
        val geometry = rewindMapGeometry(listOf(outlier) + local)
        assertEquals(local, geometry.framePoints)
        assertTrue(outlier in geometry.points, "Framing must not silently remove recorded locations")
        assertTrue(geometry.hasLocationsOutsideFrame)
        assertTrue(geometry.longitudeSpan < 1.0)
    }

    @Test fun crossingTheDateLineKeepsALocalFrame() {
        val geometry = rewindMapGeometry(listOf(MapPanelPoint(0.0, 179.9), MapPanelPoint(0.0, -179.9)))
        assertTrue(geometry.longitudeSpan < 1.0)
        assertTrue(kotlin.math.abs(geometry.center!!.longitude) > 179.0)
        assertFalse(geometry.hasLocationsOutsideFrame)
    }
}
