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
import app.logdate.client.datastore.LogDateConfigDataSource
import app.logdate.client.datastore.SessionStorage
import app.logdate.client.ensureLogDateApplicationStarted
import app.logdate.client.media.MediaManager
import app.logdate.client.networking.DataUsageMode
import app.logdate.client.networking.DataUsagePolicy
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.sync.AndroidLogDateSyncWorker
import app.logdate.client.sync.SyncManager
import app.logdate.client.sync.cloud.CloudMediaDataSource
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.MediaSyncRefStore
import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.client.sync.metadata.UploadScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import java.io.File
import java.security.MessageDigest
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** The host holds a real server request after the full worker's upload phase. */
@RunWith(AndroidJUnit4::class)
class NativeDeferredOriginalUploadProbeTest {
    @Test
    fun policyLiftAfterUploadPhaseResumesTheRetainedOriginal() =
        runBlocking {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            assumeTrue(InstrumentationRegistry.getArguments().getString("nativeAutomaticMediaMode") == "upload")
            val context = instrumentation.targetContext
            require(Build.HARDWARE in setOf("ranchu", "goldfish") && context.packageName.endsWith(".debug"))
            val fixture = readNativeDeviceEnrollmentCredentials(context)
            require(fixture.sourceDevice != null)
            awaitValidatedNativeWifi(context)
            ensureLogDateApplicationStarted(context.applicationContext as Application)
            val koin = GlobalContext.get()
            ActivityScenario.launch(MainActivity::class.java).use {
                koin.get<LogDateConfigDataSource>().awaitConfigurationLoaded()
                val sessions = koin.get<SessionStorage>()
                withTimeout(30_000) { sessions.getSessionFlow().first { it?.accountId == fixture.owner } }
                val bound = assertNotNull(sessions.getOriginBoundSession())
                assertEquals(fixture.owner, bound.session.accountId)
                assertEquals(fixture.origin, bound.origin)
                val scope = UploadScope(fixture.owner, fixture.origin)
                val policy = koin.get<DataUsagePolicy>()
                withTimeout(30_000) { policy.policy.first { it == DataUsageMode.Conservative } }
                val manager = WorkManager.getInstance(context)

                fun work() = manager.getWorkInfosForUniqueWorkFlow(AndroidLogDateSyncWorker.WORK_NAME_IMMEDIATE_SYNC)

                suspend fun periodicWriterRunning(): Boolean =
                    manager
                        .getWorkInfosForUniqueWorkFlow(AndroidLogDateSyncWorker.WORK_NAME_PERIODIC_SYNC)
                        .first()
                        .any { it.state == WorkInfo.State.RUNNING }
                val prior =
                    withTimeout(60_000) {
                        while (true) {
                            val rows = work().first()
                            if (rows.none { !it.state.isFinished } &&
                                !periodicWriterRunning()
                            ) {
                                return@withTimeout rows.map { it.id }.toSet()
                            }
                            delay(50)
                        }
                        @Suppress("UNREACHABLE_CODE")
                        error("An earlier writer did not yield before the upload fixture")
                    }
                val notes = koin.get<JournalNotesRepository>()
                val id = Uuid.parse("00000000-0000-0000-0000-000000000029")
                assertNull(notes.getNoteById(id), "The upload fixture cannot replace a retained entry")
                val original = notes.getNoteById(Uuid.parse("00000000-0000-0000-0000-000000000004")) as JournalNote.Image
                val media = koin.get<MediaManager>()
                val expected = digest(media.readMedia(original.mediaRef).data)
                val refs = koin.get<MediaSyncRefStore>()
                assertNull(refs.getScoped(id, scope))
                notes.create(original.copy(uid = id, caption = "Original queued through a policy change"))
                val metadata = koin.get<SyncMetadataService>()
                val pending = metadata.getPendingUploads(EntityType.NOTE).single { it.entityId == id.toString() }
                assertEquals(scope, pending.scope)
                assertNotNull(pending.operationId)
                val armed = File(context.noBackupFilesDir, "native-media-upload-gate-armed")
                check(!armed.exists())

                fun report(marker: String) = instrumentation.sendStatus(0, Bundle().apply { putString("nativeMediaRecovery", marker) })
                report("RESTORE_MEDIA_UPLOAD_READY")
                withTimeout(10_000) { while (!armed.exists()) delay(25) }
                // The sole explicit request accepts this operation; the policy observer must resume it.
                koin.get<SyncManager>().requestBackup()
                val predecessor =
                    withTimeout(30_000) {
                        work()
                            .first { rows -> rows.any { it.id !in prior && it.state == WorkInfo.State.RUNNING } }
                            .single { it.id !in prior && it.state == WorkInfo.State.RUNNING }
                    }
                report("RESTORE_MEDIA_UPLOAD_DEFERRED")
                awaitUnrestrictedNativeNetwork(context, policy, callbackTimeoutMillis = 30_000)
                assertEquals(pending, metadata.getPendingUploads(EntityType.NOTE).single { it.entityId == id.toString() })
                assertNull(refs.getScoped(id, scope), "The held worker must not have uploaded the original yet")
                assertEquals(WorkInfo.State.RUNNING, manager.getWorkInfoByIdFlow(predecessor.id).first()?.state)
                assertTrue(!periodicWriterRunning(), "A periodic writer must not capture the selected worker's post-upload gate")
                report("RESTORE_MEDIA_UPLOAD_POLICY_LIFTED")
                withTimeout(15_000) {
                    work().first { rows ->
                        rows.any { it.id != predecessor.id && it.id !in prior }
                    }
                }
                val uploaded =
                    withTimeout(90_000) {
                        while (true) {
                            val ref = refs.getScoped(id, scope)
                            if (ref != null && metadata.getPendingUploads(EntityType.NOTE).none { it.entityId == id.toString() }) {
                                return@withTimeout ref
                            }
                            delay(50)
                        }
                        @Suppress("UNREACHABLE_CODE")
                        error("The accepted original upload never completed")
                    }
                val current = assertNotNull(sessions.getOriginBoundSession())
                assertEquals(scope, UploadScope(current.session.accountId, current.origin))
                val server = koin.get<CloudMediaDataSource>().downloadMedia(current.session.accessToken, uploaded.mediaId).getOrThrow()
                assertEquals(id, server.contentId)
                assertEquals(expected, digest(server.data), "The real server must retain the exact original bytes")
                val retained = notes.getNoteById(id) as JournalNote.Image
                assertEquals(original.creationTimestamp, retained.creationTimestamp)
                assertEquals(original.lastUpdated, retained.lastUpdated)
                assertEquals(expected, digest(media.readMedia(retained.mediaRef).data))
                assertTrue(metadata.getPendingUploads(EntityType.NOTE).none { it.entityId == id.toString() })
            }
        }

    private fun digest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
