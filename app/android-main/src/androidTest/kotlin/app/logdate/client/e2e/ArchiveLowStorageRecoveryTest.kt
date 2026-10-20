package app.logdate.client.e2e

import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import app.logdate.client.domain.export.ExportDraft
import app.logdate.client.domain.export.ExportMetadata
import app.logdate.client.domain.export.ExportSchemaVersion
import app.logdate.client.domain.export.ExportStats
import app.logdate.client.domain.restore.RestoreUserDataUseCase
import app.logdate.client.ensureLogDateApplicationStarted
import app.logdate.client.repository.journals.JournalRepository
import app.logdate.feature.core.restore.RestoreLauncher
import app.logdate.feature.core.restore.RestoreWorker
import app.logdate.shared.model.SerializableTextBlock
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.core.context.loadKoinModules
import org.koin.dsl.module
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

/** The host changes Android's real storage state and restarts the app between stages. */
@RunWith(AndroidJUnit4::class)
class ArchiveLowStorageRecoveryTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val koin get() = GlobalContext.get()
    private val journals get() = koin.get<JournalRepository>()
    private val manager get() = WorkManager.getInstance(context)
    private val fixture: File get() {
        val label = requireNotNull(InstrumentationRegistry.getArguments().getString("storageRecoveryLabel"))
        require(label.matches(Regex("[a-z0-9-]+")))
        return context.noBackupFilesDir.resolve("archive-storage-$label.json")
    }

    @Test
    fun managedAcceptedArchiveWaitsForStorageThenResumes(): Unit =
        runBlocking {
            assumeTrue(InstrumentationRegistry.getArguments().getString("managedStorageRecoveryAcceptance") == "true")
            require(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
            require(context.packageName.endsWith(".debug"))
            val original = platformStorageState()
            assertEquals("UNKNOWN" to "NORMAL", original, "Use a fresh managed storage fixture")
            try {
                ensureLogDateApplicationStarted(context.applicationContext as android.app.Application)
                seedAcceptedSource()
                changeStorageState("force-low", "LOW" to "LOW")
                enqueueWhileStorageLow()
                verifyWaitingAfterRestart()
                verifyResumed { changeStorageState("force-not-low", "NORMAL" to "NORMAL") }
            } finally {
                restoreFixtureState({ changeStorageState("reset", original) })
            }
        }

    private fun storageShell(command: String): String =
        ParcelFileDescriptor
            .AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command))
            .bufferedReader()
            .use { it.readText() }

    private fun platformStorageState(): Pair<String, String> {
        val text = storageShell("dumpsys devicestoragemonitor")
        val forced = requireNotNull(Regex("\\bmForceState=(\\w+)").find(text)).groupValues[1]
        val level = requireNotNull(Regex("\\blevel=(\\w+)").find(text)).groupValues[1]
        return forced to level
    }

    private suspend fun changeStorageState(
        command: String,
        expected: Pair<String, String>,
    ) {
        storageShell("cmd devicestoragemonitor $command -f")
        withTimeout(10_000) {
            while (platformStorageState() != expected) delay(100)
        }
    }

    @Test
    fun acceptedArchiveWaitsForStorageAcrossProcessDeath(): Unit =
        runBlocking {
            val mode = InstrumentationRegistry.getArguments().getString("storageRecoveryMode")
            assumeTrue("Run through the low-storage acceptance harness", mode in setOf("seed", "enqueue", "waiting", "resume"))
            require(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
            require(context.packageName.endsWith(".debug"))
            ensureLogDateApplicationStarted(context.applicationContext as android.app.Application)
            when (mode) {
                "seed" -> seedAcceptedSource()
                "enqueue" -> enqueueWhileStorageLow()
                "waiting" -> verifyWaitingAfterRestart()
                "resume" -> verifyResumed()
            }
        }

    private suspend fun seedAcceptedSource() {
        assertFalse(fixture.exists(), "Use a new fixture without replacing existing content")
        val draftId = Uuid.random()
        val now = Clock.System.now()
        val draft =
            ExportDraft(
                draftId.toString(),
                content = "",
                createdAt = now,
                updatedAt = now,
                blocks = listOf(SerializableTextBlock(Uuid.random(), now, content = "Accepted archive survived low storage")),
            )
        val archive = File.createTempFile("storage-recovery-", ".zip", context.cacheDir)
        ZipOutputStream(archive.outputStream()).use { zip ->
            mapOf(
                "metadata.json" to
                    Json.encodeToString(
                        ExportMetadata(
                            version = ExportSchemaVersion.V1_2,
                            exportDate = now,
                            userId = "synthetic-storage-recovery",
                            deviceId = "synthetic-source",
                            appVersion = "1.0",
                            stats = ExportStats(0, 0, 1, 0),
                        ),
                    ),
                "journals.json" to "{\"journals\":[]}",
                "notes.json" to "{\"notes\":[]}",
                "journal_notes.json" to "{\"journal_notes\":[]}",
                "drafts.json" to Json.encodeToString(mapOf("drafts" to listOf(draft))),
            ).forEach { (name, value) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(value.encodeToByteArray())
                zip.closeEntry()
            }
        }
        val id = UUID.randomUUID()
        val bindings = RestoreWorkerTestBindings()
        val unavailable = mockk<RestoreUserDataUseCase>()
        coEvery {
            unavailable.restore(
                archive = any(),
                options = any(),
                mediaImporter = anyNullable(),
                onProgress = anyNullable(),
                onPendingMedia = anyNullable(),
                mediaRecoveryId = any(),
                expectedNoteScope = anyNullable(),
            )
        } throws IOException("Synthetic interrupted archive write")
        try {
            loadKoinModules(module { factory<RestoreUserDataUseCase> { unavailable } })
            val result =
                TestListenableWorkerBuilder<RestoreWorker>(context)
                    .setId(id)
                    .setInputData(Data.Builder().putString(RestoreWorker.SOURCE_URI_KEY, archive.toURI().toString()).build())
                    .build()
                    .doWork()
            assertIs<ListenableWorker.Result.Retry>(result)
        } finally {
            bindings.restore()
            archive.delete()
        }
        val state = JSONObject().put("recoveryId", id.toString()).put("draftId", draftId.toString())
        val record = record(state)
        assertFalse(record.optBoolean("metadataApplied", false))
        state.put("sourceHash", hash(source(state)))
        fixture.writeText(state.toString())
        assertNull(journals.getDraftForRestore(draftId))
    }

    private suspend fun enqueueWhileStorageLow() {
        val state = JSONObject(fixture.readText())
        koin.get<RestoreLauncher>().selectArchiveForMediaRecovery(state.getString("recoveryId"))
        val work = awaitSingleWork(state)
        state.put("workId", work.id.toString())
        fixture.writeText(state.toString())
        assertTrue(work.constraints.requiresStorageNotLow(), "Accepted archive must wait for Android storage to recover")
        verifyWaiting(state, work)
    }

    private suspend fun verifyWaitingAfterRestart() {
        val state = JSONObject(fixture.readText())
        val work = awaitSingleWork(state)
        assertEquals(state.getString("workId"), work.id.toString(), "Restart must retain the original request")
        verifyWaiting(state, work)
    }

    private suspend fun verifyWaiting(
        state: JSONObject,
        work: WorkInfo,
    ) {
        delay(1_000)
        assertEquals(WorkInfo.State.ENQUEUED, manager.getWorkInfoById(work.id).get()?.state)
        assertFalse(record(state).optBoolean("metadataApplied", false))
        assertEquals(state.getString("sourceHash"), hash(source(state)), "Accepted archive bytes must remain intact")
        assertNull(journals.getDraftForRestore(Uuid.parse(state.getString("draftId"))))
    }

    private suspend fun verifyResumed(
        onStorageWaiting: suspend () -> Unit = {
            instrumentation.sendStatus(0, Bundle().apply { putString("restoreStorage", "RESTORE_STORAGE_WAITING") })
        },
    ) {
        val state = JSONObject(fixture.readText())
        val id = UUID.fromString(state.getString("workId"))
        verifyWaiting(state, assertNotNull(manager.getWorkInfoById(id).get()))
        onStorageWaiting()
        withTimeout(60_000) {
            while (manager
                    .getWorkInfoById(id)
                    .get()
                    ?.state
                    ?.isFinished != true
            ) {
                delay(100)
            }
        }
        assertEquals(WorkInfo.State.SUCCEEDED, manager.getWorkInfoById(id).get()?.state)
        val draft = assertNotNull(journals.getDraftForRestore(Uuid.parse(state.getString("draftId"))))
        assertEquals("Accepted archive survived low storage", assertIs<SerializableTextBlock>(draft.blocks.single()).content)
        assertTrue(record(state).getBoolean("metadataApplied"))
        assertEquals(0, record(state).optJSONArray("pending")?.length() ?: 0)
        assertEquals(listOf(id), manager.getWorkInfosForUniqueWork(workName(state)).get().map { it.id })
    }

    private suspend fun awaitSingleWork(state: JSONObject): WorkInfo =
        withTimeout(15_000) {
            while (true) {
                val works = manager.getWorkInfosForUniqueWork(workName(state)).get()
                if (works.isNotEmpty()) return@withTimeout works.single()
                delay(100)
            }
            error("Unreachable")
        }

    private fun workName(state: JSONObject) = "archive_metadata_recovery_${state.getString("recoveryId")}"

    private fun record(state: JSONObject) =
        JSONObject(
            context.filesDir.resolve("archive_recovery/records/${state.getString("recoveryId")}.json").readText(),
        )

    private fun source(state: JSONObject): File {
        val record = record(state)
        return context.filesDir.resolve("archive_recovery/sources/${record.getString("id")}.${record.getString("sourceGeneration")}.zip")
    }

    private fun hash(file: File) =
        MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") {
            (it.toInt() and 0xff).toString(16).padStart(2, '0')
        }
}
