package app.logdate.feature.editor.ui.audio

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.unit.LayoutDirection
import app.logdate.client.media.audio.transcription.TimedTranscript
import app.logdate.client.media.audio.transcription.TimedUtterance
import app.logdate.feature.editor.ui.editor.AudioBlockUiState
import app.logdate.feature.editor.ui.editor.AudioCaptureState
import app.logdate.feature.editor.ui.editor.TextBlockUiState
import app.logdate.feature.editor.ui.text.TextBlockContent
import app.logdate.ui.theme.LogDateTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class MarkdownContentDirectionTest {
    @Test
    fun `expanded completed audio caption keeps inline formatting on one visual line`() =
        runSkikoComposeUiTest {
            setContent {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    LogDateTheme(dynamicColor = false) {
                        AudioBlockContent(
                            block =
                                AudioBlockUiState(
                                    captureState = AudioCaptureState.Ready("file:///speech.wav", 5000),
                                    caption = "**Quiet** streets",
                                ),
                            isExpanded = true,
                            isPlaying = false,
                            onPlayPauseClicked = {},
                            onDeleteClicked = {},
                            onSeekPositionChanged = {},
                            onSeekTimestampClicked = {},
                        )
                    }
                }
            }
            assertEquals(1, onNodeWithText("Quiet streets", useUnmergedTree = true).textLayout().lineCount)
        }

    @Test
    fun `source editor preserves English markers and Arabic direction in an RTL shell`() =
        runSkikoComposeUiTest {
            val source = "# $ENGLISH\n$ARABIC\nHello مرحبا."
            setContent {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    LogDateTheme(dynamicColor = false) {
                        TextBlockContent(TextBlockUiState(content = source), false, {}, {}, requestEditingFocus = false)
                    }
                }
            }
            val layout = onNodeWithTag("editor_text_input").textLayout()
            assertEquals(source, layout.layoutInput.text.text)
            assertEquals(ResolvedTextDirection.Ltr, layout.getParagraphDirection(0))
            assertEquals(ResolvedTextDirection.Rtl, layout.getBidiRunDirection(source.indexOf(ARABIC)))
            assertEquals(ResolvedTextDirection.Ltr, layout.getParagraphDirection(source.lastIndexOf("Hello")))
            assertEquals(3, layout.lineCount, "Source direction styling must preserve line count")
            assertTrue(layout.getBoundingBox(0).left < layout.getBoundingBox(2).left, "The heading marker precedes English source")
        }

    @Test
    fun `expanded completed transcript follows each utterance content direction`() = transcriptDirection(expanded = true)

    @Test
    fun `compact completed transcript preserves English and Arabic runs`() = transcriptDirection(expanded = false)

    @Test
    fun `Arabic source keeps its heading marker before its text`() =
        runSkikoComposeUiTest {
            val source = "# $ARABIC"
            setContent {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    LogDateTheme(dynamicColor = false) {
                        TextBlockContent(TextBlockUiState(content = source), false, {}, {}, requestEditingFocus = false)
                    }
                }
            }
            val layout = onNodeWithTag("editor_text_input").textLayout()
            assertEquals(source, layout.layoutInput.text.text)
            assertEquals(ResolvedTextDirection.Rtl, layout.getParagraphDirection(0))
            assertTrue(layout.getBoundingBox(0).left > layout.getBoundingBox(2).left)
        }

    @Test
    fun `Arabic compact transcript follows its content direction`() = transcriptDirection(expanded = false, arabicOnly = true)

    private fun transcriptDirection(
        expanded: Boolean,
        arabicOnly: Boolean = false,
    ) = runSkikoComposeUiTest {
        val transcript = if (arabicOnly) ARABIC else "$ENGLISH\n$ARABIC"
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                LogDateTheme(dynamicColor = false) {
                    AudioBlockContent(
                        block =
                            AudioBlockUiState(
                                captureState = AudioCaptureState.Ready("file:///speech.wav", 5000),
                                transcription = transcript,
                            ),
                        isExpanded = expanded,
                        isPlaying = false,
                        timedTranscript =
                            if (expanded) {
                                TimedTranscript(
                                    listOf(TimedUtterance(ENGLISH, 0, 2000), TimedUtterance(ARABIC, 2000, 5000)),
                                )
                            } else {
                                null
                            },
                        onPlayPauseClicked = {},
                        onDeleteClicked = {},
                        onSeekPositionChanged = {},
                        onSeekTimestampClicked = {},
                        showDeleteAction = false,
                    )
                }
            }
        }
        val english = onNodeWithText(if (expanded) ENGLISH else transcript, useUnmergedTree = true).textLayout()
        val arabic = if (expanded) onNodeWithText(ARABIC, useUnmergedTree = true).textLayout() else english
        val arabicOffset = if (expanded) 0 else transcript.indexOf(ARABIC)
        assertEquals(if (arabicOnly) ResolvedTextDirection.Rtl else ResolvedTextDirection.Ltr, english.getParagraphDirection(0))
        assertEquals(ResolvedTextDirection.Rtl, arabic.getBidiRunDirection(arabicOffset))
        if (expanded || arabicOnly) assertEquals(ResolvedTextDirection.Rtl, arabic.getParagraphDirection(arabicOffset))
        if (!arabicOnly) assertTrue(english.getBoundingBox(0).left < english.getBoundingBox(ENGLISH.lastIndex).left)
        assertTrue(arabic.getBoundingBox(arabicOffset).left > arabic.getBoundingBox(arabicOffset + ARABIC.lastIndex - 1).left)
        if (expanded || arabicOnly) {
            assertTrue(arabic.getBoundingBox(arabicOffset).left > arabic.getBoundingBox(arabicOffset + ARABIC.lastIndex).left)
        }
    }
}

private fun SemanticsNodeInteraction.textLayout(): TextLayoutResult {
    val layouts = mutableListOf<TextLayoutResult>()
    performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
    return layouts.single()
}

private const val ENGLISH = "Hello world."
private const val ARABIC = "مرحبا بالعالم."
