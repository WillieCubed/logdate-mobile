package app.logdate.wear.e2e

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.wear.compose.material3.Text
import app.logdate.wear.v2.ambient.WatchAmbientScope
import app.logdate.wear.v2.ambient.WearSdkAmbient
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertFalse

/**
 * The Wear OS 5 emulator has no Wear SDK ambient service. An ambient scope switched on there must
 * not reach `rememberAmbientModeManager`, which would crash.
 */
@RunWith(AndroidJUnit4::class)
class AmbientSupportTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun wear_os_5_has_no_wear_sdk_ambient_service() {
        assertFalse(WearSdkAmbient.isAvailable(ApplicationProvider.getApplicationContext()))
    }

    @Test
    fun an_ambient_scope_switched_on_without_the_service_shows_its_content() {
        composeRule.setContent {
            WatchAmbientScope(enabled = true) { Text("Recording", modifier = Modifier.testTag(CONTENT)) }
        }

        composeRule.onNodeWithTag(CONTENT).assertIsDisplayed()
    }

    private companion object {
        const val CONTENT = "ambient_content"
    }
}
