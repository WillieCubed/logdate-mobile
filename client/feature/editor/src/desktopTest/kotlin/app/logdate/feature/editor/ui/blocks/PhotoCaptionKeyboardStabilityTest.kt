package app.logdate.feature.editor.ui.blocks

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.dp
import app.logdate.feature.editor.ui.MainEditorContent
import app.logdate.feature.editor.ui.editor.ImageBlockUiState
import app.logdate.feature.editor.ui.layout.LocalEditorKeyboardVisible
import app.logdate.feature.editor.ui.state.BlocksUiState
import app.logdate.ui.theme.LogDateTheme
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class PhotoCaptionKeyboardStabilityTest {
    @Test
    fun `editing a portrait photo caption keeps the photo width when the keyboard reduces the viewport`() {
        val imageFile = File.createTempFile("logdate-portrait-caption-", ".png")
        try {
            val image = BufferedImage(32, 64, BufferedImage.TYPE_INT_RGB)
            for (y in 0 until image.height) {
                for (x in 0 until image.width) {
                    image.setRGB(x, y, if (y < image.height / 2) 0x4477AA else 0x99CC77)
                }
            }
            check(ImageIO.write(image, "png", imageFile))

            runSkikoComposeUiTest(size = Size(390f, 780f)) {
                var height by mutableStateOf(780.dp)
                var keyboardVisible by mutableStateOf(false)
                var block by mutableStateOf(
                    ImageBlockUiState(uri = imageFile.toURI().toString(), caption = "Portrait fixture"),
                )
                setContent {
                    LogDateTheme(dynamicColor = false) {
                        CompositionLocalProvider(LocalEditorKeyboardVisible provides keyboardVisible) {
                            Box(Modifier.fillMaxSize()) {
                                Box(Modifier.requiredHeight(height).testTag("photo_caption_viewport")) {
                                    MainEditorContent(
                                        uiState =
                                            BlocksUiState(
                                                blocks = listOf(block),
                                                expandedBlockId = block.id,
                                                availableJournals = emptyList(),
                                                selectedJournalIds = emptyList(),
                                                onBlockFocused = {},
                                                onJournalSelectionChanged = {},
                                                onUpdateBlock = { block = it as ImageBlockUiState },
                                                onCreateBlock = { _, _ -> block },
                                                onDeleteBlock = {},
                                            ),
                                        shouldReturnToPickerOnBack = false,
                                        onDismissExpanded = {},
                                    )
                                }
                            }
                        }
                    }
                }
                waitUntil(timeoutMillis = 10_000) {
                    val photo = onNodeWithContentDescription("Portrait fixture").getUnclippedBoundsInRoot()
                    photo.bottom - photo.top > (photo.right - photo.left) * 1.9f
                }
                waitForIdle()
                val before = onNodeWithTag("memory_block_${block.id}").getUnclippedBoundsInRoot()
                onNodeWithTag("memory_caption_${block.id}").performClick()
                runOnIdle {
                    keyboardVisible = true
                    height = 460.dp
                }
                waitForIdle()
                val after = onNodeWithTag("memory_block_${block.id}").getUnclippedBoundsInRoot()
                val viewport = onNodeWithTag("photo_caption_viewport").getUnclippedBoundsInRoot()
                assertEquals(460.dp, viewport.bottom - viewport.top)
                assertEquals(
                    before.right - before.left,
                    after.right - after.left,
                    "Opening the caption keyboard must not shrink the photo or shift later memories",
                )
                assertEquals(before.bottom - before.top, after.bottom - after.top)
            }
        } finally {
            imageFile.delete()
        }
    }
}
