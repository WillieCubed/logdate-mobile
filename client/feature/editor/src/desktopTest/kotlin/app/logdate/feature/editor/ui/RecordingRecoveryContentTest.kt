package app.logdate.feature.editor.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import app.logdate.feature.editor.ui.audio.RecordingRecoveryContent
import app.logdate.feature.editor.ui.editor.AudioCaptureState
import app.logdate.ui.theme.LogDateTheme
import logdate.client.feature.editor.generated.resources.Res
import logdate.client.feature.editor.generated.resources.editor_error_recording_not_recovered
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class RecordingRecoveryContentTest {
    @Test
    fun interruptedAudioOffersRecoveryWithoutOfferingToOverwriteTheTake() =
        runDesktopComposeUiTest(width = 411, height = 891) {
            var retries = 0
            setContent {
                LogDateTheme {
                    RecordingRecoveryContent(
                        AudioCaptureState.Failed(Res.string.editor_error_recording_not_recovered, "/interrupted.aac"),
                        onRetry = { retries++ },
                        recovering = false,
                    )
                }
            }
            onNodeWithTag("audio_recovery_retry").assertIsDisplayed().performClick()
            onNodeWithTag("audio_record_start_button").assertDoesNotExist()
            runOnIdle { assertEquals(1, retries) }
        }
}
