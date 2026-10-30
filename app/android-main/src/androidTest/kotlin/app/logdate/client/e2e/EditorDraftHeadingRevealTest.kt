package app.logdate.client.e2e

import android.app.Application
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Rect
import android.os.Build
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import app.logdate.client.MainActivity
import app.logdate.client.ambient.AMBIENT_PROMPT_TARGET_DRAFT
import app.logdate.client.ambient.EXTRA_AMBIENT_PROMPT_DRAFT_ID
import app.logdate.client.ambient.EXTRA_AMBIENT_PROMPT_TARGET
import app.logdate.client.ensureLogDateApplicationStarted
import app.logdate.client.media.audio.AudioRecordingManager
import app.logdate.client.repository.journals.EntryDraft
import app.logdate.client.repository.journals.EntryDraftRepository
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.testing.onboarding.DEBUG_SKIP_ONBOARDING_EXTRA
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.koin.core.context.GlobalContext
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

/** One managed Android runtime; resized windows are not physical foldable/hinge evidence. */
class EditorDraftHeadingRevealTest {
    @Test(timeout = 180_000)
    fun acceptedDraftKeepsItsHeadingBelowCloseWithoutStartingInput() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(Build.HARDWARE in setOf("ranchu", "goldfish") && context.packageName.endsWith(".debug"))
        ensureLogDateApplicationStarted(context.applicationContext as Application)
        val container = GlobalContext.get()
        val notes = container.get<JournalNotesRepository>()
        val drafts = container.get<EntryDraftRepository>()
        val recorder = container.get<AudioRecordingManager>()
        assertFalse(recorder.isRecording || recorder.isStartingRecording)
        val device = UiDevice.getInstance(instrumentation)
        val ui = NativeEditorAssertions(device, context.packageName)
        val output = File(checkNotNull(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")), "editor-draft-heading")
        check(output.mkdirs() || output.isDirectory)
        val now = Clock.System.now()
        val contextNotes =
            List(12) {
                JournalNote.Text(
                    content = "Earlier synthetic memory ${it + 1}",
                    creationTimestamp = now,
                    lastUpdated = now,
                )
            }
        val firstText = "Retained draft starts here."
        val draft =
            EntryDraft(
                Uuid.random(),
                listOf(JournalNote.Text(content = firstText, creationTimestamp = now, lastUpdated = now)) +
                    List(6) { JournalNote.Text(content = "Following draft memory ${it + 1}", creationTimestamp = now, lastUpdated = now) },
                now,
                now,
            )
        val oldSize = overrideValue(device.executeShellCommand("wm size"))
        val oldDensity = overrideValue(device.executeShellCommand("wm density"))
        try {
            runBlocking {
                contextNotes.forEach { notes.create(it) }
                assertEquals(draft.id, drafts.saveDraftSnapshot(draft))
            }
            for ((label, width) in listOf("normal-phone" to 0, "foldable-sized-window" to 1000, "landscape-tablet-sized-window" to 1280)) {
                if (width != 0) {
                    device.executeShellCommand("wm density 160")
                    device.executeShellCommand("wm size ${width}x800")
                }
                val intent =
                    Intent(context, MainActivity::class.java).apply {
                        putExtra(DEBUG_SKIP_ONBOARDING_EXTRA, true)
                        putExtra(EXTRA_AMBIENT_PROMPT_TARGET, AMBIENT_PROMPT_TARGET_DRAFT)
                        putExtra(EXTRA_AMBIENT_PROMPT_DRAFT_ID, draft.id.toString())
                    }
                ActivityScenario.launch<MainActivity>(intent).use { scenario ->
                    scenario.onActivity {
                        it.requestedOrientation =
                            if (width == 0) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    }
                    var previous: Pair<Rect, Rect>? = null
                    var stable = 0
                    ui.await("Heading and first draft clear the actual header at $label", 30_000) {
                        val heading = ui.text("Now") ?: return@await false
                        val field = ui.text(firstText) ?: return@await false
                        val close = ui.description("Close") ?: return@await false
                        val add = ui.text("Add to entry") ?: return@await false
                        if (!field.isEditable) return@await false
                        val glyph = ui.bounds(close)
                        val closeControl =
                            ui
                                .nodes()
                                .filter { it.isClickable && it.isEnabled && ui.bounds(it).contains(glyph) }
                                .minByOrNull { ui.bounds(it).let { bounds -> bounds.width() * bounds.height() } }
                                ?: return@await false
                        val closeBounds = ui.bounds(closeControl)
                        val headingBounds = ui.bounds(heading)
                        val fieldBounds = ui.bounds(field)
                        var ready = false
                        scenario.onActivity {
                            val decor = it.window.decorView
                            val density = it.resources.displayMetrics.density
                            ready = (width == 0 || (decor.width == width && decor.height == 800)) &&
                                headingBounds.top >= closeBounds.bottom + 16 * density &&
                                fieldBounds.top >= headingBounds.bottom + 8 * density &&
                                fieldBounds.bottom <= ui.bounds(add).top &&
                                !WindowInsetsCompat.toWindowInsetsCompat(decor.rootWindowInsets).isVisible(WindowInsetsCompat.Type.ime())
                        }
                        val bounds = headingBounds to fieldBounds
                        stable = if (ready && bounds == previous) stable + 1 else 0
                        previous = bounds
                        ready && stable >= 2
                    }
                    assertTrue(ui.nodes().none { it.isEditable && it.isFocused }, "Revealing a loaded draft must not focus an input")
                    assertTrue(ui.description("Return to entry") == null, "Reveal must stay in overview")
                    assertFalse(recorder.isRecording || recorder.isStartingRecording, "Reveal must never start capture")
                    check(device.takeScreenshot(File(output, "$label.png")))
                    var window = ""
                    scenario.onActivity {
                        window =
                            "${it.window.decorView.width}x${it.window.decorView.height}; density=${it.resources.displayMetrics.density}"
                    }
                    File(
                        output,
                        "$label.txt",
                    ).writeText(
                        "Native managed API${Build.VERSION.SDK_INT}; window=$window; resized=${width != 0}; no physical hinge claim\n${ui.describe()}",
                    )
                }
                val retained =
                    runBlocking {
                        drafts
                            .getDraft(draft.id)
                            .first()
                            .getOrThrow()
                            .notes
                    }
                assertEquals(draft.notes.map { it.uid }, retained.map { it.uid })
                draft.notes.zip(retained).forEach { (expected, actual) ->
                    actual as JournalNote.Text
                    assertTrue(actual.lastUpdated >= expected.lastUpdated, "Autosave time must not move backwards")
                    assertEquals(
                        expected,
                        actual.copy(lastUpdated = expected.lastUpdated),
                        "All retained content and identity fields must survive",
                    )
                }
            }
        } finally {
            device.executeShellCommand("wm size $oldSize")
            device.executeShellCommand("wm density $oldDensity")
            runBlocking {
                drafts.deleteDraft(draft.id)
                contextNotes.forEach { notes.removeById(it.uid) }
            }
        }
    }

    private fun overrideValue(value: String): String =
        value
            .lineSequence()
            .firstOrNull { it.startsWith("Override") }
            ?.substringAfter(": ")
            ?.trim() ?: "reset"
}
