package app.logdate.client.e2e

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import app.logdate.client.MainActivity
import app.logdate.client.testing.launch.ActivityLaunchTestOverrides
import app.logdate.client.testing.navigation.NavigationTestDestination
import app.logdate.client.testing.onboarding.OnboardingTestFixture
import app.logdate.feature.core.settings.ui.devices.CodeScannerAvailability
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.koin.core.context.loadKoinModules
import org.koin.core.context.unloadKoinModules
import org.koin.dsl.module
import java.io.File
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class DeviceApprovalSettingsE2ETest {
    // Managed-device images cannot download the Google code scanner module, so the layout check
    // stands in a scanner that is always available.
    private val scannerModule = module { single<CodeScannerAvailability> { CodeScannerAvailability { true } } }

    private val launchOverride =
        object : ExternalResource() {
            override fun before() {
                loadKoinModules(scannerModule)
                ActivityLaunchTestOverrides.onboardingFixture = OnboardingTestFixture.ONBOARDED_HOME
                ActivityLaunchTestOverrides.navigationDestination = NavigationTestDestination.Devices
            }

            override fun after() {
                ActivityLaunchTestOverrides.clear()
                unloadKoinModules(scannerModule)
            }
        }
    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val ruleChain: RuleChain = RuleChain.outerRule(launchOverride).around(composeRule)

    @Test
    fun `existing device leads the real Devices screen`() {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("Connect a device").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Connect a device").assertIsDisplayed().assertHasClickAction()
        val currentDeviceTop = composeRule.onNodeWithText("This device").fetchSemanticsNode().boundsInRoot.top
        val connectTop = composeRule.onNodeWithText("Connect a device").fetchSemanticsNode().boundsInRoot.top
        assertTrue(connectTop > currentDeviceTop)
        val existingDeviceBottom = composeRule.onNodeWithTag("existing-device-card").fetchSemanticsNode().boundsInRoot.bottom
        assertTrue(connectTop > existingDeviceBottom)
        val existingDeviceHeight = composeRule.onNodeWithTag("existing-device-card").fetchSemanticsNode().boundsInRoot.height
        val connectHeight = composeRule.onNodeWithTag("connect-device-action").fetchSemanticsNode().boundsInRoot.height
        assertTrue(existingDeviceHeight > connectHeight)
        val outputDir = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
            ?: error("Run this capture using a Gradle Managed Device")
        val screenshot = File(outputDir, "device-approval-settings.png")
        screenshot.parentFile?.mkdirs()
        assertTrue(UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).takeScreenshot(screenshot))
        composeRule.onNodeWithContentDescription("Device options").performClick()
        composeRule.onNodeWithText("Reset Device ID").assertIsDisplayed()
    }
}
