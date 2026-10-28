package app.logdate.client.e2e

import android.app.Application
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import app.logdate.client.MainActivity
import app.logdate.client.datastore.EntryStartMode
import app.logdate.client.datastore.LogdatePreferencesDataSource
import app.logdate.client.ensureLogDateApplicationStarted
import app.logdate.client.testing.onboarding.DEBUG_SKIP_ONBOARDING_EXTRA
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.koin.core.context.GlobalContext
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** Real app, OS-injected taps and unedited normal-speed recording; only an owned Managed Device. */
class EditorEntrySwitchVideoTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test(timeout = 120_000)
    fun homeToEmptyTypeSwitchAndContentHidesCreationChoices() {
        check(Build.HARDWARE in setOf("ranchu", "goldfish"))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        ensureLogDateApplicationStarted(context.applicationContext as Application)
        val device = UiDevice.getInstance(instrumentation)
        val preferences = GlobalContext.get().get<LogdatePreferencesDataSource>()
        val previousMode = runBlocking { preferences.observeEntryStartMode().first() }
        val previousTouches = device.executeShellCommand("settings get system show_touches").trim()
        val previousIme = device.executeShellCommand("settings get secure show_ime_with_hard_keyboard").trim()
        val output = File(checkNotNull(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")), "editor-entry-switch")
        output.mkdirs()
        val token = Uuid.random().toString()
        val remoteVideo = "/sdcard/logdate-entry-switch-$token.mp4"
        val remotePid = "/sdcard/logdate-entry-switch-$token.pid"
        val video = File(output, "home-entry-type-switch.mp4")
        try {
            device.executeShellCommand("settings put system show_touches 1")
            device.executeShellCommand("settings put secure show_ime_with_hard_keyboard 1")
            runBlocking { preferences.setEntryStartMode(EntryStartMode.TEXT) }
            val intent = Intent(context, MainActivity::class.java).putExtra(DEBUG_SKIP_ONBOARDING_EXTRA, true)
            ActivityScenario.launch<MainActivity>(intent).use { scenario ->
                scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
                val create = hasText("Create Entry") or hasContentDescription("Create new entry") or hasContentDescription("Add a memory")
                compose.waitUntil(25_000) { compose.onAllNodes(create).fetchSemanticsNodes().isNotEmpty() }
                device.waitForIdle(2_000)
                val executor = Executors.newSingleThreadExecutor()
                val script = File(output, "record-screen-$token.sh")
                script.writeText(
                    "#!/system/bin/sh\necho \$\$ > $remotePid\n" +
                        "exec screenrecord --size 720x1600 --bit-rate 4000000 --time-limit 30 $remoteVideo\n",
                )
                val command = "sh ${script.absolutePath}"
                val stream = ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command))
                val reader =
                    executor.submit {
                        stream.use { input ->
                            File(output, "screenrecord.log").outputStream().use { outputStream -> input.copyTo(outputStream) }
                        }
                    }
                var pid: Int? = null
                val startedAt = SystemClock.elapsedRealtime()
                try {
                    compose.waitUntil(5_000) {
                        pid = device.executeShellCommand("cat $remotePid").trim().toIntOrNull()
                        pid != null
                    }
                    SystemClock.sleep(2_000)
                    tap(device, compose.onAllNodes(create).onFirst())
                    compose.waitUntil(15_000) { compose.onAllNodesWithTag("editor_text_input").fetchSemanticsNodes().isNotEmpty() }
                    compose.onNodeWithTag("entry_capture_tools").assertIsDisplayed()
                    SystemClock.sleep(1_500)
                    tap(device, compose.onNodeWithContentDescription("Handwrite"))
                    compose.waitUntil(10_000) { compose.onAllNodesWithTag("handwriting_focus_surface").fetchSemanticsNodes().isNotEmpty() }
                    SystemClock.sleep(2_000)
                    tap(device, compose.onNodeWithContentDescription("Write"))
                    compose.waitUntil(10_000) { compose.onAllNodesWithTag("editor_text_input").fetchSemanticsNodes().isNotEmpty() }
                    SystemClock.sleep(1_500)
                    tap(device, compose.onNodeWithTag("editor_text_input"))
                    for (word in listOf("A", "%squiet", "%smoment.")) {
                        device.executeShellCommand("input text $word")
                        SystemClock.sleep(500)
                    }
                    compose.onNodeWithTag("editor_text_input").assertTextContains("A quiet moment.")
                    compose.waitForIdle()
                    compose.onNodeWithTag("entry_capture_tools").assertIsNotDisplayed()
                    SystemClock.sleep(2_000)
                    tap(device, compose.onNodeWithContentDescription("Return to entry"))
                    compose.waitUntil(10_000) { compose.onAllNodesWithTag("entry_journal_chooser").fetchSemanticsNodes().isNotEmpty() }
                    compose.onNodeWithTag("entry_add_memory").assertIsDisplayed()
                    SystemClock.sleep(2_000)
                    assertTrue(!reader.isDone, "The full journey must finish within the bounded normal-speed recording")
                    val remaining = 12_000 - (SystemClock.elapsedRealtime() - startedAt)
                    if (remaining > 0) SystemClock.sleep(remaining)
                    check(device.takeScreenshot(File(output, "typed-memory-retained.png")))
                } finally {
                    pid?.let { device.executeShellCommand("kill -2 $it") }
                    try {
                        reader.get(35, TimeUnit.SECONDS)
                    } finally {
                        executor.shutdown()
                        executor.awaitTermination(5, TimeUnit.SECONDS)
                    }
                    device.executeShellCommand("cp $remoteVideo ${video.absolutePath}")
                    device.executeShellCommand("rm -f $remoteVideo $remotePid")
                }
                assertTrue(video.isFile && video.length() > 10_000, "Screen recording was not retained")
            }
        } finally {
            runBlocking { preferences.setEntryStartMode(previousMode) }
            restoreSetting(device, "system", "show_touches", previousTouches)
            restoreSetting(device, "secure", "show_ime_with_hard_keyboard", previousIme)
        }
    }

    private fun tap(
        device: UiDevice,
        node: SemanticsNodeInteraction,
    ) {
        node.assertIsDisplayed()
        val center = node.fetchSemanticsNode().boundsInRoot.center
        check(device.click(center.x.roundToInt(), center.y.roundToInt()))
    }

    private fun restoreSetting(
        device: UiDevice,
        namespace: String,
        name: String,
        previous: String,
    ) {
        device.executeShellCommand(
            if (previous ==
                "null"
            ) {
                "settings delete $namespace $name"
            } else {
                "settings put $namespace $name $previous"
            },
        )
    }
}
