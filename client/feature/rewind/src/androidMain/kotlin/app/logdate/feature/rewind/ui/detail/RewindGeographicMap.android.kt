@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.rewind.ui.detail

import android.view.View
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.logdate.ui.maps.rememberGoogleMapsEnabled
import app.logdate.ui.platform.rememberSystemReduceMotion
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
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
    contentInsets: PaddingValues,
    modifier: Modifier,
) {
    val enabled = rememberGoogleMapsEnabled()
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val reduceMotion by rememberSystemReduceMotion()
    BoxWithConstraints(modifier) {
        val width = with(density) { maxWidth.roundToPx() }.coerceAtLeast(1)
        val height = with(density) { maxHeight.roundToPx() }.coerceAtLeast(1)
        val leftInset = with(density) { contentInsets.calculateLeftPadding(layoutDirection).roundToPx() }
        val rightInset = with(density) { contentInsets.calculateRightPadding(layoutDirection).roundToPx() }
        val topInset = with(density) { contentInsets.calculateTopPadding().roundToPx() }
        val bottomInset = with(density) { contentInsets.calculateBottomPadding().roundToPx() }
        val padding =
            minOf(
                with(density) { 32.dp.roundToPx() },
                (width - leftInset - rightInset).coerceAtLeast(1) / 5,
                (height - topInset - bottomInset).coerceAtLeast(1) / 5,
            )
        var image by remember(geometry, width, height, contentInsets) { mutableStateOf<ImageBitmap?>(null) }
        var timedOut by remember(geometry, width, height, contentInsets) { mutableStateOf(false) }
        if (enabled) {
            key(geometry, width, height, contentInsets) {
                AndroidView(
                    factory = { mapContext ->
                        val center = geometry.center!!
                        val zoom =
                            geometry.zoomForSize(
                                width - leftInset - rightInset,
                                height - topInset - bottomInset,
                                padding,
                                density.density,
                            )
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
                                        map.setMapStyle(MapStyleOptions(rewindMapStyle))
                                        map.uiSettings.setAllGesturesEnabled(false)
                                        map.uiSettings.isMapToolbarEnabled = false
                                        map.uiSettings.isZoomControlsEnabled = false
                                        map.uiSettings.isCompassEnabled = false
                                        map.setPadding(leftInset, topInset, rightInset, bottomInset)
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
        LaunchedEffect(image, geometry, width, height, contentInsets) {
            if (image == null) {
                delay(15_000)
                timedOut = true
            }
        }
        val reveal by animateFloatAsState(
            targetValue = if (image != null) 1f else 0f,
            animationSpec = tween(if (reduceMotion) 0 else 280),
            label = "RewindMapReveal",
        )
        val bitmap = image
        if (bitmap != null) {
            val loadedDescription = stringResource(Res.string.rewind_map_loaded)
            val loadingDescription = stringResource(Res.string.rewind_map_loading)
            Image(
                bitmap,
                stringResource(Res.string.rewind_map_accessibility),
                Modifier.fillMaxSize().graphicsLayer { alpha = reveal }.testTag("rewind-native-map").semantics {
                    stateDescription =
                        if (reveal >= 0.99f) loadedDescription else loadingDescription
                },
                contentScale = ContentScale.FillBounds,
            )
        } else {
            Crossfade(
                targetState =
                    when {
                        !enabled -> Res.string.rewind_map_not_configured
                        timedOut -> Res.string.rewind_map_unavailable
                        else -> Res.string.rewind_map_loading
                    },
                animationSpec = tween(if (reduceMotion) 0 else 180),
                modifier = Modifier.align(Alignment.Center),
                label = "RewindMapStatus",
            ) { message -> RewindMapMessage(stringResource(message)) }
        }
        // Keep hit testing in Compose so the story owns taps, holds and swipes.
        Box(
            Modifier.matchParentSize().pointerInput(Unit) {
                awaitPointerEventScope { while (true) awaitPointerEvent() }
            },
        )
    }
}

// A quiet geographic palette keeps white story text readable without darkening a band of tiles.
private val rewindMapStyle =
    """
    [
      {"elementType":"geometry","stylers":[{"color":"#242d32"}]},
      {"elementType":"labels.text.fill","stylers":[{"color":"#68767c"}]},
      {"elementType":"labels.text.stroke","stylers":[{"color":"#242d32"}]},
      {"elementType":"labels.icon","stylers":[{"visibility":"off"}]},
      {"featureType":"road","elementType":"geometry","stylers":[{"color":"#38454b"}]},
      {"featureType":"road.highway","elementType":"geometry","stylers":[{"color":"#45535a"}]},
      {"featureType":"water","elementType":"geometry","stylers":[{"color":"#193944"}]},
      {"featureType":"poi.park","elementType":"geometry","stylers":[{"color":"#2c3d36"}]}
    ]
    """.trimIndent()
