package app.logdate.client.e2e

import android.app.Instrumentation
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.compose.ui.geometry.Offset

/** Real, interpolated Android input with the production frame clock, confined to the owned GMD. */
internal class PlacesSheetPointer(
    private val instrumentation: Instrumentation,
    private var position: Offset,
) : AutoCloseable {
    private val downTime = SystemClock.uptimeMillis()

    init {
        send(MotionEvent.ACTION_DOWN)
    }

    fun moveBy(
        delta: Offset,
        durationMillis: Long,
    ) {
        val start = position
        val steps = (durationMillis / 16).toInt().coerceAtLeast(1)
        repeat(steps) { step ->
            position = start + delta * ((step + 1f) / steps)
            send(MotionEvent.ACTION_MOVE)
            SystemClock.sleep(durationMillis / steps)
        }
    }

    override fun close() = send(MotionEvent.ACTION_UP)

    private fun send(action: Int) {
        val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, position.x, position.y, 0)
        try {
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            check(instrumentation.uiAutomation.injectInputEvent(event, true))
        } finally {
            event.recycle()
        }
    }
}
