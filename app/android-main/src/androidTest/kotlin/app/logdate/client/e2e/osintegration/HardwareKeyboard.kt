package app.logdate.client.e2e.osintegration

import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import java.io.BufferedWriter

/**
 * A USB keyboard plugged into the emulator for as long as this object stays open, registered
 * through Android's `uinput` shell tool.
 *
 * Android 17's keyboard shortcut helper lists an app's shortcuts only for keys that the active
 * keyboard has. Key events that UiAutomator injects come from Android's virtual keyboard, which
 * reports no physical keys, so the helper drops every app shortcut. Key presses sent through this
 * device come from a real evdev keyboard instead, the same as a keyboard attached to a tablet.
 *
 * Key codes are Linux evdev codes (`KEY_*` in `linux/input-event-codes.h`), not Android key codes.
 */
class HardwareKeyboard : AutoCloseable {
    private val streams = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommandRw("uinput -")
    private val output = ParcelFileDescriptor.AutoCloseInputStream(streams[0])
    private val commands: BufferedWriter = ParcelFileDescriptor.AutoCloseOutputStream(streams[1]).bufferedWriter()

    init {
        send(
            """
            {"id": $DEVICE_ID, "command": "register", "name": "LogDate test keyboard", "vid": 6353, "pid": 43981,
             "bus": "usb", "configuration": [
               {"type": "UI_SET_EVBIT", "data": [$EV_KEY]},
               {"type": "UI_SET_KEYBIT", "data": [${(1..KEY_MAX_STANDARD).joinToString()}]}
             ]}
            """.trimIndent(),
        )
        // InputReader adds the device asynchronously.
        Thread.sleep(REGISTER_DELAY_MS)
    }

    /** Presses [keys] in order, holding each, then releases them in reverse, like a chord. */
    fun press(vararg keys: Int) {
        val events =
            keys.flatMap { listOf(EV_KEY, it, 1, EV_SYN, SYN_REPORT, 0) } +
                keys.reversed().flatMap { listOf(EV_KEY, it, 0, EV_SYN, SYN_REPORT, 0) }
        send("""{"id": $DEVICE_ID, "command": "inject", "events": [${events.joinToString()}]}""")
    }

    override fun close() {
        // Closing standard input ends `uinput`, which unplugs the keyboard.
        commands.close()
        output.close()
    }

    private fun send(command: String) {
        commands.write(command)
        commands.newLine()
        commands.flush()
    }

    companion object {
        const val KEY_V = 47
        const val KEY_SLASH = 53
        const val KEY_LEFTCTRL = 29
        const val KEY_LEFTMETA = 125

        private const val DEVICE_ID = 1
        private const val EV_SYN = 0
        private const val EV_KEY = 1
        private const val SYN_REPORT = 0

        /** The main keyboard block, Escape through the Meta keys. */
        private const val KEY_MAX_STANDARD = 127
        private const val REGISTER_DELAY_MS = 1_500L
    }
}
