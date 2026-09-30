package app.logdate.feature.editor.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.logdate.feature.editor.ui.common.NoteEditorToolbar
import app.logdate.feature.editor.ui.content.EditorBottomContent
import app.logdate.feature.editor.ui.content.EmptyEditorStateContent
import app.logdate.feature.editor.ui.editor.TextBlockUiState
import app.logdate.feature.editor.ui.layout.ImmersiveEditorLayout
import app.logdate.feature.editor.ui.layout.LocalEditorIsCompact
import app.logdate.feature.editor.ui.state.BlocksUiState
import app.logdate.ui.theme.LogDateTheme
import org.jetbrains.skia.Image
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ResponsiveEditorLayoutTest {
    @Test
    fun `empty editor renders its existing controls at unfolded width`() =
        runDesktopComposeUiTest(width = 840, height = 720) {
            setContent {
                LogDateTheme(dynamicColor = false, darkTheme = false) {
                    ImmersiveEditorLayout(
                        topBarContent = { NoteEditorToolbar({}, {}, {}) },
                        editorContent = { EmptyEditorStateContent({}, {}, {}, {}) },
                        bottomContent = { EditorBottomContent(emptyList(), emptyList(), {}, false, {}) },
                    )
                }
            }
            onNodeWithText("Write something").assertExists()
            onNodeWithText("Gallery").assertExists()
            val output = File("build/reports/editor-layout/unfolded.png")
            output.parentFile.mkdirs()
            output.writeBytes(Image.makeFromBitmap(onRoot().captureToImage().asSkiaBitmap()).encodeToData()!!.bytes)
        }

    @Test
    fun `wide editor respects its readable content width`() =
        runDesktopComposeUiTest(width = 1600, height = 900) {
            setContent {
                ImmersiveEditorLayout(
                    topBarContent = {},
                    editorContent = { Box(Modifier.fillMaxSize().testTag("editor")) },
                    bottomContent = {},
                )
            }
            val bounds = onNodeWithTag("editor").getBoundsInRoot()
            assertTrue((bounds.right - bounds.left) <= 1200.dp, "Editor stretched to ${(bounds.right - bounds.left)}")
            assertTrue(bounds.left > 0.dp, "Editor should be centered")
        }

    @Test
    fun `short window leaves room for journal controls without overlapping editor`() =
        runDesktopComposeUiTest(width = 700, height = 280) {
            setContent {
                ImmersiveEditorLayout(
                    topBarContent = {},
                    editorContent = { Box(Modifier.fillMaxSize().testTag("editor")) },
                    bottomContent = { Box(Modifier.size(100.dp, 48.dp).testTag("journal")) },
                )
            }
            val editor = onNodeWithTag("editor").getBoundsInRoot()
            val journal = onNodeWithTag("journal").getBoundsInRoot()
            assertTrue(editor.bottom <= journal.top, "$editor overlaps $journal")
            assertTrue(journal.bottom <= 280.dp, "Journal controls are outside the window")
        }

    @Test
    fun `narrow picker pane keeps tiles readable despite compact parent`() =
        runDesktopComposeUiTest(width = 360, height = 400) {
            setContent {
                MaterialTheme {
                    CompositionLocalProvider(LocalEditorIsCompact provides true) {
                        EmptyEditorStateContent({}, {}, {}, {}, modifier = Modifier.fillMaxSize())
                    }
                }
            }
            val audio = onNodeWithTag("editor_start_audio_block").getBoundsInRoot()
            val gallery = onNodeWithText("Gallery").getBoundsInRoot()
            assertTrue((audio.right - audio.left) >= 150.dp, "Four tiles were squeezed into a narrow pane: $audio")
            assertTrue(gallery.top > audio.bottom, "Gallery should be on the second row")
        }

    @Test
    fun `large text in a short narrow pane keeps gallery reachable`() =
        runDesktopComposeUiTest(width = 360, height = 260) {
            var photosAdded = 0
            setContent {
                MaterialTheme {
                    CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                        EmptyEditorStateContent(
                            onStartTextBlock = {},
                            onStartPhotoBlock = { photosAdded++ },
                            onStartAudioBlock = {},
                            onStartCameraBlock = {},
                        )
                    }
                }
            }
            onNodeWithText("Gallery").performScrollTo().performClick()
            assertEquals(1, photosAdded)
        }

    @Test
    fun `large text uses the capped picker width when choosing columns`() =
        runDesktopComposeUiTest(width = 1400, height = 340) {
            setContent {
                MaterialTheme {
                    CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                        EmptyEditorStateContent({}, {}, {}, {})
                    }
                }
            }
            val audio = onNodeWithTag("editor_start_audio_block").getBoundsInRoot()
            assertTrue(audio.right - audio.left >= 300.dp, "Large labels were squeezed into four columns")
        }

    @Test
    fun `entry keeps adding available and preserves text selection through resizing`() =
        runDesktopComposeUiTest(width = 1100, height = 800) {
            var block by mutableStateOf(TextBlockUiState(content = "A morning with Milo"))
            var width by mutableStateOf(1100.dp)
            setContent {
                MaterialTheme {
                    Box(Modifier.width(width)) {
                        MainEditorContent(
                            uiState =
                                BlocksUiState(
                                    blocks = listOf(block),
                                    availableJournals = emptyList(),
                                    selectedJournalIds = emptyList(),
                                    onBlockFocused = {},
                                    onJournalSelectionChanged = {},
                                    onUpdateBlock = { block = it as TextBlockUiState },
                                    onCreateBlock = { _, _ -> block },
                                    onDeleteBlock = {},
                                ),
                            shouldReturnToPickerOnBack = false,
                            onDismissExpanded = {},
                        )
                    }
                }
            }
            val draft = onNodeWithText("A morning with Milo").getBoundsInRoot()
            val add = onNodeWithTag("add_to_entry").getBoundsInRoot()
            assertTrue(add.top > draft.bottom, "Add action should stay below the scrolling entry")
            onNodeWithText("A morning with Milo").performTextReplacement("Milo's first camping trip")
            onNodeWithText("Milo's first camping trip").performTextInputSelection(TextRange(2, 6))
            runOnIdle { width = 380.dp }
            assertEquals(
                TextRange(2, 6),
                onNodeWithText("Milo's first camping trip").fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange],
            )
            onNodeWithTag("add_to_entry").assertExists()
            runOnIdle { width = 1100.dp }
            assertEquals(
                TextRange(2, 6),
                onNodeWithText("Milo's first camping trip").fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange],
            )
            onNodeWithTag("add_to_entry").assertExists()
        }
}
