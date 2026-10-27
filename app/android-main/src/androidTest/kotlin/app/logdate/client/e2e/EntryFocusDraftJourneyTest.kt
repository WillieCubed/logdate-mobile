package app.logdate.client.e2e

import android.app.Application
import android.content.Intent
import android.graphics.Point
import android.graphics.PointF
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.activity.BackEventCompat
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.printToString
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import app.logdate.client.EditorActivity
import app.logdate.client.datastore.EntryStartMode
import app.logdate.client.datastore.LogdatePreferencesDataSource
import app.logdate.client.e2e.osintegration.Stylus
import app.logdate.client.ensureLogDateApplicationStarted
import app.logdate.client.repository.journals.EntryDraftRepository
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.koin.core.context.GlobalContext
import java.io.File
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

/** Blank production editor, real Android IME, and real draft storage. No entry fixtures. */
class EntryFocusDraftJourneyTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test(timeout = 240_000)
    fun typedTextAndDrawnInkSurviveDraftReopen() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish") { "This capture journey requires a Managed Device emulator" }
        ensureLogDateApplicationStarted(context.applicationContext as Application)
        val device = UiDevice.getInstance(instrumentation)
        val output =
            File(
                checkNotNull(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")),
                "entry-focus-draft",
            ).apply {
                mkdirs()
            }
        val previousIme = device.executeShellCommand("settings get secure show_ime_with_hard_keyboard").trim()
        device.executeShellCommand("settings put secure show_ime_with_hard_keyboard 1")
        val drafts = GlobalContext.get().get<EntryDraftRepository>()
        val preferences = GlobalContext.get().get<LogdatePreferencesDataSource>()
        val previousDefault = runBlocking { preferences.observeEntryStartMode().first() }
        val motionKeys = listOf("animator_duration_scale", "transition_animation_scale", "window_animation_scale")
        val previousMotion = motionKeys.associateWith { device.executeShellCommand("settings get global $it").trim() }
        val before =
            runBlocking {
                drafts
                    .getDrafts()
                    .first()
                    .map { it.id }
                    .toSet()
            }
        val text = "Took the long way home. The light was too good to miss."

        fun launch(entry: Uuid? = null) =
            ActivityScenario.launch<EditorActivity>(
                Intent(context, EditorActivity::class.java)
                    .putExtra("entry_id", entry?.toString()),
            )

        fun capture(name: String) {
            compose.waitForIdle()
            Thread.sleep(400)
            check(device.takeScreenshot(File(output, "$name.png")))
        }

        fun predictiveBack(
            scenario: ActivityScenario<EditorActivity>,
            name: String,
            commit: Boolean,
        ) {
            compose.mainClock.autoAdvance = false
            scenario.onActivity { activity ->
                activity.onBackPressedDispatcher.dispatchOnBackStarted(BackEventCompat(0f, 400f, 0f, BackEventCompat.EDGE_LEFT))
            }
            listOf(0.2f, 0.45f, 0.7f).forEach { progress ->
                scenario.onActivity { activity ->
                    activity.onBackPressedDispatcher.dispatchOnBackProgressed(
                        BackEventCompat(160f * progress, 400f, progress, BackEventCompat.EDGE_LEFT),
                    )
                }
                compose.mainClock.advanceTimeBy(16)
                Thread.sleep(80)
            }
            capture(name)
            scenario.onActivity { activity ->
                if (commit) {
                    activity.onBackPressedDispatcher.onBackPressed()
                } else {
                    activity.onBackPressedDispatcher.dispatchOnBackCancelled()
                }
            }
            compose.mainClock.autoAdvance = true
            compose.waitForIdle()
        }
        val (recordOutput, recordInput) = instrumentation.uiAutomation.executeShellCommandRw("sh")
        ParcelFileDescriptor.AutoCloseOutputStream(recordInput).bufferedWriter().use {
            it.write("screenrecord --time-limit 150 ${output.absolutePath}/journey.mp4 >/dev/null 2>&1 & echo $!\n")
        }
        val recordingPid = ParcelFileDescriptor.AutoCloseInputStream(recordOutput).bufferedReader().use { it.readText().trim().toInt() }
        try {
            runBlocking { preferences.setEntryStartMode(EntryStartMode.TEXT) }
            motionKeys.forEach { device.executeShellCommand("settings put global $it 1") }
            launch().use { scenario ->
                waitForTextEditor()
                capture("01-default-text-focus")
                compose.onNodeWithTag("editor_text_input").performClick()
                compose.onNodeWithTag("editor_text_input").performTextInput(text)
                waitForTextEditor()
                compose.onNodeWithTag("editor_text_input").performClick()
                waitForKeyboard(scenario)
                capture("02-text-keyboard")
                scenario.recreate()
                waitForTextEditor()
                compose.onNodeWithTag("editor_text_input").performClick()
                waitForKeyboard(scenario)
                compose.onNodeWithText(text).assertIsDisplayed()
                capture("02b-text-after-recreation")
                device.pressBack()
                waitForKeyboard(scenario, visible = false)
                predictiveBack(scenario, "02c-text-back-cancel", commit = false)
                compose.onNodeWithTag("entry_mode_focus").assertIsDisplayed()
                compose.onNodeWithText(text).assertIsDisplayed()
                predictiveBack(scenario, "02d-text-back-commit", commit = true)
                compose.onNodeWithTag("entry_mode_overview").assertIsDisplayed()
                compose.mainClock.autoAdvance = false
                compose.onNodeWithContentDescription("Handwrite").performClick()
                compose.mainClock.advanceTimeBy(64)
                predictiveBack(scenario, "02e-back-cancel-during-expansion", commit = false)
                compose.onNodeWithTag("entry_mode_focus").assertIsDisplayed()
                waitForKeyboard(scenario, visible = false)
                compose.waitUntil(15_000) {
                    compose.onAllNodes(hasTestTag("handwriting_canvas") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
                }
                val canvas = compose.onNodeWithTag("handwriting_canvas").fetchSemanticsNode().boundsInRoot
                val strokes =
                    Stylus.hello(
                        PointF(canvas.left + canvas.width * 0.15f, canvas.top + canvas.height * 0.35f),
                        minOf(canvas.width * 0.1f, canvas.height * 0.08f),
                    )
                check(strokes.flatten().all { it.x in canvas.left..canvas.right && it.y in canvas.top..canvas.bottom }) {
                    "Pen input must stay inside the visible canvas: $canvas"
                }
                Stylus().write(strokes)
                capture("03-handwriting-focus")
                predictiveBack(scenario, "03b-ink-back-cancel", commit = false)
                compose.onNodeWithTag("entry_mode_focus").assertIsDisplayed()
                predictiveBack(scenario, "03c-ink-back-commit", commit = true)
                capture("04-entry-overview")
                compose.onNodeWithContentDescription("Close").performClick()
                capture("04-save-draft-dialog")
                compose.onNodeWithTag("exit_dialog_save_draft").performClick()
                compose.waitUntil(15_000) { scenario.state == Lifecycle.State.DESTROYED }
            }
            val saved =
                runBlocking {
                    drafts
                        .getDrafts()
                        .first { entries ->
                            entries.any {
                                it.id !in before &&
                                    it.notes.any { n -> n is JournalNote.Text && n.content == text }
                            }
                        }.first {
                            it.id !in before &&
                                it.notes.any { n -> n is JournalNote.Text && n.content == text }
                        }
                }
            val originalInk = saved.notes.filterIsInstance<JournalNote.Handwriting>().single()
            assertEquals(6, originalInk.document.strokes.size, "Every injected pen stroke must be retained")
            launch().use { scenario ->
                waitForTextEditor()
                compose.onNodeWithContentDescription("Return to entry").performClick()
                compose.onNodeWithContentDescription("Entry options").performClick()
                compose.onNodeWithTag("entry_open_drafts").performClick()
                compose.waitUntil(15_000) { compose.onAllNodesWithTag("draft_item_${saved.id}").fetchSemanticsNodes().isNotEmpty() }
                capture("05-draft-list")
                compose.onNodeWithTag("draft_item_${saved.id}").performClick()
                compose.waitUntil(30_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithText(text).assertIsDisplayed()
                capture("05-reopened-draft")
                compose.onNodeWithTag("memory_block_${originalInk.uid}").performScrollTo().performClick()
                capture("06-reopened-ink")
                compose.onNodeWithContentDescription("Return to entry").performClick()
                val textFocusTag = "memory_block_${saved.notes.filterIsInstance<JournalNote.Text>().single().uid}"
                compose.onNodeWithTag(textFocusTag).performScrollTo().performClick()
                waitForTextEditor()
                compose.onNodeWithTag("editor_text_input").performClick()
                waitForKeyboard(scenario)
                capture("07-reopened-text-keyboard")
                val reopened = runBlocking { drafts.getDraft(saved.id).first().getOrThrow() }
                assertEquals(
                    originalInk.document,
                    reopened.notes
                        .filterIsInstance<JournalNote.Handwriting>()
                        .single()
                        .document,
                )
                compose.onNodeWithContentDescription("Return to entry").performClick()
                compose.onNodeWithContentDescription("Save entry").performClick()
                compose.waitUntil(15_000) { scenario.state == Lifecycle.State.DESTROYED }
            }
            val notes = GlobalContext.get().get<JournalNotesRepository>()
            runBlocking {
                notes.allNotesObserved.first { savedNotes -> savedNotes.any { it is JournalNote.Text && it.content == text } }
            }
            launch(entry = originalInk.uid).use {
                compose.waitUntil(30_000) {
                    compose.onAllNodesWithTag("memory_block_${originalInk.uid}").fetchSemanticsNodes().isNotEmpty()
                }
                capture("08-reopened-saved-entry")
                compose.onNodeWithTag("memory_block_${originalInk.uid}").performScrollTo().performClick()
                capture("09-saved-entry-ink")
                val backY = device.displayHeight / 2
                check(device.swipe(arrayOf(Point(1, backY), Point(device.displayWidth / 5, backY), Point(1, backY)), 40))
                compose.waitForIdle()
                compose.onNodeWithTag("handwriting_focus_surface").assertIsDisplayed()
                capture("09b-system-edge-back-cancel")
                check(device.swipe(1, backY, device.displayWidth / 2, backY, 60))
                compose.waitUntil(10_000) { compose.onAllNodesWithTag("entry_mode_overview").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithTag("entry_mode_overview").assertIsDisplayed()
                capture("09c-system-edge-back-commit")
                compose.onNodeWithTag("memory_block_${originalInk.uid}").performScrollTo().performClick()
                val persistedInk =
                    runBlocking { notes.allNotesObserved.first() }.filterIsInstance<JournalNote.Handwriting>().first {
                        it.uid ==
                            originalInk.uid
                    }
                assertEquals(originalInk.document, persistedInk.document)
            }
            launch(
                entry =
                    saved.notes
                        .filterIsInstance<JournalNote.Text>()
                        .single()
                        .uid,
            ).use { scenario ->
                compose.waitUntil(30_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
                compose
                    .onNodeWithTag(
                        "memory_block_${saved.notes.filterIsInstance<JournalNote.Text>().single().uid}",
                    ).performScrollTo()
                    .performClick()
                waitForTextEditor()
                compose.onNodeWithTag("editor_text_input").performClick()
                waitForKeyboard(scenario)
                capture("10-saved-text-keyboard")
            }
            launch().use { scenario ->
                val currentText = "Keep this writing through back gestures."
                waitForTextEditor()
                compose.onNodeWithTag("editor_text_input").performTextInput(currentText)
                compose.onNodeWithContentDescription("Return to entry").performClick()
                compose.waitUntil(15_000) {
                    compose.onAllNodesWithContentDescription("Browse earlier today").fetchSemanticsNodes().isNotEmpty()
                }
                compose.onNodeWithContentDescription("Browse earlier today").performClick()
                predictiveBack(scenario, "11-earlier-back-cancel", commit = false)
                compose.onNodeWithTag("entry_day_browser").assertIsDisplayed()
                predictiveBack(scenario, "12-earlier-back-commit", commit = true)
                compose.onNodeWithTag("entry_mode_overview").assertIsDisplayed()
                compose.onNodeWithText(currentText).performScrollTo().assertIsDisplayed()
                predictiveBack(scenario, "13-exit-back-cancel", commit = false)
                compose.onNodeWithTag("exit_dialog_save_draft").assertDoesNotExist()
                predictiveBack(scenario, "14-exit-back-commit", commit = true)
                compose.onNodeWithTag("exit_dialog_save_draft").assertIsDisplayed()
                compose.onNodeWithTag("exit_dialog_cancel").performClick()
                compose.onNodeWithText(currentText).assertIsDisplayed()
            }
        } finally {
            previousMotion.forEach { (key, value) ->
                device.executeShellCommand(if (value == "null") "settings delete global $key" else "settings put global $key $value")
            }
            runBlocking { preferences.setEntryStartMode(previousDefault) }
            device.executeShellCommand("kill -2 $recordingPid")
            Thread.sleep(700)
            device.executeShellCommand(
                if (previousIme ==
                    "null"
                ) {
                    "settings delete secure show_ime_with_hard_keyboard"
                } else {
                    "settings put secure show_ime_with_hard_keyboard $previousIme"
                },
            )
        }
    }

    private fun waitForTextEditor() {
        try {
            compose.waitUntil(15_000) { compose.onAllNodesWithTag("editor_text_input").fetchSemanticsNodes().isNotEmpty() }
            compose.waitForIdle()
            Thread.sleep(700)
        } catch (failure: Throwable) {
            throw AssertionError(compose.onRoot().printToString(), failure)
        }
    }

    private fun waitForKeyboard(
        scenario: ActivityScenario<EditorActivity>,
        visible: Boolean = true,
    ) {
        compose.waitUntil(10_000) {
            var shown = false
            scenario.onActivity {
                shown = ViewCompat.getRootWindowInsets(it.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == true
            }
            shown == visible
        }
    }
}
