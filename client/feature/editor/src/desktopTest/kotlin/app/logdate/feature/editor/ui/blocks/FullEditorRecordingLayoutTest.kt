package app.logdate.feature.editor.ui.blocks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.logdate.client.media.device.DefaultMediaDevices
import app.logdate.client.media.device.MediaDeviceCategory
import app.logdate.client.media.device.MediaDeviceKind
import app.logdate.client.media.device.MediaDeviceSelectionUiState
import app.logdate.client.media.device.MediaDeviceUiState
import app.logdate.feature.editor.ui.audio.ActiveRecordingDisplay
import app.logdate.feature.editor.ui.common.LOGDATE_EDITOR_DRAFTS_BUTTON_TAG
import app.logdate.feature.editor.ui.common.LOGDATE_EDITOR_SAVE_BUTTON_TAG
import app.logdate.feature.editor.ui.common.NoteEditorToolbar
import app.logdate.feature.editor.ui.content.EditorBottomContent
import app.logdate.feature.editor.ui.editor.AudioBlockUiState
import app.logdate.feature.editor.ui.editor.AudioCaptureState
import app.logdate.feature.editor.ui.editor.AutoSaveStatus
import app.logdate.feature.editor.ui.editor.EditorState
import app.logdate.feature.editor.ui.layout.EditorColumnMaxWidth
import app.logdate.feature.editor.ui.layout.FocusedEntryPanes
import app.logdate.feature.editor.ui.layout.ImmersiveEditorLayout
import app.logdate.feature.editor.ui.layout.LocalEditorFocusPresentation
import app.logdate.shared.model.Journal
import app.logdate.ui.media.MediaDeviceSelectorTags
import app.logdate.ui.platform.DefaultScreenCornerRadius
import app.logdate.ui.theme.LogDateTheme
import org.jetbrains.skia.Image
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

/** Production editor chrome and block layout with synthetic recording data, without a microphone. */
@OptIn(ExperimentalTestApi::class)
class FullEditorRecordingLayoutTest {
    @Test
    fun `portrait recording preserves editor context in light theme`() = verifyLayout(Size(390f, 780f), false)

    @Test
    fun `portrait recording preserves editor context in dark theme`() = verifyLayout(Size(390f, 780f), true)

    @Test
    fun `failed transcription promotes audio in light theme`() = verifyLayout(Size(390f, 780f), false, transcriptionError = true)

    @Test
    fun `failed transcription promotes audio in dark theme`() = verifyLayout(Size(390f, 780f), true, transcriptionError = true)

    @Test
    fun `failed transcription keeps paused controls on a short screen`() =
        verifyLayout(Size(390f, 640f), false, paused = true, transcriptionError = true)

    @Test
    fun `portrait recording shows switcher when another microphone is available`() =
        verifyLayout(Size(390f, 780f), false, alternativeInput = true)

    @Test
    fun `short portrait recording preserves editor context and transport`() = verifyLayout(Size(390f, 640f), false)

    @Test
    fun `wide recording retains context controls`() = verifyLayout(Size(700f, 780f), false)

    @Test
    fun `tablet recording and journal selector share the entry column`() = verifyLayout(Size(1280f, 800f), false)

    @Test
    fun `paused mobile recording remains focused`() = verifyLayout(Size(390f, 780f), false, paused = true)

    @Test
    fun `finalizing mobile recording remains focused`() = verifyLayout(Size(390f, 780f), false, captureState = AudioCaptureState.Stopping())

    @Test
    fun `mobile toolbar and entry surfaces share the same visual gutter`() = verifyLayout(Size(390f, 780f), false, verifyGutters = true)

    @Test
    fun `recording content and transport share the same inner gutter`() = verifyLayout(Size(390f, 780f), false, verifyInnerGutters = true)

    @Test
    fun `card and recording controls share the device corner center in light theme`() =
        verifyLayout(Size(390f, 780f), false, screenCornerRadius = 48.dp)

    @Test
    fun `card and recording controls share the device corner center in dark theme`() =
        verifyLayout(Size(390f, 780f), true, screenCornerRadius = 48.dp)

    private fun verifyLayout(
        size: Size,
        darkTheme: Boolean,
        paused: Boolean = false,
        captureState: AudioCaptureState = AudioCaptureState.Recording(),
        verifyGutters: Boolean = false,
        verifyInnerGutters: Boolean = false,
        alternativeInput: Boolean = false,
        transcriptionError: Boolean = false,
        screenCornerRadius: Dp? = null,
    ) = runSkikoComposeUiTest(size = size) {
        val block = AudioBlockUiState(captureState = captureState, caption = "Retained caption")
        val journal = Journal(title = "Personal")
        val microphone =
            if (alternativeInput) {
                DefaultMediaDevices.systemMicrophone.copy(label = "Phone microphone", category = MediaDeviceCategory.BUILT_IN)
            } else {
                DefaultMediaDevices.systemMicrophone
            }
        val microphones =
            if (alternativeInput) {
                listOf(
                    microphone,
                    MediaDeviceUiState("usb", "USB microphone", MediaDeviceKind.AUDIO_INPUT, MediaDeviceCategory.USB),
                )
            } else {
                listOf(microphone)
            }
        setContent {
            LogDateTheme(dynamicColor = false, darkTheme = darkTheme) {
                ImmersiveEditorLayout(
                    screenCornerRadius = screenCornerRadius ?: DefaultScreenCornerRadius,
                    modifier = if (screenCornerRadius != null) Modifier.clip(RoundedCornerShape(screenCornerRadius)) else Modifier,
                    isAudioRecordingActive = EditorState(blocks = listOf(block)).isRecordingAudio,
                    topBarContent = {
                        NoteEditorToolbar(
                            {},
                            {},
                            {},
                            modifier = Modifier.testTag("editor_toolbar"),
                            draftCount = 1,
                            autoSaveStatus = AutoSaveStatus.SAVING,
                            optionsVisible = false,
                            saveEnabled = false,
                        )
                    },
                    bottomContent = {
                        EditorBottomContent(
                            listOf(journal),
                            listOf(journal.id),
                            {},
                            false,
                            {},
                            modifier = Modifier.fillMaxWidth().testTag("journal_surface_bounds"),
                            enabled = false,
                        )
                    },
                    editorContent = {
                        FocusedEntryPanes(focusedContent = {
                            BoxWithConstraints(
                                Modifier.fillMaxSize().wrapContentSize(Alignment.TopCenter).widthIn(max = EditorColumnMaxWidth),
                            ) {
                                val contextVisible = LocalEditorFocusPresentation.current.entryActionsEnabled
                                val recordingHeight = unfinishedAudioHeight(maxHeight, maxWidth, if (contextVisible) 56.dp else 0.dp)
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize().testTag("recording_entry"),
                                    contentPadding = PaddingValues(start = 8.dp, top = 8.dp, end = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp),
                                ) {
                                    item {
                                        MemoryBlockSurface(block, true, {}, {}, {}) {
                                            Column {
                                                ActiveRecordingDisplay(
                                                    isPaused = paused,
                                                    transcriptionHasError = transcriptionError,
                                                    audioLevels =
                                                        listOf(
                                                            .05f,
                                                            .08f,
                                                            .04f,
                                                            .12f,
                                                            .29f,
                                                            .54f,
                                                            .78f,
                                                            .9f,
                                                            .71f,
                                                            .42f,
                                                            .21f,
                                                            .08f,
                                                            .04f,
                                                            .05f,
                                                            .18f,
                                                            .38f,
                                                            .6f,
                                                            .84f,
                                                            .68f,
                                                            .39f,
                                                            .24f,
                                                            .1f,
                                                            .05f,
                                                            .04f,
                                                            .09f,
                                                            .22f,
                                                            .49f,
                                                            .7f,
                                                            .94f,
                                                            .85f,
                                                            .61f,
                                                            .4f,
                                                            .23f,
                                                            .1f,
                                                            .04f,
                                                            .03f,
                                                            .05f,
                                                            .12f,
                                                            .27f,
                                                            .46f,
                                                            .63f,
                                                            .78f,
                                                            .59f,
                                                            .35f,
                                                            .18f,
                                                            .09f,
                                                            .04f,
                                                            .03f,
                                                            .04f,
                                                            .02f,
                                                        ),
                                                    recordingDuration = 42.seconds,
                                                    onRestart = {},
                                                    onPause = {},
                                                    onFinish = {},
                                                    transcriptionText =
                                                        "I took the long way home today.\n\n" +
                                                            "The light was coming through the trees, " +
                                                            "and for once I wasn't in a hurry.\n\n" +
                                                            "I want to remember how that felt.",
                                                    inputSelection =
                                                        MediaDeviceSelectionUiState(
                                                            MediaDeviceKind.AUDIO_INPUT,
                                                            microphones,
                                                            microphone.id,
                                                        ),
                                                    modifier = Modifier.height(recordingHeight),
                                                )
                                                MemoryCaptionField(block, {}, {}, editRequest = 0)
                                            }
                                        }
                                    }
                                    if (contextVisible) item { EndOfEntryAddControl(0f, false, {}, {}, {}) }
                                }
                            }
                        }, contextContent = { Text("Earlier entry content") })
                    },
                )
            }
        }
        waitForIdle()
        val directory = File(System.getProperty("logdate.review.screenshots", "/private/tmp/logdate-review-screenshots"))
        directory.mkdirs()
        val theme = if (darkTheme) "dark" else "light"
        File(
            directory,
            "recording-focused-${size.width.toInt()}x${size.height.toInt()}-$theme${if (transcriptionError) {
                if (paused) "-transcription-error-paused" else "-transcription-error"
            } else if (alternativeInput) {
                "-alternative-input"
            } else if (paused) {
                "-paused"
            } else if (captureState is AudioCaptureState.Stopping) {
                "-finishing"
            } else {
                ""
            }}${if (screenCornerRadius != null) "-device-corners" else ""}.png",
        ).writeBytes(
            requireNotNull(Image.makeFromBitmap(onRoot().captureToImage().asSkiaBitmap()).encodeToData()).bytes,
        )
        onNodeWithContentDescription("Back").assertIsDisplayed()
        onNodeWithTag(LOGDATE_EDITOR_SAVE_BUTTON_TAG).assertIsDisplayed()
        onNodeWithTag(LOGDATE_EDITOR_DRAFTS_BUTTON_TAG).assertDoesNotExist()
        onNodeWithContentDescription("More options").assertDoesNotExist()
        onNodeWithTag("block_menu_${block.id}").assertDoesNotExist()
        if (alternativeInput) {
            onNodeWithText("Phone microphone").assertIsDisplayed()
        } else {
            onNodeWithTag(MediaDeviceSelectorTags.chip("Microphone")).assertDoesNotExist()
        }
        if (transcriptionError) {
            onNodeWithText("I want to remember how that felt.").assertDoesNotExist()
            onNodeWithContentDescription("Show transcript").assertIsDisplayed()
        } else {
            onNodeWithText("I want to remember how that felt.").assertIsDisplayed()
        }
        if (paused) {
            onNodeWithContentDescription("Restart recording").assertIsDisplayed()
        } else {
            onNodeWithContentDescription("Restart recording").assertDoesNotExist()
        }
        onNodeWithContentDescription(if (paused) "Resume" else "Pause").assertIsDisplayed()
        onNodeWithText("Finish").assertIsDisplayed()
        if (size.width < 600f && !verifyGutters && !verifyInnerGutters) {
            onNodeWithTag("add_to_entry").assertDoesNotExist()
            onNodeWithText("Personal").assertDoesNotExist()
            val recording = onNodeWithTag("memory_block_${block.id}").getUnclippedBoundsInRoot()
            assertEquals(size.height.dp - 16.dp, recording.bottom, "Focused audio must preserve the safe bottom margin")
        } else if (!verifyGutters && !verifyInnerGutters) {
            onNodeWithTag("add_to_entry").assertDoesNotExist()
            onNodeWithText("Personal").assertIsDisplayed()
            val recording = onNodeWithTag("memory_block_${block.id}").getUnclippedBoundsInRoot()
            val journalSurface = onNodeWithTag("journal_surface_bounds").getUnclippedBoundsInRoot()
            assertEquals(recording.left, journalSurface.left)
            assertEquals(recording.right, journalSurface.right)
        }
        if (verifyGutters) {
            val recording = onNodeWithTag("memory_block_${block.id}").getUnclippedBoundsInRoot()
            val back = onNodeWithContentDescription("Back").getUnclippedBoundsInRoot()
            val save = onNodeWithTag(LOGDATE_EDITOR_SAVE_BUTTON_TAG).getUnclippedBoundsInRoot()
            assertEquals(16.dp, back.top)
            assertEquals(16.dp, recording.top - back.bottom)
            assertEquals(recording.left, back.left, "Back's visible tonal surface must line up with the entry gutter")
            assertEquals(recording.right, save.right, "Save's visible tonal surface must line up with the entry gutter")
        }
        if (verifyInnerGutters) {
            val transcript = onNodeWithText("I want to remember how that felt.", useUnmergedTree = true).getUnclippedBoundsInRoot()
            val surface = onNodeWithTag("recording_transcript_surface", useUnmergedTree = true).getUnclippedBoundsInRoot()
            val pause = onNodeWithContentDescription(if (paused) "Resume" else "Pause").getUnclippedBoundsInRoot()
            assertEquals(transcript.left, pause.left, "Transcript text and transport share the same inner gutter")
            assertEquals(surface.left + 16.dp, transcript.left, "Transcript text has consistent padding within its container")
        }
        if (screenCornerRadius != null) {
            val card = onNodeWithTag("memory_block_${block.id}").getUnclippedBoundsInRoot()
            val pause = onNodeWithContentDescription("Pause").getUnclippedBoundsInRoot()
            val finish = onNodeWithText("Finish").getUnclippedBoundsInRoot()
            assertEquals(48.dp, card.left + 32.dp, "The card and display share the lower left corner center")
            assertEquals(size.height.dp - 48.dp, card.bottom - 32.dp)
            assertEquals(48.dp, pause.left + 16.dp, "The nested rounded square shares that center")
            assertEquals(size.height.dp - 48.dp, pause.bottom - 16.dp)
            assertEquals(size.width.dp - 48.dp, finish.right - 16.dp)
        }
        onNodeWithTag("memory_caption_${block.id}").assertDoesNotExist()
    }
}
