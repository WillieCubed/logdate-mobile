package app.logdate.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.logdate.client.sensor.GyroOffset
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import kotlin.math.roundToInt


@Composable
fun ParallaxEnabledCanvas() {
}


@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ParallaxImage(
    uri: String,
    offset: GyroOffset,
    contentDescription: String,
    modifier: Modifier = Modifier,
    maxOffset: Dp = 25.dp,
) {

    // Dispose sensor listener when composable leaves composition
    DisposableEffect(Unit) {
        onDispose {
//            sensorManager.unregisterListener(gyroscopeListener)
        }
    }

    val offsetX = calculateOffset(offset.x, maxOffset)
    val offsetY = calculateOffset(offset.y, maxOffset)

    Box(
        modifier = modifier
            .fillMaxSize()
//            .pointerInteropFilter {
//                // Prevent touch events from interfering with parallax effect
//                true
//            }
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalPlatformContext.current)
                .data(uri)
                .build(),
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = offsetX.toPx()
                    translationY = offsetY.toPx()
                }
        )
    }
}

private fun calculateOffset(value: Float, maxOffset: Dp): Dp {
    val offset = (value * maxOffset.value).roundToInt().dp
    return offset.coerceIn(-maxOffset, maxOffset)
}

