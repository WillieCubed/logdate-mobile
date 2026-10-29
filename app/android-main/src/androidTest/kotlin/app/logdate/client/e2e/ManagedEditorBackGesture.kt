package app.logdate.client.e2e

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice

/** Real touchscreen edge gestures on the owned managed emulator, including a physical reversal. */
internal class ManagedEditorBackGesture(
    private val device: UiDevice,
) {
    fun perform(
        cancel: Boolean,
        atPartialProgress: () -> Unit,
    ) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val down = SystemClock.uptimeMillis()
        val y = device.displayHeight * 0.45f
        val extent = device.displayWidth * if (cancel) 0.32f else 0.72f

        fun send(
            action: Int,
            x: Float,
        ) {
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            try {
                check(instrumentation.uiAutomation.injectInputEvent(event, true))
            } finally {
                event.recycle()
            }
        }
        send(MotionEvent.ACTION_DOWN, 1f)
        try {
            for (step in 1..12) {
                SystemClock.sleep(20)
                send(MotionEvent.ACTION_MOVE, 1f + extent * step / 12f)
                if (step == 6) atPartialProgress()
            }
            if (cancel) {
                for (step in 11 downTo 0) {
                    SystemClock.sleep(25)
                    send(MotionEvent.ACTION_MOVE, 1f + extent * step / 12f)
                }
            }
        } finally {
            send(MotionEvent.ACTION_UP, if (cancel) 1f else 1f + extent)
        }
    }
}
