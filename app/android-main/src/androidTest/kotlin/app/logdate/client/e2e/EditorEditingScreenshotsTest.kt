package app.logdate.client.e2e

import android.content.Context
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.down
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.up
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import app.logdate.feature.editor.ui.MainEditorContent
import app.logdate.feature.editor.ui.common.NoteEditorToolbar
import app.logdate.feature.editor.ui.content.EditorBottomContent
import app.logdate.feature.editor.ui.editor.EntryBlockUiState
import app.logdate.feature.editor.ui.editor.ImageBlockUiState
import app.logdate.feature.editor.ui.editor.TextBlockUiState
import app.logdate.feature.editor.ui.layout.ImmersiveEditorLayout
import app.logdate.feature.editor.ui.state.BlocksUiState
import app.logdate.shared.model.Journal
import app.logdate.shared.model.PhotoPresentation
import app.logdate.ui.foldable.FoldableHingeBounds
import app.logdate.ui.foldable.FoldableHingeInfo
import app.logdate.ui.foldable.FoldableHingeOrientation
import app.logdate.ui.foldable.FoldableHingeState
import app.logdate.ui.foldable.FoldableLayoutInfo
import app.logdate.ui.foldable.FoldableOcclusionType
import app.logdate.ui.foldable.FoldablePosture
import app.logdate.ui.foldable.provideFoldableLayoutInfo
import app.logdate.ui.theme.LogDateTheme
import java.io.File
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid
import org.junit.Rule
import org.junit.Test

/** Emulator-only capture of the production editing components and the Android IME. */
class EditorEditingScreenshotsTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test(timeout = 180_000)
    fun editingWithKeyboardAndPhoto() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        val context = instrumentation.targetContext
        val oldImeSetting = device.executeShellCommand("settings get secure show_ime_with_hard_keyboard").trim()
        device.executeShellCommand("settings put secure show_ime_with_hard_keyboard 1")
        val photoFile = File(context.cacheDir, "editor-screenshot-photo.jpg")
        context.assets.open("sample_note_photo.jpg").use { source -> photoFile.outputStream().use { source.copyTo(it) } }
        val text = TextBlockUiState(content = "Milo's first camping trip\n\nWe found a quiet spot by the trees.")
        val photo = ImageBlockUiState(uri = photoFile.toURI().toString(), caption = "Our campsite")
        val journal = Journal(title = "Life with Milo")
        val blocks = mutableStateOf<List<EntryBlockUiState>>(listOf(text, photo))
        val expanded = mutableStateOf<Uuid?>(null)
        val fold = mutableStateOf(FoldableLayoutInfo())
        val listState = LazyListState()
        compose.runOnUiThread {
            compose.activity.enableEdgeToEdge()
            compose.activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            compose.activity.setContent {
                provideFoldableLayoutInfo(fold.value) {
                    LogDateTheme(dynamicColor = false, darkTheme = false) {
                        ImmersiveEditorLayout(
                            topBarContent = { NoteEditorToolbar({ expanded.value = null }, {}, {}) },
                            editorContent = {
                                MainEditorContent(
                                    uiState = BlocksUiState(
                                        blocks = blocks.value,
                                        expandedBlockId = expanded.value,
                                        availableJournals = listOf(journal),
                                        selectedJournalIds = listOf(journal.id),
                                        onBlockFocused = { expanded.value = it },
                                        onJournalSelectionChanged = {},
                                        onUpdateBlock = { updated ->
                                            blocks.value = blocks.value.map { if (it.id == updated.id) updated else it }
                                        },
                                        onCreateBlock = { _, id ->
                                            TextBlockUiState(id = id).also { blocks.value = blocks.value + it }
                                        },
                                        onDeleteBlock = { id -> blocks.value = blocks.value.filterNot { it.id == id } },
                                    ),
                                    shouldReturnToPickerOnBack = false,
                                    onDismissExpanded = { expanded.value = null },
                                    listState = listState,
                                )
                            },
                            bottomContent = {
                                EditorBottomContent(
                                    listOf(journal),
                                    listOf(journal.id),
                                    {},
                                    false,
                                    {},
                                    modifier = Modifier.testTag("editor_journal_selector"),
                                )
                            },
                        )
                    }
                }
            }
        }
        try {
            val wide = compose.activity.resources.configuration.screenWidthDp >= 640
            val variants = if (wide) listOf("unfolded", "book", "tabletop") else listOf("cover")
            for (variant in variants) {
                compose.runOnUiThread {
                    fold.value = layoutInfo(variant)
                    blocks.value = listOf(text, photo)
                    expanded.value = text.id
                }
                compose.waitForIdle()
                compose.onNodeWithTag("editor_block_list").performScrollToIndex(0)
                Thread.sleep(700)
                compose.onNodeWithTag("editor_text_input").performClick()
                compose.onNodeWithTag("editor_text_input").performTextInputSelection(TextRange(text.content.length))
                compose.onNodeWithTag("editor_text_input").performTextInput(" The air smelled like pine.")
                Thread.sleep(700)
                compose.onNodeWithTag("editor_text_input").performClick()
                waitForKeyboard(true)
                capture(device, "$variant-text-keyboard")
                hideKeyboard()
                compose.runOnUiThread { expanded.value = photo.id }
                compose.waitForIdle()
                compose.onNodeWithTag("editor_block_list").performScrollToIndex(1)
                capture(device, "$variant-photo")
                val photoBounds = compose.onNodeWithTag("memory_block_${photo.id}").getUnclippedBoundsInRoot()
                val listBounds = compose.onNodeWithTag("editor_block_list").getBoundsInRoot()
                assertTrue(photoBounds.bottom <= listBounds.bottom + 2.dp, "Selected photo is clipped: $photoBounds in $listBounds")
                compose.onNodeWithTag("block_menu_${photo.id}").performClick()
                compose.onNodeWithText("Framed").performClick()
                compose.waitForIdle()
                capture(device, "$variant-photo-framed")
                compose.onNodeWithTag("block_menu_${photo.id}").performClick()
                compose.onNodeWithText("Edge to edge").performClick()
                compose.waitForIdle()
                repeat(3) {
                    if (!compose.onNodeWithTag("memory_caption_${photo.id}").isDisplayed()) {
                        compose.onNodeWithTag("editor_block_list").performTouchInput { swipeUp() }
                    }
                }
                compose.onNodeWithTag("memory_caption_${photo.id}").performClick()
                waitForKeyboard(true)
                compose.waitForIdle()
                compose.onNodeWithTag("memory_caption_${photo.id}").performTextReplacement("Our campsite at sunset")
                Thread.sleep(700)
                assertEquals(
                    "Our campsite at sunset",
                    (blocks.value.first { it.id == photo.id } as ImageBlockUiState).caption,
                    compose.onRoot().printToString(),
                )
                compose.onNodeWithTag("memory_caption_${photo.id}").performClick()
                waitForKeyboard(true)
                capture(device, "$variant-photo-caption-keyboard")
                hideKeyboard()
                compose.runOnUiThread { expanded.value = null }
                compose.waitForIdle()
                capture(device, "$variant-draft")
                compose.runOnUiThread {
                    blocks.value = blocks.value.map {
                        if (it is ImageBlockUiState) it.copy(presentation = PhotoPresentation.Framed) else it
                    }
                }
                compose.waitForIdle()
                repeat(3) {
                    if (!compose.onNodeWithTag("memory_caption_${photo.id}").isDisplayed()) {
                        compose.onNodeWithTag("editor_block_list").performTouchInput { swipeUp() }
                        compose.waitForIdle()
                        Thread.sleep(700)
                    }
                }
                capture(device, "$variant-draft-framed")
                compose.onNodeWithTag("memory_caption_${photo.id}").assertIsDisplayed()
                compose.onNodeWithTag("editor_block_list").performScrollToIndex(blocks.value.size)
                compose.waitForIdle()
                capture(device, "$variant-add-rest")
                compose.onNodeWithTag("editor_block_list").performTouchInput { swipeUp(durationMillis = 650) }
                compose.waitUntil(4_000) { compose.onNodeWithTag("add_memory_CAMERA").isDisplayed() }
                compose.waitForIdle()
                listOf("TEXT", "IMAGE", "AUDIO", "VIDEO", "CAMERA").forEach { type ->
                    compose.onNodeWithTag("add_memory_$type").assertIsDisplayed()
                }
                val addBounds = compose.onNodeWithTag("add_memory_surface").getUnclippedBoundsInRoot()
                val framedBounds = compose.onNodeWithTag("memory_block_${photo.id}").getUnclippedBoundsInRoot()
                val expandedListBounds = compose.onNodeWithTag("editor_block_list").getBoundsInRoot()
                assertTrue(framedBounds.bottom <= addBounds.top + 2.dp, "Add block overlaps the photo: $framedBounds and $addBounds")
                assertTrue(abs(framedBounds.left.value - addBounds.left.value) <= 2f, "Add block and photo have different left edges")
                assertTrue(abs(framedBounds.right.value - addBounds.right.value) <= 2f, "Add block and photo have different right edges")
                val journalBounds = compose.onNodeWithTag("editor_journal_selector").getBoundsInRoot()
                assertTrue(abs(journalBounds.left.value - addBounds.left.value) <= 2f, "Journal selector and memory have different left edges: $journalBounds and $addBounds")
                assertTrue(abs(journalBounds.right.value - addBounds.right.value) <= 2f, "Journal selector and memory have different right edges: $journalBounds and $addBounds")
                val surfaceGap = journalBounds.top - addBounds.bottom
                assertTrue(surfaceGap in 6.dp..12.dp, "Add surface and journal selector need a small gap: $surfaceGap")
                capture(device, "$variant-pull-open")
                assertTrue(addBounds.bottom >= expandedListBounds.bottom - 4.dp, "Expanded Add block leaves extra dead space: $addBounds in $expandedListBounds")
                compose.onNodeWithTag("add_close").performClick()
                waitForCompactAdd()
                compose.onNodeWithTag("editor_block_list").performTouchInput {
                    down(Offset(center.x, center.y + 80f))
                    moveBy(Offset(0f, -80f))
                    up()
                }
                waitForCompactAdd()
                compose.onNodeWithTag("add_to_entry").performClick()
                waitForExpandedAdd(listState)
                capture(device, "$variant-add-menu")
                compose.onNodeWithTag("add_memory_surface").performTouchInput { swipeDown(durationMillis = 600) }
                waitForCompactAdd()
                compose.onNodeWithTag("add_to_entry").performClick()
                waitForExpandedAdd(listState)
                compose.onNodeWithTag("add_memory_TEXT").performClick()
                compose.waitForIdle()
                assertEquals(3, blocks.value.size, compose.onRoot().printToString())
                compose.onNodeWithTag("memory_block_${blocks.value.last().id}").assertIsDisplayed()
                waitForKeyboard(true)
                assertEquals(3, blocks.value.size)
                capture(device, "$variant-added-text-keyboard")
                hideKeyboard()
            }
        } finally {
            val command = if (oldImeSetting == "null") {
                "settings delete secure show_ime_with_hard_keyboard"
            } else {
                "settings put secure show_ime_with_hard_keyboard $oldImeSetting"
            }
            device.executeShellCommand(command)
        }
    }

    private fun waitForExpandedAdd(listState: LazyListState) {
        val ready = {
            val addBounds = compose.onNodeWithTag("add_memory_surface").getUnclippedBoundsInRoot()
            val journalBounds = compose.onNodeWithTag("editor_journal_selector").getBoundsInRoot()
            val listBounds = compose.onNodeWithTag("editor_block_list").getBoundsInRoot()
            val addWidth = addBounds.right - addBounds.left
            val journalWidth = journalBounds.right - journalBounds.left
            abs(addWidth.value - journalWidth.value) <= 2f && addBounds.bottom <= listBounds.bottom + 2.dp
        }
        try {
            compose.waitUntil(4_000) { ready() }
        } catch (e: Throwable) {
            val layout = listState.layoutInfo
            val footer = layout.visibleItemsInfo.firstOrNull { it.key == "add_memory_footer" }
            val addBounds = compose.onNodeWithTag("add_memory_surface").getUnclippedBoundsInRoot()
            val listBounds = compose.onNodeWithTag("editor_block_list").getBoundsInRoot()
            throw AssertionError("Expanded Add clipped: footer=$footer viewportEnd=${layout.viewportEndOffset} canScrollForward=${listState.canScrollForward} surface=$addBounds list=$listBounds", e)
        }
    }

    private fun waitForCompactAdd() {
        compose.waitUntil(4_000) {
            val bounds = compose.onNodeWithTag("add_memory_surface").getBoundsInRoot()
            bounds.right - bounds.left <= 182.dp
        }
    }

    private fun layoutInfo(variant: String): FoldableLayoutInfo {
        if (variant != "book" && variant != "tabletop") return FoldableLayoutInfo()
        val metrics = compose.activity.resources.displayMetrics
        val width = metrics.widthPixels / metrics.density
        val height = metrics.heightPixels / metrics.density
        val vertical = variant == "book"
        val left = if (vertical) width / 2 else 0f
        val top = if (vertical) 0f else height / 2
        val right = if (vertical) left else width
        val bottom = if (vertical) height else top
        return FoldableLayoutInfo(
            isFoldable = true,
            posture = if (vertical) FoldablePosture.Book else FoldablePosture.Tabletop,
            hinge = FoldableHingeInfo(
                orientation = if (vertical) FoldableHingeOrientation.Vertical else FoldableHingeOrientation.Horizontal,
                state = FoldableHingeState.HalfOpened,
                occlusionType = FoldableOcclusionType.None,
                bounds = FoldableHingeBounds(left.dp, top.dp, right.dp, bottom.dp, (right - left).dp, (bottom - top).dp),
                isSeparating = true,
            ),
        )
    }

    private fun hideKeyboard() {
        compose.runOnUiThread {
            val activity = compose.activity
            (activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .hideSoftInputFromWindow(activity.window.decorView.windowToken, 0)
            activity.currentFocus?.clearFocus()
        }
        waitForKeyboard(false)
    }

    private fun waitForKeyboard(visible: Boolean) {
        compose.waitUntil(10_000) {
            var shown = false
            compose.runOnUiThread {
                shown = ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
                    ?.isVisible(WindowInsetsCompat.Type.ime()) == true
            }
            shown == visible
        }
        compose.waitForIdle()
    }

    private fun capture(device: UiDevice, name: String) {
        // Wait for system IME animation and local photo decoding, beyond Compose's idle state.
        Thread.sleep(700)
        if (name.endsWith("keyboard")) waitForKeyboard(true)
        val arguments = InstrumentationRegistry.getArguments()
        val outputDir = arguments.getString("additionalTestOutputDir")
            ?: error("Run this capture using a Gradle Managed Device with additionalTestOutputDir")
        val output = File(outputDir, "$name.png")
        output.parentFile?.mkdirs()
        assertTrue(device.takeScreenshot(output), "Screenshot failed: $name")
    }
}
