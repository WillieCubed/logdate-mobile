package app.logdate.client.e2e

import android.content.Intent
import android.os.Bundle
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityOptionsCompat
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkInfo
import androidx.work.WorkManager
import app.logdate.client.media.MediaManager
import app.logdate.client.repository.journals.JournalContentRepository
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.JournalRepository
import app.logdate.feature.core.restore.AndroidRestoreLauncher
import app.logdate.feature.core.restore.RestoreLauncher
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.json.JSONObject
import org.koin.core.context.GlobalContext
import org.koin.core.context.loadKoinModules
import org.koin.dsl.module
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** Shared native cancellation checkpoints; no test cases or independent device lifecycle. */
abstract class ArchiveRecoveryCancellationFixture {
    protected val instrumentation = InstrumentationRegistry.getInstrumentation()
    protected val context get() = instrumentation.targetContext
    protected val fixture get(): File {
        val label = InstrumentationRegistry.getArguments().getString("archiveRecoveryFixtureLabel")
        require(label == null || label.matches(Regex("[a-z0-9-]+")))
        return context.noBackupFilesDir.resolve("archive-media-recovery-${label ?: "acceptance"}.json")
    }
    protected val koin get() = GlobalContext.get()
    protected val notes get() = koin.get<JournalNotesRepository>()
    protected val journals get() = koin.get<JournalRepository>()
    protected val links get() = koin.get<JournalContentRepository>()
    protected val media get() = koin.get<MediaManager>()
    protected val json = Json { ignoreUnknownKeys = true }

    /** Controlled save latency leaves the real worker at an accepted, persisted media checkpoint. */
    protected suspend fun cancelRunningRecovery() {
        val state = JSONObject(fixture.readText())
        verifyEdits(state)
        val launcher = assertIs<AndroidRestoreLauncher>(koin.get<RestoreLauncher>())
        val manager = WorkManager.getInstance(context)
        val bindings = RestoreWorkerTestBindings()
        val delegate = media
        val reachedSecondOriginal = CompletableDeferred<Unit>()
        val cancellationObserved = CompletableDeferred<Unit>()
        val gated =
            object : MediaManager by delegate {
                override suspend fun saveMediaFromFile(
                    sourceFilePath: String,
                    fileName: String,
                    mimeType: String,
                ): String {
                    if (fileName == "replacement-video.mp4") {
                        reachedSecondOriginal.complete(Unit)
                        try {
                            awaitCancellation()
                        } finally {
                            cancellationObserved.complete(Unit)
                        }
                    }
                    return delegate.saveMediaFromFile(sourceFilePath, fileName, mimeType)
                }
            }
        val workName = "archive_media_recovery_${state.getString("recoveryId")}"
        val previousWork =
            manager
                .getWorkInfosForUniqueWork(workName)
                .get()
                .map { it.id }
                .toSet()
        val pickerLaunches = trapDocumentPicker(launcher)
        var workId: UUID? = null
        var cancellationCompleted = false
        try {
            loadKoinModules(module { single<MediaManager> { gated } })
            launcher.selectArchiveForMediaRecovery(state.getString("recoveryId"))
            workId = awaitNewMediaWork(manager, workName, previousWork, pickerLaunches)
            assertEquals(WorkInfo.State.ENQUEUED, manager.getWorkInfoById(workId).get()?.state)
            instrumentation.sendStatus(0, Bundle().apply { putString("archiveCancellation", "RESTORE_CANCELLATION_READY") })
            withTimeout(30_000) { reachedSecondOriginal.await() }
            assertEquals(WorkInfo.State.RUNNING, manager.getWorkInfoById(workId).get()?.state)
            val progress = recoveryRecord(state).getJSONObject("summary")
            assertEquals(1, progress.getInt("mediaRecoveryChecked"))
            assertEquals(2, progress.getInt("mediaRecoveryTotal"))
            assertEquals(1, progress.getInt("unavailableOriginals"))
            assertTrue(progress.getBoolean("recoveringMedia"), "A blocked original must never be reported complete")
            val photo = assertIs<JournalNote.Image>(notes.getNoteById(state.id("note")))
            assertContentEquals(asset("proof.png"), delegate.readMedia(photo.mediaRef).data)
            state.put("acceptedPhotoReference", photo.mediaRef)
            state.put("acceptedPhotoHash", checksum(delegate.readMedia(photo.mediaRef).data))
            state.put("cancelledWorkId", workId.toString())
            state.put("retainedSourceHash", checksum(recoverySource(state).readBytes()))
            state.put(
                "noteIds",
                notes.allNotesObserved
                    .first()
                    .map { it.uid.toString() }
                    .sorted()
                    .joinToString(","),
            )
            state.put(
                "draftIds",
                journals
                    .getAllDrafts()
                    .map { it.id.toString() }
                    .sorted()
                    .joinToString(","),
            )
            fixture.writeText(state.toString())
            launcher.cancelRestore()
            withTimeout(15_000) { cancellationObserved.await() }
            awaitWorkFinished(workId)
            assertEquals(WorkInfo.State.CANCELLED, manager.getWorkInfoById(workId).get()?.state)
            assertTrue(recoveryRecord(state).getBoolean("pausedByUser"))
            assertTrue(context.filesDir.resolve("archive_recovery/records/${state.getString("recoveryId")}.paused").isFile)
            verifyCancelledAfterRestart()
            cancellationCompleted = true
        } finally {
            if (!cancellationCompleted) {
                launcher.cancelRestore()
                workId?.let { manager.cancelWorkById(it).result.get() }
            }
            bindings.restore()
        }
    }

    protected suspend fun verifyCancelledAfterRestart() {
        val state = JSONObject(fixture.readText())
        val record = recoveryRecord(state)
        assertTrue(record.getBoolean("pausedByUser"), "Restart must retain the user's pause")
        assertTrue(record.getBoolean("metadataApplied"))
        assertEquals(1, record.getJSONArray("pending").length())
        val summary = record.getJSONObject("summary")
        assertEquals(1, summary.getInt("unavailableOriginals"))
        assertEquals(1, summary.getInt("mediaRecoveryChecked"))
        assertEquals(2, summary.getInt("mediaRecoveryTotal"))
        assertEquals(state.getString("retainedSourceHash"), checksum(recoverySource(state).readBytes()))
        val work = WorkManager.getInstance(context).getWorkInfoById(UUID.fromString(state.getString("cancelledWorkId"))).get()
        assertEquals(WorkInfo.State.CANCELLED, assertNotNull(work).state)
        verifyAcceptedCheckpoint(state)
    }

    protected suspend fun resumeCancelledRecovery() {
        verifyCancelledAfterRestart()
        val state = JSONObject(fixture.readText())
        val launcher = assertIs<AndroidRestoreLauncher>(koin.get<RestoreLauncher>())
        val manager = WorkManager.getInstance(context)
        val workName = "archive_media_recovery_${state.getString("recoveryId")}"
        val previousWork =
            manager
                .getWorkInfosForUniqueWork(workName)
                .get()
                .map { it.id }
                .toSet()
        val pickerLaunches = trapDocumentPicker(launcher)
        launcher.selectArchiveForMediaRecovery(state.getString("recoveryId"))
        val id = awaitNewMediaWork(manager, workName, previousWork, pickerLaunches)
        assertTrue(id.toString() != state.getString("cancelledWorkId"))
        state.put("resumedWorkId", id.toString())
        fixture.writeText(state.toString())
        awaitWorkFinished(id)
        assertEquals(
            WorkInfo.State.SUCCEEDED,
            WorkManager
                .getInstance(context)
                .getWorkInfoById(id)
                .get()
                ?.state,
        )
        verifyCancelledCompletion()
    }

    private fun trapDocumentPicker(launcher: AndroidRestoreLauncher): AtomicInteger {
        val launches = AtomicInteger()
        launcher.setupActivityResultLauncher(
            object : ActivityResultLauncher<Intent>() {
                override fun launch(
                    input: Intent,
                    options: ActivityOptionsCompat?,
                ) {
                    launches.incrementAndGet()
                }

                override fun unregister() = Unit

                override val contract = ActivityResultContracts.StartActivityForResult()
            },
        )
        return launches
    }

    private suspend fun awaitNewMediaWork(
        manager: WorkManager,
        name: String,
        previous: Set<UUID>,
        pickerLaunches: AtomicInteger,
    ): UUID =
        withTimeout(15_000) {
            var resumed: UUID? = null
            while (resumed == null) {
                assertEquals(0, pickerLaunches.get(), "An intact retained archive must resume without selecting another document")
                resumed =
                    manager
                        .getWorkInfosForUniqueWork(name)
                        .get()
                        .firstOrNull { it.id !in previous }
                        ?.id
                if (resumed == null) delay(100)
            }
            assertEquals(0, pickerLaunches.get())
            resumed
        }

    protected suspend fun verifyCancelledCompletion() {
        val state = JSONObject(fixture.readText())
        verifyAcceptedCheckpoint(state)
        verifyOriginals(state)
        val record = recoveryRecord(state)
        assertFalse(record.optBoolean("pausedByUser"))
        assertEquals(0, record.optJSONArray("pending")?.length() ?: 0)
        val summary = record.getJSONObject("summary")
        assertEquals(0, summary.optInt("unavailableOriginals"))
        assertEquals(0, summary.optInt("unavailablePreviews"))
        assertFalse(summary.optBoolean("recoveringMedia"))
        assertEquals(1, summary.getInt("notesImported"))
        assertEquals(1, summary.getInt("draftsImported"))
        assertEquals(3, summary.getInt("mediaImported"))
        val manager = WorkManager.getInstance(context)
        assertEquals(WorkInfo.State.SUCCEEDED, manager.getWorkInfoById(UUID.fromString(state.getString("resumedWorkId"))).get()?.state)
        val cancelledState = manager.getWorkInfoById(UUID.fromString(state.getString("cancelledWorkId"))).get()?.state
        assertTrue(cancelledState == null || cancelledState == WorkInfo.State.CANCELLED, "Replaced work must stay cancelled or be pruned")
    }

    private suspend fun verifyAcceptedCheckpoint(state: JSONObject) {
        verifyEdits(state)
        val photo = assertIs<JournalNote.Image>(notes.getNoteById(state.id("note")))
        assertEquals(state.getString("acceptedPhotoReference"), photo.mediaRef)
        assertEquals(state.getString("acceptedPhotoHash"), checksum(media.readMedia(photo.mediaRef).data))
        assertEquals(
            state.getString("noteIds"),
            notes.allNotesObserved
                .first()
                .map { it.uid.toString() }
                .sorted()
                .joinToString(","),
        )
        assertEquals(
            state.getString("draftIds"),
            journals
                .getAllDrafts()
                .map { it.id.toString() }
                .sorted()
                .joinToString(","),
        )
    }

    private suspend fun awaitWorkFinished(id: UUID) =
        withTimeout(60_000) {
            val manager = WorkManager.getInstance(context)
            while (manager
                    .getWorkInfoById(id)
                    .get()
                    ?.state
                    ?.isFinished != true
            ) {
                delay(100)
            }
        }

    private fun recoveryRecord(state: JSONObject) =
        JSONObject(
            context.filesDir.resolve("archive_recovery/records/${state.getString("recoveryId")}.json").readText(),
        )

    private fun recoverySource(state: JSONObject): File {
        val record = recoveryRecord(state)
        return context.filesDir.resolve("archive_recovery/sources/${record.getString("id")}.${record.getString("sourceGeneration")}.zip")
    }

    private fun checksum(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {
            (it.toInt() and 0xff).toString(16).padStart(2, '0')
        }

    protected abstract suspend fun verifyEdits(state: JSONObject)

    protected abstract suspend fun verifyOriginals(state: JSONObject)

    protected abstract fun asset(name: String): ByteArray

    protected fun JSONObject.id(key: String): Uuid = Uuid.parse(getString(key))
}
