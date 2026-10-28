@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.rewind.ui.detail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.coroutines.delay
import logdate.client.feature.rewind.generated.resources.Res
import logdate.client.feature.rewind.generated.resources.rewind_map_accessibility
import logdate.client.feature.rewind.generated.resources.rewind_map_loading
import logdate.client.feature.rewind.generated.resources.rewind_map_unavailable
import org.jetbrains.compose.resources.stringResource
import platform.CoreLocation.CLLocationCoordinate2DMake
import platform.Foundation.NSError
import platform.MapKit.MKCoordinateRegionMake
import platform.MapKit.MKCoordinateSpanMake
import platform.MapKit.MKMapView
import platform.MapKit.MKMapViewDelegateProtocol
import platform.MapKit.MKPointAnnotation
import platform.UIKit.accessibilityLabel
import platform.darwin.NSObject

@Composable
internal actual fun RewindGeographicMap(
    geometry: RewindMapGeometry,
    modifier: Modifier,
) {
    val center = geometry.center ?: return
    var loaded by remember(geometry) { mutableStateOf(false) }
    var failed by remember(geometry) { mutableStateOf(false) }
    val delegate =
        remember(geometry) {
            object : NSObject(), MKMapViewDelegateProtocol {
                override fun mapViewDidFinishRenderingMap(
                    mapView: MKMapView,
                    fullyRendered: Boolean,
                ) {
                    if (fullyRendered) {
                        loaded = true
                        failed = false
                    }
                }

                override fun mapViewDidFailLoadingMap(
                    mapView: MKMapView,
                    withError: NSError,
                ) {
                    failed = true
                }
            }
        }
    LaunchedEffect(geometry, loaded) {
        if (!loaded) {
            delay(15_000)
            failed = true
        }
    }
    val description = stringResource(Res.string.rewind_map_accessibility)
    Box(modifier) {
        key(geometry) {
            UIKitView(
                modifier = Modifier.fillMaxSize(),
                factory = {
                    MKMapView().apply {
                        this.delegate = delegate
                        showsUserLocation = false
                        showsCompass = false
                        showsScale = false
                        zoomEnabled = false
                        scrollEnabled = false
                        rotateEnabled = false
                        pitchEnabled = false
                        userInteractionEnabled = false
                        accessibilityLabel = description
                        addAnnotations(
                            geometry.points.map { point ->
                                MKPointAnnotation().apply {
                                    setCoordinate(CLLocationCoordinate2DMake(point.latitude, point.longitude))
                                }
                            },
                        )
                        setRegion(
                            MKCoordinateRegionMake(
                                CLLocationCoordinate2DMake(center.latitude, center.longitude),
                                MKCoordinateSpanMake(geometry.latitudeSpan, geometry.longitudeSpan),
                            ),
                            animated = false,
                        )
                    }
                },
                properties = UIKitInteropProperties(interactionMode = null, isNativeAccessibilityEnabled = true),
                onRelease = { it.delegate = null },
            )
        }
        if (!loaded) {
            RewindMapMessage(
                stringResource(if (failed) Res.string.rewind_map_unavailable else Res.string.rewind_map_loading),
                Modifier.align(Alignment.TopCenter),
            )
        }
    }
}
