package app.logdate.client.e2e

import android.graphics.Rect
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Bounded native observations: no Compose clock or Espresso idling while a real finger is held. */
@Suppress("DEPRECATION")
internal class NativeEditorAssertions(
    private val device: UiDevice,
    private val packageName: String,
) {
    private val automation = InstrumentationRegistry.getInstrumentation().uiAutomation

    fun nodes(): List<AccessibilityNodeInfo> {
        check(automation.clearCache()) { "Native accessibility snapshot could not be refreshed" }
        val roots = automation.windows.mapNotNull { it.root }.ifEmpty { listOfNotNull(automation.rootInActiveWindow) }
        val pending = ArrayDeque(roots)
        val result = mutableListOf<AccessibilityNodeInfo>()
        var visited = 0
        while (pending.isNotEmpty() && visited++ < 3_000) {
            val node = pending.removeFirst()
            if (node.packageName?.toString() == packageName) result += node
            for (index in 0 until node.childCount) node.getChild(index)?.let(pending::addLast)
        }
        return result
    }

    fun await(
        message: String,
        timeout: Long = 10_000,
        condition: () -> Boolean,
    ) {
        val deadline = SystemClock.elapsedRealtime() + timeout
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition()) return
            SystemClock.sleep(40)
        }
        throw AssertionError("$message\n${describe()}")
    }

    fun description(value: String): AccessibilityNodeInfo? =
        nodes().firstOrNull { it.isVisibleToUser && it.contentDescription?.toString() == value }

    fun text(value: String): AccessibilityNodeInfo? = nodes().firstOrNull { it.isVisibleToUser && it.text?.toString() == value }

    fun input(): AccessibilityNodeInfo? = nodes().filter { it.isVisibleToUser && it.isEditable }.singleOrNull()

    fun assertNoInactivePullProgress() {
        val current = nodes()
        assertTrue(current.any { it.isVisibleToUser && it.isEditable })
        assertTrue(
            current.none {
                it.isVisibleToUser && it.className?.toString() == "android.widget.ProgressBar"
            },
            "Focused writing must not expose an inactive pull progress target",
        )
    }

    fun bounds(node: AccessibilityNodeInfo): Rect = Rect().also(node::getBoundsInScreen)

    fun tapDescription(value: String) {
        await("Visible enabled control: $value") { description(value)?.isEnabled == true }
        tap(checkNotNull(description(value)))
    }

    fun tap(node: AccessibilityNodeInfo) {
        assertTrue(node.isVisibleToUser && node.isEnabled)
        val rect = bounds(node)
        assertTrue(rect.width() > 0 && rect.height() > 0)
        check(device.click(rect.centerX(), rect.centerY()))
    }

    fun awaitInput(): AccessibilityNodeInfo {
        await("Exactly one visible editable input") { input() != null }
        return checkNotNull(input())
    }

    fun awaitStableInput(): AccessibilityNodeInfo {
        var previous: Rect? = null
        var stable = 0
        var result: AccessibilityNodeInfo? = null
        await("Stable native editable geometry", 8_000) {
            val node = input() ?: return@await false
            val current = bounds(node)
            stable = if (current == previous) stable + 1 else 0
            previous = current
            result = node
            stable >= 2
        }
        return checkNotNull(result)
    }

    fun memoryOwner(input: AccessibilityNodeInfo): AccessibilityNodeInfo {
        var ancestor = input.parent
        repeat(16) {
            val current = ancestor ?: error("Editable input has no selected memory owner")
            if (current.isSelected || current.isChecked) return AccessibilityNodeInfo.obtain(current)
            ancestor = current.parent
        }
        error("Editable input has no bounded selected memory owner")
    }

    fun assertArmedHomePull(
        output: File,
        name: String,
    ) {
        await("Armed physical Home pull", 3_000) { description("Release to start a new entry") != null }
        val source = checkNotNull(description("Release to start a new entry"))
        val rect = bounds(source)
        assertTrue(rect.left >= 0 && rect.right <= device.displayWidth)
        assertTrue(rect.width() > device.displayWidth / 2 && rect.height() > 0)
        assertTrue(description("Return to entry") == null, "Pull preview cannot mount focused editor controls")
        File(output, "$name.txt").writeText("Armed source bounds=$rect\n${describe()}")
        check(device.takeScreenshot(File(output, "$name.png")))
    }

    fun assertPartialBack(
        full: Rect,
        output: File,
    ) {
        await("Actual intermediate memory geometry during held Back", 3_000) {
            input()?.let { bounds(it).let { now -> now.height() < full.height() - 1 || now.width() < full.width() - 1 } } == true
        }
        check(device.takeScreenshot(File(output, "predictive-back-partial.png")))
    }

    fun assertText() {
        assertEquals("A quiet moment.", awaitInput().text?.toString())
    }

    fun describe(): String =
        nodes().take(150).joinToString("\n") {
            "${it.className} text=${it.text} desc=${it.contentDescription} " +
                "bounds=${bounds(it)} selected=${it.isSelected} checked=${it.isChecked}"
        }
}
