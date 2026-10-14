package app.logdate.ui.audio

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import app.logdate.client.media.device.MediaDeviceKind
import app.logdate.client.media.device.systemControlledSelection
import app.logdate.ui.media.MediaDeviceSelector
import app.logdate.ui.media.MediaDeviceSelectorTags
import app.logdate.ui.theme.LogDateTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class PlaybackHandoffTest {
    @Test
    fun handoffIsDirectAndDoesNotSelectAnAudioRoute() =
        runDesktopComposeUiTest {
            var transfers = 0
            var routeChanges = 0
            setContent {
                LogDateTheme {
                    Column {
                        MediaDeviceSelector(
                            selection = systemControlledSelection(MediaDeviceKind.AUDIO_OUTPUT),
                            onDeviceSelected = { routeChanges++ },
                            systemSettingsAction = null,
                        )
                        PlaybackHandoffAction(PlaybackHandoffState(available = true, onContinue = { transfers++ }))
                    }
                }
            }
            onNodeWithTag(MediaDeviceSelectorTags.chip("Audio output")).assertIsDisplayed()
            onNodeWithText("Continue on watch").performClick()
            assertEquals(1, transfers)
            assertEquals(0, routeChanges)
        }

    @Test
    fun coldWatchFailureExplainsRecoveryWithoutClaimingPlaybackMoved() =
        runDesktopComposeUiTest {
            setContent {
                LogDateTheme {
                    PlaybackHandoffAction(
                        PlaybackHandoffState(available = true, failed = true, failure = PlaybackHandoffFailure.WATCH_STARTING),
                    )
                }
            }
            onNodeWithText(
                "Your watch is still starting LogDate. Open LogDate on your watch, then try again. Playback is still on your phone.",
            ).assertIsDisplayed()
            onNodeWithText("Try again").assertIsDisplayed()
        }

    @Test
    fun failedTransferKeepsRecoveryBesideTheCurrentPlaybackTask() =
        runDesktopComposeUiTest {
            setContent {
                LogDateTheme {
                    PlaybackHandoffAction(PlaybackHandoffState(available = true, failed = true))
                }
            }
            onNodeWithText("Playback is still on your phone. Check your watch connection and try again.").assertIsDisplayed()
            onNodeWithText("Try again").assertIsDisplayed()
        }
}
