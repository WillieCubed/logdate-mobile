package app.logdate.client.e2e

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import app.logdate.client.EditorActivity
import app.logdate.client.media.audio.AudioDurationResolver
import app.logdate.client.media.audio.AudioRecordingManager
import app.logdate.client.repository.journals.EntryDraftRepository
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.PendingMediaRecord
import app.logdate.client.repository.journals.PendingMediaType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import java.io.File
import kotlin.time.Clock
import kotlin.uuid.Uuid

/** Two-phase acceptance: terminate the app after the ready marker, then run recovery. */
@RunWith(AndroidJUnit4::class)
class QuietSaveRecoveryE2ETest {
    @get:Rule
    val microphone: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO)

    @Test
    fun prepareRecordingForProcessTermination() =
        runBlocking<Unit> {
            requireEmulator()
            val context = ApplicationProvider.getApplicationContext<Context>()
            val scenario = ActivityScenario.launch(EditorActivity::class.java)
            try {
                val repository = GlobalContext.get().get<EntryDraftRepository>()
                val recorder = GlobalContext.get().get<AudioRecordingManager>()
                val now = Clock.System.now()
                val block = Uuid.random()
                val draft =
                    repository.createDraft(
                        Uuid.random(),
                        listOf(JournalNote.Text(content = "Recording recovery acceptance", creationTimestamp = now, lastUpdated = now)),
                        listOf(PendingMediaRecord(block, PendingMediaType.AUDIO, now, timeZoneId = TimeZone.currentSystemDefault().id)),
                    )
                assertTrue(recorder.startRecording(block))
                delay(4_000)
                val pending =
                    repository
                        .getDraft(draft)
                        .first()
                        .getOrThrow()
                        .pendingMedia
                        .single()
                assertNotNull(pending.recordingOwnerId)
                assertFalse(File(pending.filePath!!).exists())
                File(context.getExternalFilesDir(null), "quiet-save-recovery-draft.txt").writeText(draft.toString())
                InstrumentationRegistry.getInstrumentation().sendStatus(
                    2,
                    Bundle().apply { putString("stream", "QUIET_SAVE_READY_FOR_PROCESS_TERMINATION\n") },
                )
                delay(600_000)
                error("Acceptance driver must terminate the app while recording")
            } finally {
                scenario.close()
            }
        }

    @Test
    fun recoverRecordingAfterProcessTermination() =
        runBlocking<Unit> {
            requireEmulator()
            val context = ApplicationProvider.getApplicationContext<Context>()
            val scenario = ActivityScenario.launch(EditorActivity::class.java)
            try {
                val koin = GlobalContext.get()
                val repository = koin.get<EntryDraftRepository>()
                val draft = Uuid.parse(File(context.getExternalFilesDir(null), "quiet-save-recovery-draft.txt").readText())
                val interrupted = repository.getDraft(draft).first().getOrThrow()
                val pending = interrupted.pendingMedia.single()
                val path = pending.filePath!!
                val duration = koin.get<AudioDurationResolver>().resolveDurationMs(path)
                assertNotNull(duration)
                assertTrue(duration!! >= 2_000)
                assertTrue(File(path).length() > 0)
                val saved = repository.completeRecording(pending.blockId, path, duration, pending.recordingOwnerId!!, false)
                assertEquals(draft, saved)
                assertEquals(draft, repository.completeRecording(pending.blockId, path, duration, pending.recordingOwnerId!!, false))
                val recovered = repository.getDraft(draft).first().getOrThrow()
                assertEquals("Recording recovery acceptance", (recovered.notes.first() as JournalNote.Text).content)
                assertEquals(1, recovered.notes.filterIsInstance<JournalNote.Audio>().size)
                assertTrue(recovered.pendingMedia.isEmpty())
            } finally {
                scenario.close()
            }
        }

    private fun requireEmulator() {
        check(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish") { "Recovery acceptance requires an emulator" }
    }
}
