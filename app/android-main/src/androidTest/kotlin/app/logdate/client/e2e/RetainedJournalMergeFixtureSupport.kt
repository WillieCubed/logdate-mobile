package app.logdate.client.e2e

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import androidx.test.core.app.ActivityScenario
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import app.logdate.client.MainActivity
import app.logdate.client.database.LogDateDatabase
import app.logdate.client.device.restore.RetainedJournalWorkspace
import app.logdate.client.media.MediaManager
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.JournalRepository
import app.logdate.shared.model.EditorDraft
import app.logdate.shared.model.SerializableTextBlock
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal object RetainedJournalMergeFixtureSupport {
    fun requireAcceptanceDevice(context: Context) {
        check(Build.HARDWARE in setOf("ranchu", "goldfish")) { "Gradle Managed Device required" }
        check(context.packageName.endsWith(".debug")) { "Synthetic debug application required" }
    }

    fun removeEmptyDirectories(root: File) {
        if (!root.exists()) return
        root.walkBottomUp().filter(File::isDirectory).forEach { directory ->
            check(directory.listFiles().isNullOrEmpty()) { "Expected the active media directory to be empty" }
            check(directory.delete()) { "Could not remove empty working media directory" }
        }
        check(!root.exists()) { "The now-empty active media root should be removed for admission testing" }
    }

    fun captureJournalScreen(
        context: Context,
        instrumentation: android.app.Instrumentation,
        journalId: Uuid,
        evidence: File,
        screenshot: String,
        vararg visibleText: String,
    ) {
        val intent =
            Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                data = Uri.parse("logdate://journal/$journalId")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        ActivityScenario.launch<MainActivity>(intent).use {
            val device = UiDevice.getInstance(instrumentation)
            visibleText.forEach { text ->
                assertTrue(device.wait(Until.hasObject(By.text(text)), 60_000L), "Journal screen did not show $text")
            }
            captureScreenshot(instrumentation, evidence, screenshot)
        }
    }

    fun captureScreenshot(
        instrumentation: android.app.Instrumentation,
        directory: File,
        name: String,
    ) {
        val destination = File(directory, name)
        assertTrue(UiDevice.getInstance(instrumentation).takeScreenshot(destination), "Could not capture $name")
        assertTrue(destination.isFile && destination.length() > 8)
    }

    fun draft(
        id: Uuid,
        journalId: Uuid,
        content: String,
        now: Instant,
    ) = EditorDraft(
        id = id,
        blocks = listOf(SerializableTextBlock(Uuid.random(), now, content = content)),
        selectedJournalIds = listOf(journalId),
        createdAt = now,
        lastModifiedAt = now,
    )

    fun draftText(draft: EditorDraft): String =
        draft.blocks
            .filterIsInstance<SerializableTextBlock>()
            .single()
            .content

    fun pngFixture(): ByteArray {
        val bitmap = Bitmap.createBitmap(128, 96, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.rgb(36, 64, 96))
            val paint = Paint().apply { color = Color.rgb(242, 180, 72) }
            canvas.drawRect(8f, 12f, 72f, 84f, paint)
            paint.color = Color.rgb(120, 208, 172)
            canvas.drawCircle(96f, 48f, 24f, paint)
            return ByteArrayOutputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
                output.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    fun writeDurably(
        file: File,
        contents: String,
    ) {
        FileOutputStream(file).use { output ->
            output.write(contents.encodeToByteArray())
            output.fd.sync()
        }
    }

    suspend fun assertRecoveredContent(
        state: JSONObject,
        session: RetainedJournalWorkspace.Session,
        delivery: String,
        journals: JournalRepository,
        notes: JournalNotesRepository,
        media: MediaManager,
        database: LogDateDatabase,
        workspace: RetainedJournalWorkspace,
    ) {
        val journalId = Uuid.parse(state.getString("journalId"))
        val conflictNoteId = Uuid.parse(state.getString("conflictNoteId"))
        val deletedNoteId = Uuid.parse(state.getString("deletedNoteId"))
        val imageNoteId = Uuid.parse(state.getString("imageNoteId"))
        val draftId = Uuid.parse(state.getString("draftId"))
        val journalsNow = journals.allJournalsObserved.first()
        val sourceJournals = journalsNow.filter { it.id != journalId && it.title == "Source journal" }
        assertEquals(1, sourceJournals.size, "Idempotent recovery has one stable source journal variant")
        val sourceJournal = sourceJournals.single()
        assertEquals("Working journal edit", journals.getJournalById(journalId)?.title)

        val notesNow = notes.allNotesObserved.first()
        val sourceConflicts = notesNow.filterIsInstance<JournalNote.Text>().filter { it.content == "Source conflict" }
        assertEquals(1, sourceConflicts.size, "Idempotent recovery has one stable source note variant")
        val sourceConflict = sourceConflicts.single()
        assertEquals("Working text edit", (notes.getNoteById(conflictNoteId) as? JournalNote.Text)?.content)
        assertNull(notes.getNoteById(deletedNoteId), "A working deletion must not be resurrected")
        assertTrue(
            sourceJournal.id to sourceConflict.uid in notes.getAllJournalNoteLinks().toSet(),
            "Recovered note membership follows the recovered journal variant",
        )

        val image = assertNotNull(notes.getNoteById(imageNoteId) as? JournalNote.Image)
        val recoveredBytes = media.readMedia(image.mediaRef).data
        assertEquals(state.getString("imageSha256"), sha256(recoveredBytes))
        val drafts = journals.getAllDrafts()
        assertEquals("working unfinished edit", draftText(assertNotNull(drafts.singleOrNull { it.id == draftId })))
        val sourceDrafts =
            drafts.filter { it.id != draftId && draftText(it) == "source unfinished edit" }
        assertEquals(1, sourceDrafts.size, "The source draft is recovered as one stable variant")
        assertEquals(listOf(sourceJournal.id), sourceDrafts.single().selectedJournalIds)

        assertTrue(
            database.syncMetadataDao().getAllPending().any { pending ->
                pending.entityType == "NOTE" &&
                    pending.entityId == deletedNoteId.toString() &&
                    pending.operation == "DELETE" &&
                    pending.ownerId == state.getString("ownerId") &&
                    pending.serverOrigin == state.getString("serverOrigin")
            },
            "The scoped repository deletion outbox remains intact",
        )
        assertTrue(workspace.retainedSources().containsKey(session.id), "The immutable source stays in custody")
        assertTrue(
            workspace.coherentIncomingPayloads().containsKey(delivery),
            "The accepted key payload stays in custody",
        )
    }
}
