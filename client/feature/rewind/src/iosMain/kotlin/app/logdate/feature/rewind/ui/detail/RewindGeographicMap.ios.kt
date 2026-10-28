@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.rewind.ui.detail

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import app.logdate.ui.platform.rememberSystemReduceMotion
import kotlinx.cinterop.useContents
import kotlinx.coroutines.delay
import logdate.client.feature.rewind.generated.resources.Res
import logdate.client.feature.rewind.generated.resources.rewind_map_accessibility
import logdate.client.feature.rewind.generated.resources.rewind_map_loading
import logdate.client.feature.rewind.generated.resources.rewind_map_unavailable
import org.jetbrains.compose.resources.stringResource
import platform.CoreGraphics.CGRectMake
import platform.CoreLocation.CLLocationCoordinate2DMake
import platform.Foundation.NSError
import platform.MapKit.MKMapPointForCoordinate
import platform.MapKit.MKMapRectMake
import platform.MapKit.MKMapRectWorld
import platform.MapKit.MKMapView
import platform.MapKit.MKMapViewDelegateProtocol
import platform.MapKit.MKPointAnnotation
import platform.UIKit.UIEdgeInsetsMake
import platform.UIKit.UIUserInterfaceStyle
import platform.UIKit.accessibilityLabel
import platform.darwin.NSObject

@Composable
internal actual fun RewindGeographicMap(
    geometry: RewindMapGeometry,
    contentInsets: PaddingValues,
    modifier: Modifier,
) {
    val center = geometry.center ?: return
    val reduceMotion by rememberSystemReduceMotion()
    var loaded by remember(geometry, contentInsets) { mutableStateOf(false) }
    var failed by remember(geometry, contentInsets) { mutableStateOf(false) }
    val delegate =
        remember(geometry, contentInsets) {
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
    LaunchedEffect(geometry, contentInsets, loaded) {
        if (!loaded) {
            delay(15_000)
            failed = true
        }
    }
    val direction = LocalLayoutDirection.current
    val description = stringResource(Res.string.rewind_map_accessibility)
    val reveal by animateFloatAsState(
        targetValue = if (loaded) 1f else 0.01f,
        animationSpec = tween(if (reduceMotion) 0 else 280),
        label = "RewindMapReveal",
    )

    fun longitude(value: Double) = ((value + 540.0) % 360.0) - 180.0
    val northwest =
        MKMapPointForCoordinate(
            CLLocationCoordinate2DMake(
                (center.latitude + geometry.latitudeSpan / 2).coerceAtMost(85.0),
                longitude(
                    center.longitude - geometry.longitudeSpan / 2,
                ),
            ),
        )
    val southeast =
        MKMapPointForCoordinate(
            CLLocationCoordinate2DMake(
                (center.latitude - geometry.latitudeSpan / 2).coerceAtLeast(-85.0),
                longitude(
                    center.longitude + geometry.longitudeSpan / 2,
                ),
            ),
        )
    val left = northwest.useContents { x }
    val right = southeast.useContents { x }.let { if (it < left) it + MKMapRectWorld.size.width else it }
    val top = northwest.useContents { y }
    val rectangle = MKMapRectMake(left, top, right - left, southeast.useContents { y } - top)
    Box(modifier) {
        key(geometry, contentInsets) {
            UIKitView(
                modifier = Modifier.fillMaxSize(),
                factory = {
                    object : MKMapView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)) {
                        private var framed = false

                        override fun layoutSubviews() {
                            super.layoutSubviews()
                            if (!framed && bounds.useContents { size.width > 0 && size.height > 0 }) {
                                framed = true
                                setVisibleMapRect(
                                    rectangle,
                                    edgePadding =
                                        UIEdgeInsetsMake(
                                            contentInsets.calculateTopPadding().value.toDouble(),
                                            contentInsets.calculateLeftPadding(direction).value.toDouble(),
                                            contentInsets.calculateBottomPadding().value.toDouble(),
                                            contentInsets.calculateRightPadding(direction).value.toDouble(),
                                        ),
                                    animated = false,
                                )
                            }
                        }
                    }.apply {
                        overrideUserInterfaceStyle = UIUserInterfaceStyle.UIUserInterfaceStyleDark
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
                    }
                },
                update = { it.alpha = reveal.toDouble() },
                properties = UIKitInteropProperties(interactionMode = null, isNativeAccessibilityEnabled = true),
                onRelease = { it.delegate = null },
            )
        }
        if (!loaded) {
            RewindMapMessage(
                stringResource(if (failed) Res.string.rewind_map_unavailable else Res.string.rewind_map_loading),
                Modifier.align(Alignment.Center),
            )
        }
    }
}
