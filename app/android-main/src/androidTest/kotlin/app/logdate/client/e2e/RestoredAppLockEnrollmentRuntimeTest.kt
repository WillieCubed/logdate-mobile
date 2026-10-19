package app.logdate.client.e2e

import android.app.KeyguardManager
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import app.logdate.client.MainActivity
import app.logdate.client.datastore.LogdatePreferencesDataSource
import app.logdate.feature.core.AppViewModel
import app.logdate.feature.core.GlobalAppUiLoadedState
import app.logdate.feature.core.isAppUnlocked
import app.logdate.shared.model.user.AppSecurityLevel
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import java.io.File
import java.util.regex.Pattern
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class RestoredAppLockEnrollmentRuntimeTest {
    @Test
    fun canceledDestinationSecuritySetupKeepsTheRestoredJournalLocked() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assumeFalse(context.getSystemService(KeyguardManager::class.java).isDeviceSecure)
        val preferences = GlobalContext.get().get<LogdatePreferencesDataSource>()
        val original = runBlocking { preferences.userData.first().securityLevel }
        val device = UiDevice.getInstance(instrumentation)
        var scenario: ActivityScenario<MainActivity>? = null
        try {
            runBlocking { preferences.setShouldShowBiometric(true) }
            scenario = ActivityScenario.launch(MainActivity::class.java)
            assertTrue(
                device.wait(Until.hasObject(By.pkg("com.android.settings")), 90_000),
                "The restored app lock must offer native destination security enrollment",
            )
            device.pressBack()
            assertTrue(
                device.wait(Until.hasObject(By.text("Set up device security")), 30_000),
                "Canceled enrollment must return to the protected journal",
            )
            assertFalse(device.hasObject(By.pkg("com.android.settings")))
            assertEquals(AppSecurityLevel.BIOMETRIC, runBlocking { preferences.userData.first().securityLevel })
            assertTrue(device.takeScreenshot(File(context.externalCacheDir, "restored-lock-enrollment-canceled.png")))
            device.pressHome()
            context.startActivity(context.packageManager.getLaunchIntentForPackage(context.packageName))
            assertTrue(device.wait(Until.hasObject(By.text("Set up device security")), 30_000))
            assertFalse(device.hasObject(By.pkg("com.android.settings")), "Returning must not reopen canceled security setup")
            assertEquals(AppSecurityLevel.BIOMETRIC, runBlocking { preferences.userData.first().securityLevel })
        } finally {
            runBlocking { preferences.setShouldShowBiometric(original == AppSecurityLevel.BIOMETRIC) }
            scenario?.close()
        }
    }

    @Test
    fun destinationPinUnlocksAgainAfterBackgroundingAndActivityRecreation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        assertEquals("1", device.executeShellCommand("getprop ro.kernel.qemu").trim(), "Emulator-only security fixture")
        assumeFalse(context.getSystemService(KeyguardManager::class.java).isDeviceSecure)
        val preferences = GlobalContext.get().get<LogdatePreferencesDataSource>()
        val original = runBlocking { preferences.userData.first().securityLevel }
        var scenario: ActivityScenario<MainActivity>? = null
        var pinInstalled = false
        try {
            val setup = device.executeShellCommand("locksettings set-pin 135790")
            pinInstalled = setup.contains("Pin set", ignoreCase = true)
            assertTrue(pinInstalled, "The emulator must accept its temporary security fixture")
            runBlocking { preferences.setShouldShowBiometric(true) }
            scenario = ActivityScenario.launch(MainActivity::class.java)
            completeSystemPin(device)
            assertJournalUnlocked(scenario)

            device.pressHome()
            context.startActivity(context.packageManager.getLaunchIntentForPackage(context.packageName))
            // A fresh system challenge is required; a retained successful state cannot satisfy it.
            completeSystemPin(device)
            assertJournalUnlocked(scenario)

            scenario.close()
            scenario = ActivityScenario.launch(MainActivity::class.java)
            completeSystemPin(device)
            assertJournalUnlocked(scenario)
        } finally {
            runBlocking { preferences.setShouldShowBiometric(original == AppSecurityLevel.BIOMETRIC) }
            scenario?.close()
            if (pinInstalled) {
                val cleanup = device.executeShellCommand("locksettings clear --old 135790")
                assertTrue(cleanup.contains("cleared", ignoreCase = true), "The emulator security fixture must be removed")
            }
        }
    }

    private fun completeSystemPin(device: UiDevice) {
        if (device.wait(Until.hasObject(By.text("Use passcode")), 2_000)) {
            device.findObject(By.text("Use passcode")).click()
        }
        val input =
            device.wait(
                Until.findObject(
                    By.clazz("android.widget.EditText").pkg(Pattern.compile("com\\.android\\.(settings|systemui)")),
                ),
                90_000,
            )
        assertTrue(input != null, "Native device credential authentication must be offered")
        input.text = "135790"
        device.pressEnter()
    }

    private fun assertJournalUnlocked(scenario: ActivityScenario<MainActivity>) {
        lateinit var viewModel: AppViewModel
        scenario.onActivity { viewModel = ViewModelProvider(it)[AppViewModel::class.java] }
        runBlocking {
            withTimeout(30_000) {
                viewModel.uiState.filterIsInstance<GlobalAppUiLoadedState>().first { it.isAppUnlocked }
            }
        }
    }
}
