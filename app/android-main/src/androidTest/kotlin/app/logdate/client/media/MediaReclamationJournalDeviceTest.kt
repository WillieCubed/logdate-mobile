package app.logdate.client.media

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.logdate.client.media.storage.AndroidMediaReclamationJournal
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

@RunWith(AndroidJUnit4::class)
class MediaReclamationJournalDeviceTest {
    @Test
    fun candidateSurvivesWriterProcessDeath() =
        withFixture { context, root, runId ->
            val reference = "logdate-media://library/$runId.jpg"
            val before = checkNotNull(call(context, "pid", runId)).getInt("pid")

            assertEquals(before, checkNotNull(call(context, "recordThenExit", runId, reference)).getInt("pid"))

            assertEquals(listOf(reference), runBlocking { AndroidMediaReclamationJournal(root).pendingCandidates() })
            var restarted = before
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
            while (restarted == before && System.nanoTime() < deadline) {
                Thread.sleep(50)
                restarted = runCatching { checkNotNull(call(context, "pid", runId)).getInt("pid") }.getOrDefault(before)
            }
            assertNotEquals(before, restarted, "The candidate must outlive its writing process")
        }

    @Test
    fun candidateWriteWaitsForAnotherProcessHoldingTheJournalLock() =
        withFixture { context, root, runId ->
            val executor = Executors.newFixedThreadPool(2)
            try {
                val held = executor.submit { call(context, "holdLock", runId) }
                val ready = File(root, "ready")
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
                while (!ready.exists() && System.nanoTime() < deadline) Thread.sleep(10)
                assertTrue(ready.exists(), "The other process never acquired the journal lock")

                val reference = "logdate-media://library/$runId.png"
                val started = CountDownLatch(1)
                val recording =
                    executor.submit {
                        started.countDown()
                        runBlocking { AndroidMediaReclamationJournal(root).recordCandidate(reference) }
                    }
                assertTrue(started.await(2, TimeUnit.SECONDS), "The competing writer never started")
                Thread.sleep(200)
                assertFalse(recording.isDone, "A second process must not write while the journal lock is held")
                File(root, "release").writeText("go")

                recording.get(10, TimeUnit.SECONDS)
                held.get(10, TimeUnit.SECONDS)
                assertEquals(listOf(reference), runBlocking { AndroidMediaReclamationJournal(root).pendingCandidates() })
            } finally {
                File(root, "release").writeText("go")
                executor.shutdownNow()
            }
        }

    private fun withFixture(block: (Context, File, String) -> Unit) {
        check(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish") { "Managed emulator required" }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".debug")) { "Isolated debug app required" }
        val runId = Uuid.random().toString()
        val root = File(context.noBackupFilesDir, "media-reclamation-probe/$runId")
        try {
            block(context, root, runId)
        } finally {
            check(root.deleteRecursively())
        }
    }

    private fun call(
        context: Context,
        method: String,
        runId: String,
        reference: String? = null,
    ): Bundle? {
        val uri = Uri.parse("content://${context.packageName}.media-reclamation-probe")
        val extras =
            Bundle().apply {
                putString("runId", runId)
                reference?.let { putString("reference", it) }
            }
        return context.contentResolver.call(uri, method, null, extras)
    }
}
