package app.logdate.client.e2e.osintegration

import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import app.logdate.client.datastore.LogdatePreferencesDataSource
import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.datastore.featureflags.FeatureFlagStore
import app.logdate.client.media.audio.AudioRecordingManager
import app.logdate.client.repository.journals.JournalRepository
import app.logdate.client.repository.user.UserStateRepository
import app.logdate.shared.model.Journal
import kotlinx.coroutines.runBlocking
import org.koin.core.context.GlobalContext
import java.io.File
import java.util.regex.Pattern
import kotlin.time.Instant

/**
 * Captures real Android system UI (launcher, shade, notifications, bubbles) for the OS integration
 * screenshot lane.
 *
 * Compose screenshot tests cannot see these surfaces because Android draws them, so they are
 * captured on a Gradle Managed Device instead. SystemUI demo mode pins the clock, battery, and signal
 * so captures of the same state look the same from run to run.
 *
 * Images are written to the managed device's additional test output as
 * `os-integration/<surface>/<state>--<form factor>-api<level>.png`; `./run screenshots:os` copies
 * them into `docs/screenshots/android-os-integration/`.
 */
object OsSurfaceCapture {
    const val APP_LABEL = "LogDate"
    private const val TIMEOUT_MS = 10_000L
    private const val RECORDING_NOTIFICATION_ID = 1001

    val device: UiDevice by lazy { UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()) }

    /** `phone` below a 600dp smallest width, `tablet` at or above it. */
    val formFactor: String
        get() {
            val configuration =
                InstrumentationRegistry
                    .getInstrumentation()
                    .targetContext.resources.configuration
            return if (configuration.smallestScreenWidthDp >= 600) "tablet" else "phone"
        }

    private val outputRoot: File
        get() {
            val path =
                InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
                    ?: error("OS surface captures run on a Gradle Managed Device, which supplies additionalTestOutputDir")
            return File(path, "os-integration")
        }

    /** Runs [command] through the instrumentation's shell and returns its output. */
    fun shell(command: String): String {
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
    }

    /**
     * Runs [script] in a device shell, passed on standard input, so arguments with spaces or JSON
     * survive intact. [shell] splits its command on whitespace and cannot do that.
     */
    fun shellScript(script: String): String {
        val (output, input) = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommandRw("sh")
        ParcelFileDescriptor.AutoCloseOutputStream(input).bufferedWriter().use { it.write(script + "\n") }
        return ParcelFileDescriptor.AutoCloseInputStream(output).bufferedReader().use { it.readText() }
    }

    /** Pins the status bar to 9:41, full battery, and full signal, with no notification icons. */
    fun enterDemoMode() {
        shell("settings put global sysui_demo_allowed 1")
        demo("enter")
        demo("clock -e hhmm 0941")
        demo("battery -e level 100 -e plugged false")
        demo("network -e wifi show -e level 4 -e mobile show -e datatype none -e level 4")
        demo("notifications -e visible false")
    }

    fun exitDemoMode() {
        demo("exit")
    }

    private fun demo(command: String) {
        val parts = command.split(' ', limit = 2)
        val extras = parts.getOrNull(1).orEmpty()
        shell("am broadcast -a com.android.systemui.demo -e command ${parts[0]} $extras")
    }

    /** Saves the whole screen as the capture for [surface] in [state] and returns the file. */
    fun capture(
        surface: String,
        state: String,
    ): File {
        device.waitForIdle()
        val file = File(outputRoot, "$surface/$state--$formFactor-api${Build.VERSION.SDK_INT}.png")
        file.parentFile?.mkdirs()
        check(device.takeScreenshot(file)) { "Screenshot failed for $surface/$state" }
        return file
    }

    /** Swipes up through a list until [text] is fully on screen, then centers it a little. */
    fun scrollUntilText(
        text: String,
        maxSwipes: Int = 8,
    ) {
        val width = device.displayWidth
        val height = device.displayHeight
        repeat(maxSwipes) {
            val found = device.findObject(By.text(text))
            if (found != null && found.visibleBounds.bottom < height * 3 / 4) return
            device.swipe(width / 2, height * 3 / 4, width / 2, height / 3, 20)
            device.waitForIdle()
        }
        waitForText(text)
    }

    /** Saves the current view hierarchy under `failures/` for debugging; it is never published. */
    fun dumpHierarchy(name: String) {
        runCatching {
            val file = File(outputRoot, "failures/$name--$formFactor-api${Build.VERSION.SDK_INT}.xml")
            file.parentFile?.mkdirs()
            file.outputStream().use { device.dumpWindowHierarchy(it) }
        }
    }

    /** Saves [text] as evidence for [surface] in [state], next to the screenshots. */
    fun writeEvidence(
        surface: String,
        state: String,
        text: String,
    ): File {
        val file = File(outputRoot, "$surface/$state--$formFactor-api${Build.VERSION.SDK_INT}.txt")
        file.parentFile?.mkdirs()
        file.writeText(text)
        return file
    }

    /**
     * What Android decided about the recording notification's Live Update: whether the app may
     * promote, whether the notification qualifies, and whether it was promoted. Android 16 added
     * these checks; older versions report only the SDK level.
     */
    fun recordingPromotionReport(): String {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val notifications = context.getSystemService(NotificationManager::class.java)
        val recording = notifications.activeNotifications.firstOrNull { it.id == RECORDING_NOTIFICATION_ID }?.notification
        return buildString {
            appendLine("sdk=${Build.VERSION.SDK_INT}")
            appendLine("recordingNotificationPosted=${recording != null}")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA && recording != null) {
                appendLine("canPostPromotedNotifications=${notifications.canPostPromotedNotifications()}")
                appendLine("hasPromotableCharacteristics=${recording.hasPromotableCharacteristics()}")
                appendLine("promotedOngoing=${recording.flags and Notification.FLAG_PROMOTED_ONGOING != 0}")
            }
        }
    }

    /**
     * Opens the launcher's app list and returns LogDate's icon there. Its description is exactly the
     * app name; the hotseat's "Predicted app: LogDate" copy is skipped, because long-pressing a
     * prediction offers to pin it instead of listing the app's shortcuts.
     */
    fun findAppIconInLauncher(): UiObject2 {
        device.pressHome()
        device.waitForIdle()
        val width = device.displayWidth
        val height = device.displayHeight
        device.swipe(width / 2, height * 4 / 5, width / 2, height / 5, 12)
        return device.wait(Until.findObject(By.text(APP_LABEL).desc(APP_LABEL)), TIMEOUT_MS)
            ?: error("LogDate is not in the launcher's app list")
    }

    /** Long-presses LogDate's launcher icon so the launcher shows its shortcuts. */
    fun openLauncherShortcuts() {
        findAppIconInLauncher().longClick()
        device.waitForIdle()
    }

    /**
     * Waits for [text] on screen. If it never appears, saves what the screen showed instead (a
     * screenshot and the view hierarchy, under `failures/`) before failing, so the cause is visible.
     */
    fun waitForText(
        text: String,
        timeoutMs: Long = TIMEOUT_MS,
    ): UiObject2 {
        // Android 17 can show Quick Settings tiles as icons only, naming them in the description.
        val visible = By.text(text)
        val described = By.desc(Pattern.compile(".*\\Q$text\\E.*"))
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            (device.findObject(visible) ?: device.findObject(described))?.let { return it }
            Thread.sleep(250L)
        }
        val name = text.lowercase().replace(Regex("[^a-z0-9]+"), "-")
        runCatching { capture(surface = "failures", state = "missing-$name") }
        runCatching {
            File(outputRoot, "failures/missing-$name.xml").outputStream().use { device.dumpWindowHierarchy(it) }
        }
        error("'$text' did not appear")
    }

    val packageName: String
        get() = InstrumentationRegistry.getInstrumentation().targetContext.packageName

    /**
     * Puts the app in the state a returning person sees: onboarded, unlocked, and allowed to record
     * and post notifications, so system surfaces open straight into the editor.
     */
    fun prepareOnboardedApp() {
        runBlocking {
            val users = GlobalContext.get().get<UserStateRepository>()
            users.setBirthday(Instant.parse("1994-06-14T00:00:00Z"))
            users.setBiometricEnabled(false)
            users.setIsOnboardingComplete(true)
        }
        shell("pm grant $packageName android.permission.RECORD_AUDIO")
        shell("pm grant $packageName android.permission.POST_NOTIFICATIONS")
    }

    /** Turns on [flags] and waits for flag-gated components to follow. */
    fun enableFlags(vararg flags: FeatureFlag) {
        runBlocking {
            val store = GlobalContext.get().get<FeatureFlagStore>()
            flags.forEach { store.setEnabled(it, true) }
        }
        device.waitForIdle()
        Thread.sleep(1_000L)
    }

    /**
     * Waits until the flag-gated component [className] is enabled, then gives the launcher a moment
     * to notice the package change. Components switch asynchronously after a flag turns on.
     */
    fun awaitComponentEnabled(className: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val component = ComponentName(context, className)
        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            val state = context.packageManager.getComponentEnabledSetting(component)
            if (state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                Thread.sleep(3_000L)
                return
            }
            Thread.sleep(250L)
        }
        error("$className was not enabled")
    }

    /** Adds the tile served by [serviceClass] to Quick Settings, as if the person placed it. */
    fun addQuickSettingsTile(serviceClass: String) {
        shell("cmd statusbar add-tile $packageName/$serviceClass")
    }

    /** Opens the fully expanded Quick Settings panel. */
    fun openQuickSettings() {
        device.openQuickSettings()
        device.waitForIdle()
        Thread.sleep(800L)
    }

    /** Opens the notification shade. */
    fun openNotifications() {
        device.openNotification()
        device.waitForIdle()
        Thread.sleep(800L)
    }

    /** Turns LogDate's system search visibility, the consent assistants need to read entries, on or off. */
    fun setSystemSearchVisibility(enabled: Boolean) {
        runBlocking { GlobalContext.get().get<LogdatePreferencesDataSource>().setSystemSearchVisibilityEnabled(enabled) }
    }

    /** Creates a journal named [title] and returns its id. */
    fun createJournal(title: String): String =
        runBlocking { GlobalContext.get().get<JournalRepository>().create(Journal(title = title)).toString() }

    /** Opens the journal [journalId] through its deep link. */
    fun openJournal(journalId: String) {
        shell("am start -a android.intent.action.VIEW -d logdate://journal/$journalId -f 0x10000000 $packageName")
        device.wait(Until.hasObject(By.pkg(packageName).depth(0)), TIMEOUT_MS)
        device.waitForIdle()
    }

    /** Lets every LogDate conversation float as a bubble, as if the person chose "All conversations". */
    fun allowBubbles() {
        shell("cmd notification set_bubbles $packageName 1")
    }

    /** Opens the editor through the public capture action, as a shortcut or tile would. */
    fun openCapture(mode: String) {
        shell("am start -a app.logdate.action.CAPTURE --es mode $mode -f 0x10000000 $packageName")
        device.wait(Until.hasObject(By.pkg(packageName).depth(0)), TIMEOUT_MS)
        device.waitForIdle()
    }

    /** Stops any recording a test left running, so the next test starts from an idle recorder. */
    fun stopRecording() {
        runCatching { GlobalContext.get().get<AudioRecordingManager>().requestStopRecording() }
        Thread.sleep(1_000L)
    }

    fun collapseShade() {
        shell("cmd statusbar collapse")
        device.waitForIdle()
    }
}
