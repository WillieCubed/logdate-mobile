package app.logdate.feature.editor.ui.canvas.interactive

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.shareIn
import javax.inject.Inject
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
    val context = LocalContext.current
    val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    val gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

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
            .pointerInteropFilter {
                // Prevent touch events from interfering with parallax effect
                true
            }
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
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

data class GyroOffset(val x: Float, val y: Float)

interface GyroSensorProvider {
    val currentValue: SharedFlow<GyroOffset>

    /**
     * Releases resources and observers associated with the sensor provider.
     */
    fun cleanup()
}

class AndroidGyroSensorProvider @Inject constructor(
    coroutineScope: CoroutineScope,
    @ApplicationContext context: Context,
) : GyroSensorProvider {
    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val _currentValue = MutableSharedFlow<GyroOffset>()

    private val gyroscopeListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val (x, y) = event.values
            _currentValue.tryEmit(GyroOffset(x, y))
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
            // No-op
        }
    }

    init {
        if (gyroscope != null) {
            sensorManager.registerListener(
                gyroscopeListener,
                gyroscope,
                SensorManager.SENSOR_DELAY_NORMAL
            )
        }
    }

    override val currentValue: SharedFlow<GyroOffset> = _currentValue.shareIn(
        coroutineScope,
        started = SharingStarted.WhileSubscribed(),
    )

    override fun cleanup() {
        sensorManager.unregisterListener(gyroscopeListener)
    }
}