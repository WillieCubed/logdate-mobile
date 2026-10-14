package app.logdate.moments.e2e

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.moments.MarkState
import app.logdate.client.repository.moments.MarkedMoment
import app.logdate.client.repository.moments.SyncableMarkedMomentRepository
import app.logdate.feature.timeline.ui.marks.MarkedMomentEntry
import app.logdate.ui.theme.LogDateTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.uuid.Uuid

@RunWith(AndroidJUnit4::class)
class MarkedMomentTimelineRuntimeTest {
    @get:Rule val compose = createComposeRule()
    private val koin get() = KoinJavaComponent.getKoin()
    private val marks get() = koin.get<SyncableMarkedMomentRepository>()
    private val notes get() = koin.get<JournalNotesRepository>()
    private lateinit var mark: MarkedMoment
    private var resolved = emptySet<Uuid>()
    private var written: Uuid? = null

    @Before fun prepare() =
        runBlocking {
            val now = Clock.System.now()
            mark = MarkedMoment(Uuid.random(), now, "UTC", null, null, MarkState.OPEN, "", emptySet(), "emulator-mark", now, 0)
            assertNotNull(marks.applyFromSync(mark))
            Unit
        }

    @After fun cleanup() =
        runBlocking {
            val ids = resolved + marks.getMark(mark.id)?.resolvedNoteIds.orEmpty()
            marks.delete(mark.id, Clock.System.now())
            ids.forEach { notes.removeById(it) }
        }

    private fun <T> storage(block: suspend () -> T): T {
        val work = CoroutineScope(Dispatchers.IO).async { withTimeout(10_000) { block() } }
        compose.waitUntil(15_000) { work.isCompleted }
        return runBlocking { work.await() }
    }

    private fun open() {
        compose.setContent { LogDateTheme { MarkedMomentEntry(mark) { written = it } } }
        compose.waitForIdle()
    }

    @Test fun captionPublishesRealTimelineNoteAndFillsMark() {
        open()
        compose.onNodeWithText("What was happening?").performTextInput("Emulator marked moment")
        val instrumentation =
            androidx.test.platform.app.InstrumentationRegistry
                .getInstrumentation()
        val output =
            androidx.test.platform.app.InstrumentationRegistry
                .getArguments()
                .getString("additionalTestOutputDir")
        if (output != null) {
            val directory = java.io.File(output).apply { mkdirs() }
            kotlin.test.assertTrue(
                androidx.test.uiautomator.UiDevice
                    .getInstance(
                        instrumentation,
                    ).takeScreenshot(java.io.File(directory, "marked-moment-caption-phone.png")),
            )
        }
        compose.onNodeWithText("Save caption").performClick()
        compose.waitUntil(15_000) { runBlocking { marks.getMark(mark.id)?.state == MarkState.FILLED } }
        val filled = storage { marks.getMark(mark.id) }!!
        resolved = filled.resolvedNoteIds
        assertEquals("Emulator marked moment", filled.caption)
        val note = storage { notes.getNoteById(filled.resolvedNoteIds.single()) }
        assertEquals("Emulator marked moment", (note as JournalNote.Text).content)
        assertEquals(mark.capturedAt.toEpochMilliseconds(), note.creationTimestamp.toEpochMilliseconds())
    }

    @Test fun writeMoreKeepsMarkUntilExplicitLeaveAsIs() {
        open()
        compose.onNodeWithText("Write more").performClick()
        assertEquals(mark.id, written)
        assertEquals(MarkState.OPEN, storage { marks.getMark(mark.id) }?.state)
        compose.onNodeWithText("Leave as is").performClick()
        compose.waitUntil(15_000) { runBlocking { marks.getMark(mark.id)?.state == MarkState.DISMISSED } }
    }

    @Test fun deleteRequiresConsequenceReviewAndRetainsDeletion() {
        open()
        compose.onNodeWithText("Delete mark").performClick()
        compose.onNodeWithText("Delete this mark?").assertExists()
        assertNotNull(storage { marks.getMark(mark.id) })
        compose.onNode(hasText("Delete mark") and hasAnyAncestor(isDialog())).performClick()
        compose.waitUntil(15_000) { runBlocking { marks.getMark(mark.id) == null } }
        assertNull(storage { marks.getMark(mark.id) })
    }

    @Test fun photoAttachmentCopiesGallerySourceAndPublishesNote() {
        val context =
            androidx.test.platform.app.InstrumentationRegistry
                .getInstrumentation()
                .targetContext
        val source = java.io.File(context.cacheDir, "mark-photo-${Uuid.random()}.png")
        val bitmap = android.graphics.Bitmap.createBitmap(12, 12, android.graphics.Bitmap.Config.ARGB_8888)
        source.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val uri =
            androidx.core.content.FileProvider
                .getUriForFile(context, "${context.packageName}.provider", source)
                .toString()
        try {
            val attach = koin.get<app.logdate.client.domain.moments.AttachPhotoToMarkUseCase>()
            kotlin.test.assertTrue(storage { attach(mark.id, uri) })
            val filled = storage { marks.getMark(mark.id) }!!
            resolved = filled.resolvedNoteIds
            assertEquals(MarkState.FILLED, filled.state)
            val note = storage { notes.getNoteById(resolved.single()) } as JournalNote.Image
            kotlin.test.assertNotEquals(uri, note.mediaRef)
            val manager = koin.get<app.logdate.client.media.MediaManager>()
            kotlin.test.assertTrue(storage { manager.openMedia(note.mediaRef).sizeBytes > 0 })
            kotlin.test.assertTrue(source.exists())
        } finally {
            source.delete()
        }
    }
}
