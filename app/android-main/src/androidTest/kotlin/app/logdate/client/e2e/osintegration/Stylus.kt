package app.logdate.client.e2e.osintegration

import android.graphics.Point
import android.graphics.PointF
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.platform.app.InstrumentationRegistry
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * A pen on the emulator's screen: each stroke presses down, moves through its points at a pen's
 * sampling rate, and lifts.
 *
 * The events come from one of the emulator's touch screens that also reports a stylus, and carry
 * the stylus tool type, so Android treats them as pen input: writing over a text field starts the
 * keyboard's handwriting mode, as it does with a real pen on a tablet.
 */
class Stylus {
    private val uiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation
    private val deviceId =
        InputDevice.getDeviceIds().firstOrNull { id ->
            InputDevice.getDevice(id)?.supportsSource(InputDevice.SOURCE_STYLUS) == true
        } ?: error("This device has no input device that reports a stylus")

    /** Touches the pen to [point] and lifts it. */
    fun tap(point: Point) {
        stroke(listOf(PointF(point)))
    }

    /**
     * Writes [strokes] one after another. [beforeLastLift] runs while the pen is still down at the
     * end of the last stroke, when the handwriting is drawn but not yet turned into text.
     */
    fun write(
        strokes: List<List<PointF>>,
        beforeLastLift: () -> Unit = {},
    ) {
        strokes.forEachIndexed { index, points ->
            stroke(points, beforeLift = if (index == strokes.lastIndex) beforeLastLift else ({}))
            Thread.sleep(PAUSE_BETWEEN_STROKES_MS)
        }
    }

    private fun stroke(
        points: List<PointF>,
        beforeLift: () -> Unit = {},
    ) {
        val downTime = SystemClock.uptimeMillis()
        send(MotionEvent.ACTION_DOWN, points.first(), downTime)
        points.zipWithNext().forEach { (from, to) ->
            val steps = ceil(hypot(to.x - from.x, to.y - from.y) / STEP_PX).toInt().coerceAtLeast(1)
            for (step in 1..steps) {
                val fraction = step.toFloat() / steps
                Thread.sleep(FRAME_MS)
                send(MotionEvent.ACTION_MOVE, PointF(from.x + (to.x - from.x) * fraction, from.y + (to.y - from.y) * fraction), downTime)
            }
        }
        beforeLift()
        send(MotionEvent.ACTION_UP, points.last(), downTime)
    }

    private fun send(
        action: Int,
        point: PointF,
        downTime: Long,
    ) {
        val properties =
            MotionEvent.PointerProperties().apply {
                id = 0
                toolType = MotionEvent.TOOL_TYPE_STYLUS
            }
        val coords =
            MotionEvent.PointerCoords().apply {
                x = point.x
                y = point.y
                pressure = if (action == MotionEvent.ACTION_UP) 0f else 1f
                size = 1f
            }
        val event =
            MotionEvent.obtain(
                downTime,
                SystemClock.uptimeMillis(),
                action,
                1,
                arrayOf(properties),
                arrayOf(coords),
                0,
                0,
                1f,
                1f,
                deviceId,
                0,
                InputDevice.SOURCE_STYLUS,
                0,
            )
        uiAutomation.injectInputEvent(event, true)
        event.recycle()
    }

    companion object {
        /** About 120 samples a second, the rate of a typical tablet pen. */
        private const val FRAME_MS = 8L
        private const val STEP_PX = 6f
        private const val PAUSE_BETWEEN_STROKES_MS = 120L

        /** How wide [hello] writes, in x-heights. */
        const val HELLO_WIDTH = 4.3f

        /**
         * The strokes of "hello" in joined-up print, starting at [origin] on the baseline, with
         * letters [unit] pixels tall at x-height.
         */
        fun hello(
            origin: PointF,
            unit: Float,
        ): List<List<PointF>> {
            fun at(
                x: Float,
                y: Float,
            ) = PointF(origin.x + x * unit, origin.y - y * unit)

            fun upright(x: Float) = listOf(at(x, 2.5f), at(x, 1.25f), at(x, 0f))
            val hump = listOf(at(0f, 0.6f), at(0.2f, 0.9f), at(0.5f, 1f), at(0.75f, 0.8f), at(0.8f, 0.5f), at(0.8f, 0f))
            val e =
                listOf(
                    at(1.2f, 0.5f),
                    at(1.6f, 0.5f),
                    at(1.95f, 0.5f),
                    at(1.92f, 0.75f),
                    at(1.75f, 0.96f),
                    at(1.55f, 1f),
                    at(1.35f, 0.88f),
                    at(1.22f, 0.45f),
                    at(1.27f, 0.12f),
                    at(1.47f, 0f),
                    at(1.72f, -0.02f),
                    at(1.97f, 0.12f),
                )
            val o =
                (0..13).map { step ->
                    val angle = step / 12f * 2 * PI.toFloat() + PI.toFloat()
                    at(3.8f + 0.5f * sin(angle), 0.5f - 0.5f * cos(angle))
                }
            return listOf(upright(0f), hump, e, upright(2.4f), upright(2.9f), o)
        }
    }
}
