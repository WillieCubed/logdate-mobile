package app.logdate.feature.editor.ui.audio

import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import app.logdate.ui.theme.LogDateTheme
import kotlinx.coroutines.CompletableDeferred
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ValidatedAudioContentTest {
    @Test
    fun unresolvedRecordingShowsLoadingAndKnownDurationWithoutPlayback() =
        runDesktopComposeUiTest {
            val gate = CompletableDeferred<Long?>()
            setContent {
                LogDateTheme {
                    ValidatedAudioContent("retained.m4a", 8_000, { gate.await() }) { Text("Playable $it") }
                }
            }
            onNodeWithText("Loading recording…").assertIsDisplayed()
            onNodeWithText("00:08").assertIsDisplayed()
            onNodeWithText("Playable 8000").assertDoesNotExist()
            gate.complete(8_000)
            waitUntil { onNodeWithText("Playable 8000").isDisplayed() }
            onNodeWithText("Playable 8000").assertIsDisplayed()
        }

    @Test
    fun unavailableRecordingRetainsMetadataAndRetriesSameFileWithoutPlayback() =
        runDesktopComposeUiTest {
            val requested = mutableListOf<String>()
            setContent {
                LogDateTheme {
                    ValidatedAudioContent("retained.m4a", 8_000, {
                        requested += it
                        if (requested.size == 1) null else 8_000
                    }) { Text("Playable $it") }
                }
            }
            waitUntil { onNodeWithTag("audio_preview_retry").isDisplayed() }
            onNodeWithText("Recording unavailable").assertIsDisplayed()
            onNodeWithText("00:08").assertIsDisplayed()
            onNodeWithText("Playable 8000").assertDoesNotExist()
            onNodeWithTag("audio_preview_retry").performClick()
            waitUntil { onNodeWithText("Playable 8000").isDisplayed() }
            assertEquals(listOf("retained.m4a", "retained.m4a"), requested)
            onNodeWithText("Audio").assertDoesNotExist()
        }

    @Test
    fun availabilityAndMetadataChangesKeepTheSameContainerAndDoNotReloadOnDurationTicks() =
        runDesktopComposeUiTest {
            val uri = mutableStateOf("first.m4a")
            val duration = mutableStateOf(1_000L)
            val first = CompletableDeferred<Long?>()
            val requested = mutableListOf<String>()
            setContent {
                LogDateTheme {
                    ValidatedAudioContent(uri.value, duration.value, {
                        requested += it
                        if (it == "first.m4a") first.await() else null
                    }) { Text("Playable $it") }
                }
            }
            val identity = onNodeWithTag("audio_preview_container").fetchSemanticsNode().id
            runOnIdle { duration.value = 2_000 }
            assertEquals(identity, onNodeWithTag("audio_preview_container").fetchSemanticsNode().id)
            first.complete(2_000)
            waitUntil { onNodeWithText("Playable 2000").isDisplayed() }
            assertEquals(identity, onNodeWithTag("audio_preview_container").fetchSemanticsNode().id)
            runOnIdle { duration.value = 3_000 }
            assertEquals(listOf("first.m4a"), requested)
            runOnIdle { uri.value = "missing.m4a" }
            waitUntil { onNodeWithTag("audio_preview_retry").isDisplayed() }
            onNodeWithText("Playable 2000").assertDoesNotExist()
            assertEquals(identity, onNodeWithTag("audio_preview_container").fetchSemanticsNode().id)
        }
}
