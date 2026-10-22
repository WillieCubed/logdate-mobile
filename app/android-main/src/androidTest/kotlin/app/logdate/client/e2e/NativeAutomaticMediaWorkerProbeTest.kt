package app.logdate.client.e2e

import android.app.Application
import android.os.Build
import android.os.Bundle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkInfo
import androidx.work.WorkManager
import app.logdate.client.MainActivity
import app.logdate.client.database.LogDateDatabase
import app.logdate.client.datastore.LogDateConfigDataSource
import app.logdate.client.datastore.SessionStorage
import app.logdate.client.ensureLogDateApplicationStarted
import app.logdate.client.media.MediaManager
import app.logdate.client.media.storage.StoredMediaReferences
import app.logdate.client.media.storage.isRecoveredOriginal
import app.logdate.client.networking.DataUsageMode
import app.logdate.client.networking.DataUsagePolicy
import app.logdate.client.sync.AndroidLogDateSyncWorker
import app.logdate.client.sync.SyncManager
import app.logdate.client.sync.metadata.UploadScope
import app.logdate.shared.config.LogDateConfigRepository
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.uuid.Uuid

/** Requires the synthetic enrollment server; verification never invokes recovery or sync itself. */
@RunWith(AndroidJUnit4::class)
class NativeAutomaticMediaWorkerProbeTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val koin get() = GlobalContext.get()
    private val database get() = koin.get<LogDateDatabase>()
    private val media get() = koin.get<MediaManager>()
    private val originals by lazy { NativeOriginalMediaProbe(context, koin, ::reportFailureState) }
    private val noteId = Uuid.parse("00000000-0000-0000-0000-000000000004")

    @Test
    fun automaticRecovery() =
        runBlocking {
            val arguments = InstrumentationRegistry.getArguments()
            val mode = arguments.getString("nativeAutomaticMediaMode")
            assumeTrue(mode != null)
            require(mode in setOf("metered", "restricted", "restart"))
            val stage = arguments.getString("nativeAutomaticMediaStage")
            require(stage in if (mode == "restart") setOf("prepare", "verify") else setOf("recover"))
            require(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
            require(context.packageName.endsWith(".debug"))
            val credentials = readNativeDeviceEnrollmentCredentials(context)
            require(credentials.sourceDevice != null) { "An enrolled synthetic destination is required" }
            awaitValidatedNativeWifi(context)
            ensureLogDateApplicationStarted(context.applicationContext as Application)
            ActivityScenario.launch(MainActivity::class.java).use {
                val sessions = koin.get<SessionStorage>()
                koin.get<LogDateConfigDataSource>().awaitConfigurationLoaded()
                assertEquals(credentials.origin, koin.get<LogDateConfigRepository>().getCurrentBackendUrl())
                withTimeout(30_000) { sessions.getSessionFlow().first { it?.accountId == credentials.owner } }
                val bound = requireNotNull(sessions.getOriginBoundSession())
                assertEquals(credentials.owner, bound.session.accountId)
                assertEquals(credentials.origin, bound.origin)
                val scope = UploadScope(credentials.owner, credentials.origin)
                val marker = File(context.noBackupFilesDir, "native-automatic-media-$mode.json")
                if (stage != "verify") {
                    val expected = if (mode == "restricted") DataUsageMode.Restricted else DataUsageMode.Conservative
                    withTimeout(30_000) { koin.get<DataUsagePolicy>().policy.first { it == expected } }
                    if (mode == "metered") {
                        withTimeout(60_000) {
                            WorkManager
                                .getInstance(context)
                                .getWorkInfosForUniqueWorkFlow(AndroidLogDateSyncWorker.WORK_NAME_IMMEDIATE_SYNC)
                                .first { work -> work.none { !it.state.isFinished } }
                        }
                    }
                    originals.prepareMissingOriginal(marker, scope)
                    if (mode == "metered") {
                        val armed = File(context.noBackupFilesDir, "native-media-worker-gate-armed")
                        assertFalse(armed.exists())
                        report("RESTORE_MEDIA_READY")
                        withTimeout(10_000) { while (!armed.exists()) delay(25) }
                    }
                    // Only the initial request is explicit. Policy changes and cold startup must
                    // resume accepted work without another request from the fixture.
                    koin.get<SyncManager>().requestBackup()
                    originals.retainDeferredOperation(marker, scope)
                    if (stage == "prepare") return@use
                    val gated =
                        if (mode == "metered") {
                            withTimeout(10_000) {
                                WorkManager
                                    .getInstance(context)
                                    .getWorkInfosForUniqueWorkFlow(AndroidLogDateSyncWorker.WORK_NAME_IMMEDIATE_SYNC)
                                    .first { work -> work.any { it.state == WorkInfo.State.RUNNING } }
                                    .single { it.state == WorkInfo.State.RUNNING }
                            }
                        } else {
                            null
                        }
                    report("RESTORE_MEDIA_DEFERRED")
                    awaitUnrestrictedNativeNetwork(context, koin.get<DataUsagePolicy>(), callbackTimeoutMillis = 30_000)
                    if (mode == "metered") {
                        try {
                            requirePromptSuccessorOrCompletedOriginal(marker, requireNotNull(gated), scope)
                        } catch (timeout: TimeoutCancellationException) {
                            reportFailureState(scope)
                            throw timeout
                        }
                    }
                }
                try {
                    originals.verifyAutomaticOriginal(marker, scope)
                } catch (timeout: TimeoutCancellationException) {
                    reportFailureState(scope)
                    throw timeout
                }
            }
        }

    private suspend fun reportFailureState(scope: UploadScope) {
        val row = database.downloadInboxDao().get(scope.ownerId, scope.serverOrigin, "MEDIA_NATIVE_NOTE", noteId.toString())
        val work =
            WorkManager
                .getInstance(context)
                .getWorkInfosForUniqueWorkFlow(AndroidLogDateSyncWorker.WORK_NAME_IMMEDIATE_SYNC)
                .first()
        val status = koin.get<SyncManager>().getSyncStatus()
        val periodic =
            WorkManager
                .getInstance(context)
                .getWorkInfosForUniqueWorkFlow(AndroidLogDateSyncWorker.WORK_NAME_PERIODIC_SYNC)
                .first()
        val note = database.imageNoteDao().getNoteOneOff(noteId)
        val diagnostic =
            JSONObject()
                .put("inboxState", row?.state)
                .put("inboxReason", row?.reason)
                .put("attempts", row?.attempts)
                .put("retryInMillis", row?.nextAttemptAt?.minus(System.currentTimeMillis()))
                .put("networkPolicy", koin.get<DataUsagePolicy>().currentMode().toString())
                .put("pendingDownloads", status.pendingDownloads)
                .put("requestState", status.requestState.toString())
                .put("work", JSONArray(work.map { "${it.state}:${it.runAttemptCount}" }))
                .put("periodicWork", JSONArray(periodic.map { "${it.state}:${it.runAttemptCount}" }))
                .put(
                    "completedCurrentMediaScope",
                    work.any {
                        it.state == WorkInfo.State.SUCCEEDED &&
                            it.outputData.getString("recovered_media_owner") == scope.ownerId &&
                            it.outputData.getString("recovered_media_origin") == scope.serverOrigin
                    },
                ).put("originalAvailable", media.isRecoveredOriginal(note.contentUri, koin.get<StoredMediaReferences>()))
        InstrumentationRegistry.getInstrumentation().sendStatus(
            0,
            Bundle().apply { putString("nativeMediaDiagnostic", diagnostic.toString()) },
        )
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val errors =
            instrumentation.uiAutomation
                .executeShellCommand(
                    "logcat -d -t 300 -v brief -s AndroidMediaManager:E NativeMediaRecovery:W",
                ).use { descriptor ->
                    android.os.ParcelFileDescriptor
                        .AutoCloseInputStream(descriptor)
                        .bufferedReader()
                        .use { it.readText() }
                }
        instrumentation.sendStatus(0, Bundle().apply { putString("nativeMediaStorageLog", errors) })
    }

    private suspend fun requirePromptSuccessorOrCompletedOriginal(
        marker: File,
        predecessor: WorkInfo,
        scope: UploadScope,
    ) {
        val manager = WorkManager.getInstance(context)
        assertEquals(WorkInfo.State.RUNNING, manager.getWorkInfoByIdFlow(predecessor.id).first()?.state)
        report("RESTORE_MEDIA_POLICY_LIFTED")
        val baseline =
            JSONObject(marker.readText())
                .getJSONArray("priorWorkIds")
                .let { ids -> (0 until ids.length()).map { ids.getString(it) }.toSet() }
        // The server gate may belong to a periodic writer while this immediate worker
        // waits for the phase lock. It may then recover the original itself. Otherwise a
        // new request must persist before the 30-second retry backoff can satisfy the case.
        val completion =
            withTimeout(15_000) {
                manager
                    .getWorkInfosForUniqueWorkFlow(AndroidLogDateSyncWorker.WORK_NAME_IMMEDIATE_SYNC)
                    .first { work ->
                        work.any { it.id != predecessor.id && it.id.toString() !in baseline } ||
                            (
                                work.any { it.id == predecessor.id && completedMediaForScope(it, scope) } &&
                                    originals.appliedOriginal(marker, scope)
                            )
                    }.let { work ->
                        work.firstOrNull { it.id != predecessor.id && it.id.toString() !in baseline }
                            ?: work.single { it.id == predecessor.id && completedMediaForScope(it, scope) }
                    }
            }
        originals.writeCheckpoint(marker, JSONObject(marker.readText()).put("completionWorkId", completion.id.toString()))
    }

    private fun report(marker: String) {
        InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply { putString("nativeMediaRecovery", marker) })
    }
}
