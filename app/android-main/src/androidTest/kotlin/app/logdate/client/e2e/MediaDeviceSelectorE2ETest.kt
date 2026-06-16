package app.logdate.client.e2e

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.logdate.client.media.device.MediaDeviceCategory
import app.logdate.client.media.device.MediaDeviceKind
import app.logdate.client.media.device.MediaDeviceSelectionUiState
import app.logdate.client.media.device.MediaDeviceUiState
import app.logdate.ui.media.MediaDeviceSelectorSheet
import app.logdate.ui.media.MediaDeviceSelectorTags
import app.logdate.ui.media.MediaRouteSettingsAction
import app.logdate.ui.theme.LogDateTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaDeviceSelectorE2ETest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun controllableCameraRowsSelectDeviceFromTheRow() {
        var selectedDeviceId: String? = null
        val selection =
            MediaDeviceSelectionUiState(
                kind = MediaDeviceKind.CAMERA,
                devices =
                    listOf(
                        MediaDeviceUiState(
                            id = "back-camera",
                            label = "Back camera",
                            kind = MediaDeviceKind.CAMERA,
                            category = MediaDeviceCategory.BACK_CAMERA,
                        ),
                        MediaDeviceUiState(
                            id = "usb-camera",
                            label = "USB camera",
                            kind = MediaDeviceKind.CAMERA,
                            category = MediaDeviceCategory.USB,
                            isExternal = true,
                        ),
                    ),
                selectedDeviceId = "back-camera",
            )

        composeRule.setContent {
            LogDateTheme {
                MediaDeviceSelectorSheet(
                    selection = selection,
                    title = "Camera",
                    onDeviceSelected = { selectedDeviceId = it },
                    systemSettingsAction = null,
                    onDismiss = {},
                )
            }
        }

        composeRule
            .onNodeWithTag(MediaDeviceSelectorTags.deviceRow("Camera", "back-camera"))
            .assertIsDisplayed()
            .assertIsSelected()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "In use"))
            .assertHasClickAction()

        composeRule
            .onNodeWithTag(MediaDeviceSelectorTags.deviceRow("Camera", "usb-camera"))
            .assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "External device"))
            .assertHasClickAction()
            .performClick()

        assertEquals("usb-camera", selectedDeviceId)
        composeRule.onNodeWithText("Tap a device to switch.").assertIsDisplayed()
    }

    @Test
    fun systemControlledOutputShowsSettingsAffordance() {
        var openedSettings = false
        val selection =
            MediaDeviceSelectionUiState(
                kind = MediaDeviceKind.AUDIO_OUTPUT,
                devices =
                    listOf(
                        MediaDeviceUiState(
                            id = "speaker",
                            label = "Phone speaker",
                            kind = MediaDeviceKind.AUDIO_OUTPUT,
                            category = MediaDeviceCategory.BUILT_IN,
                        ),
                        MediaDeviceUiState(
                            id = "bluetooth",
                            label = "Bluetooth headphones",
                            kind = MediaDeviceKind.AUDIO_OUTPUT,
                            category = MediaDeviceCategory.BLUETOOTH,
                            isExternal = true,
                        ),
                    ),
                selectedDeviceId = "speaker",
                isSelectionControllable = false,
                routeControlMessage = "Use Android's output switcher to route playback.",
            )

        composeRule.setContent {
            LogDateTheme {
                MediaDeviceSelectorSheet(
                    selection = selection,
                    title = "Audio output",
                    onDeviceSelected = {},
                    systemSettingsAction =
                        MediaRouteSettingsAction(
                            label = "Open sound settings",
                            onClick = { openedSettings = true },
                        ),
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithText("Use Android's output switcher to route playback.").assertIsDisplayed()
        composeRule
            .onNodeWithTag(MediaDeviceSelectorTags.systemSettingsButton("Audio output"))
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()

        assertEquals(true, openedSettings)
    }

    @Test
    fun systemControlledOutputDefaultsToPlatformOutputSwitcherAction() {
        val selection =
            MediaDeviceSelectionUiState(
                kind = MediaDeviceKind.AUDIO_OUTPUT,
                devices =
                    listOf(
                        MediaDeviceUiState(
                            id = "speaker",
                            label = "Phone speaker",
                            kind = MediaDeviceKind.AUDIO_OUTPUT,
                            category = MediaDeviceCategory.BUILT_IN,
                        ),
                    ),
                selectedDeviceId = "speaker",
                isSelectionControllable = false,
                routeControlMessage = "Use Android's output switcher to route playback.",
            )

        composeRule.setContent {
            LogDateTheme {
                MediaDeviceSelectorSheet(
                    selection = selection,
                    title = "Audio output",
                    onDeviceSelected = {},
                    onDismiss = {},
                )
            }
        }

        composeRule
            .onNodeWithTag(MediaDeviceSelectorTags.systemSettingsButton("Audio output"))
            .assertIsDisplayed()
            .assertHasClickAction()
        composeRule.onNodeWithText("Open output switcher").assertIsDisplayed()
    }
}
