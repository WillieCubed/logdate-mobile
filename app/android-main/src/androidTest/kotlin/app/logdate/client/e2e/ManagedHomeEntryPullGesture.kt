package app.logdate.client.e2e

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice

/** Owned managed-device touchscreen pull; retreat cancels without a navigation side effect. */
internal class ManagedHomeEntryPullGesture(
    private val device: UiDevice,
) {
    fun perform(
        retreat: Boolean,
        atArmed: () -> Unit,
    ) {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val downTime = SystemClock.uptimeMillis()
        val x = device.displayWidth * 0.5f
        val start = device.displayHeight * 0.22f
        val travel = device.displayHeight * 0.32f

        fun send(
            action: Int,
            fraction: Float,
        ) {
            val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, start + travel * fraction, 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            try {
                check(automation.injectInputEvent(event, true))
            } finally {
                event.recycle()
            }
        }
        send(MotionEvent.ACTION_DOWN, 0f)
        try {
            for (step in 1..16) {
                SystemClock.sleep(30)
                send(MotionEvent.ACTION_MOVE, step / 16f)
            }
            atArmed()
            if (retreat) {
                for (step in 15 downTo 0) {
                    SystemClock.sleep(25)
                    send(MotionEvent.ACTION_MOVE, step / 16f)
                }
            }
        } finally {
            send(MotionEvent.ACTION_UP, if (retreat) 0f else 1f)
        }
    }
}
