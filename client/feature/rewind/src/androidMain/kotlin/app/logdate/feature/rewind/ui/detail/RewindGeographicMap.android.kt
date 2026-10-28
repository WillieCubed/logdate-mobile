@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.rewind.ui.detail

import android.view.View
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.logdate.ui.maps.rememberGoogleMapsEnabled
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import kotlinx.coroutines.delay
import logdate.client.feature.rewind.generated.resources.Res
import logdate.client.feature.rewind.generated.resources.rewind_map_accessibility
import logdate.client.feature.rewind.generated.resources.rewind_map_loaded
import logdate.client.feature.rewind.generated.resources.rewind_map_loading
import logdate.client.feature.rewind.generated.resources.rewind_map_not_configured
import logdate.client.feature.rewind.generated.resources.rewind_map_unavailable
import org.jetbrains.compose.resources.stringResource

@Composable
internal actual fun RewindGeographicMap(
    geometry: RewindMapGeometry,
    modifier: Modifier,
) {
    val enabled = rememberGoogleMapsEnabled()
    val density = LocalDensity.current
    BoxWithConstraints(modifier) {
        val width = with(density) { maxWidth.roundToPx() }.coerceAtLeast(1)
        val height = with(density) { maxHeight.roundToPx() }.coerceAtLeast(1)
        val padding = minOf(with(density) { 32.dp.roundToPx() }, width / 5, height / 5)
        var image by remember(geometry, width, height) { mutableStateOf<ImageBitmap?>(null) }
        var timedOut by remember(geometry, width, height) { mutableStateOf(false) }
        if (enabled) {
            key(geometry, width, height) {
                AndroidView(
                    factory = { mapContext ->
                        val center = geometry.center!!
                        val zoom = geometry.zoomForSize(width, height, padding, density.density)
                        val target = LatLng(center.latitude, center.longitude)
                        MapView(mapContext, GoogleMapOptions().liteMode(true).camera(CameraPosition.fromLatLngZoom(target, zoom))).apply {
                            // Keep the staging renderer drawable without letting a
                            // native view cover story chrome before the snapshot arrives.
                            alpha = 0.01f
                            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                            isClickable = false
                            onCreate(null)
                            onStart()
                            onResume()
                            var requested = false
                            addOnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
                                if (!requested && right > left && bottom > top) {
                                    requested = true
                                    getMapAsync { map ->
                                        map.uiSettings.setAllGesturesEnabled(false)
                                        map.uiSettings.isMapToolbarEnabled = false
                                        map.uiSettings.isZoomControlsEnabled = false
                                        map.uiSettings.isCompassEnabled = false
                                        val attributionInset = with(density) { 12.dp.roundToPx() }
                                        map.setPadding(attributionInset, attributionInset, attributionInset, attributionInset)
                                        map.isMyLocationEnabled = false
                                        geometry.points.forEach { point ->
                                            map.addMarker(MarkerOptions().position(LatLng(point.latitude, point.longitude)))
                                        }
                                        map.moveCamera(CameraUpdateFactory.newLatLngZoom(target, zoom))
                                        map.setOnMapLoadedCallback {
                                            if (isAttachedToWindow) {
                                                map.snapshot { bitmap ->
                                                    if (bitmap != null && isAttachedToWindow) {
                                                        image = bitmap.asImageBitmap()
                                                        visibility = View.INVISIBLE
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    onRelease = { view ->
                        view.onPause()
                        view.onStop()
                        view.onDestroy()
                    },
                )
            }
        }
        LaunchedEffect(image, geometry, width, height) {
            if (image == null) {
                delay(15_000)
                timedOut = true
            }
        }
        val bitmap = image
        if (bitmap != null) {
            val loadedDescription = stringResource(Res.string.rewind_map_loaded)
            Image(
                bitmap,
                stringResource(Res.string.rewind_map_accessibility),
                Modifier.fillMaxSize().testTag("rewind-native-map").semantics { stateDescription = loadedDescription },
                contentScale = ContentScale.FillBounds,
            )
        } else {
            RewindMapMessage(
                stringResource(
                    when {
                        !enabled -> Res.string.rewind_map_not_configured
                        timedOut -> Res.string.rewind_map_unavailable
                        else -> Res.string.rewind_map_loading
                    },
                ),
                Modifier.align(Alignment.Center),
            )
        }
        // Keep hit testing in Compose so the story owns taps, holds and swipes.
        Box(
            Modifier.matchParentSize().pointerInput(Unit) {
                awaitPointerEventScope { while (true) awaitPointerEvent() }
            },
        )
    }
}
