package app.logdate.reminders.e2e

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import app.logdate.client.domain.reminders.ReminderPreferencesState
import app.logdate.feature.core.settings.ui.ReminderSettingsContent
import app.logdate.ui.theme.LogDateTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class ReminderSettingsVisualTest {
    @get:Rule val compose = createComposeRule()

    @Test fun populatedRemindersAtDefaultDarkLargeTextAndRtl() {
        var variant by mutableIntStateOf(0)
        compose.setContent {
            key(variant) {
                val density = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(density.density, if (variant == 2) 2f else 1f),
                    LocalLayoutDirection provides if (variant == 3) LayoutDirection.Rtl else LayoutDirection.Ltr,
                ) {
                    LogDateTheme(darkTheme = variant == 1, dynamicColor = false) {
                        ReminderSettingsContent(
                            state = ReminderPreferencesState(ready = true, rolloutEnabled = true, enabled = true),
                            notificationsAllowed = false,
                            locationAllowed = false,
                            streaksEnabled = true,
                            busy = false,
                            saveFailed = false,
                            onEnabled = {},
                            onFix = {},
                            onBack = {},
                        )
                    }
                }
            }
        }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val configuration = instrumentation.targetContext.resources.configuration
        val deviceLabel = "${configuration.smallestScreenWidthDp}dp-api${android.os.Build.VERSION.SDK_INT}"
        val output =
            File(
                InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
                    ?: error("Use a Gradle Managed Device for visual acceptance"),
            )
        output.mkdirs()
        val device = UiDevice.getInstance(instrumentation)

        fun capture(name: String) {
            assertFalse(device.hasObject(By.textContains("isn't responding")), "A system error dialog invalidates visual acceptance")
            assertTrue(device.takeScreenshot(File(output, name)))
        }
        listOf("light", "dark", "large-text", "rtl").forEachIndexed { index, label ->
            compose.runOnIdle { variant = index }
            compose.waitForIdle()
            compose.onNodeWithText("At most one reminder a day, on your phone or watch.").assertIsDisplayed()
            capture("reminders-$deviceLabel-$label-top.png")
            compose.onNodeWithText("After a workout").performScrollTo().assertIsDisplayed()
            capture("reminders-$deviceLabel-$label-bottom.png")
        }
    }
}
