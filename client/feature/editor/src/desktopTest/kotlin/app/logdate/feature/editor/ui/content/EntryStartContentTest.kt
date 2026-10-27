package app.logdate.feature.editor.ui.content

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import app.logdate.client.datastore.EntryStartMode
import app.logdate.client.repository.journals.JournalNote
import app.logdate.feature.editor.ui.editor.BlockType
import app.logdate.ui.theme.LogDateTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.uuid.Uuid

@OptIn(ExperimentalTestApi::class)
class EntryStartContentTest {
    @Test
    fun `saved default makes that capture action primary without creating a block`() {
        val modes =
            mapOf(
                EntryStartMode.HANDWRITING to BlockType.HANDWRITING,
                EntryStartMode.CAMERA to BlockType.CAMERA,
                EntryStartMode.AUDIO to BlockType.AUDIO,
                EntryStartMode.GALLERY to BlockType.IMAGE,
            )
        for ((mode, expected) in modes) {
            runDesktopComposeUiTest(width = 360, height = 800) {
                var created: BlockType? = null
                setContent {
                    LogDateTheme(dynamicColor = false) {
                        EntryStartContent(emptyList(), { type, _ -> created = type }, {}, defaultMode = mode)
                    }
                }
                assertNull(created)
                onNodeWithTag("entry_start_primary").assertIsDisplayed().performClick()
                assertEquals(expected, created)
            }
        }
    }

    @Test
    fun `writing is ready without choosing a mode or creating content`() =
        runDesktopComposeUiTest(width = 360, height = 800) {
            var created: BlockType? = null
            setContent {
                LogDateTheme(dynamicColor = false) {
                    EntryStartContent(emptyList(), { type, _ -> created = type }, {})
                }
            }
            assertNull(created)
            onNodeWithTag("entry_start_writing").assertIsDisplayed().performClick()
            assertEquals(BlockType.TEXT, created)
        }

    @Test
    fun `handwriting remains a secondary capture action`() =
        runDesktopComposeUiTest(width = 360, height = 800) {
            var created: BlockType? = null
            setContent {
                LogDateTheme(dynamicColor = false) {
                    EntryStartContent(emptyList(), { type, _ -> created = type }, {})
                }
            }
            onNodeWithTag("editor_start_handwriting_block").assertIsDisplayed().performClick()
            assertEquals(BlockType.HANDWRITING, created)
        }

    @Test
    fun `existing notes are visible context and only open when selected`() =
        runDesktopComposeUiTest(width = 840, height = 700) {
            val note =
                JournalNote.Text(
                    content = "Coffee outside before the day got busy.",
                    creationTimestamp = Clock.System.now(),
                    lastUpdated = Clock.System.now(),
                )
            var opened: Uuid? = null
            var created: BlockType? = null
            setContent {
                LogDateTheme(dynamicColor = false) {
                    EntryStartContent(listOf(note), { type, _ -> created = type }, { opened = it })
                }
            }
            onNodeWithText(note.content).assertIsDisplayed().performClick()
            assertEquals(note.uid, opened)
            assertNull(created)
            onNodeWithTag("entry_start_writing").assertIsDisplayed()
        }
}
