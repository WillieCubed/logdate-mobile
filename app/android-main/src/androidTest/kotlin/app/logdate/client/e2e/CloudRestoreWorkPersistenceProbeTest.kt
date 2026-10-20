package app.logdate.client.e2e

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import app.logdate.client.datastore.SessionStorage
import app.logdate.client.datastore.UserSession
import app.logdate.client.device.restore.PostRestoreDetector
import app.logdate.client.ensureLogDateApplicationStarted
import app.logdate.feature.core.restore.CloudRestoreScheduler
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Host-driven probe proving the production cloud-restore request survives app process death. */
@RunWith(AndroidJUnit4::class)
class CloudRestoreWorkPersistenceProbeTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val workNameFile get() = File(context.noBackupFilesDir, "cloud-restore-work-probe")
    private val workIdFile get() = File(context.noBackupFilesDir, "cloud-restore-work-id-probe")
    private val deliveryIdFile get() = File(context.noBackupFilesDir, "cloud-restore-delivery-probe")

    @Test
    fun cloudRestoreWorkSurvivesProcessDeath(): Unit =
        runBlocking {
            val arguments = InstrumentationRegistry.getArguments()
            val mode = arguments.getString("cloudRestoreWorkMode")
            assertTrue(mode in setOf("enqueue", "verify", "cleanup"), "Host acceptance mode is required")
            assertTrue(context.packageName.endsWith(".debug"), "Isolated debug application required")
            if (mode == "enqueue") {
                val detector = PostRestoreDetector(context)
                assertNull(detector.pendingContentRecoveryId(), "Use a fresh isolated native recovery fixture")
                detector.beginNativeRestoreSession()
                detector.markNativeRestorePayloadReceived(databaseDelivered = false)
                detector.recordRestoreCompleted()
                deliveryIdFile.writeText(assertNotNull(detector.pendingContentRecoveryId()))
            }
            val application = context.applicationContext as android.app.Application
            ensureLogDateApplicationStarted(application)
            val sessions = GlobalContext.get().get<SessionStorage>()
            val workManager = WorkManager.getInstance(context)

            when (mode) {
                "enqueue" -> enqueueOfflineRestore(sessions, workManager)
                "verify" -> verifyAfterProcessRestart(sessions, workManager)
                "cleanup" -> cleanup(sessions, workManager)
            }
        }

    private suspend fun enqueueOfflineRestore(
        sessions: SessionStorage,
        workManager: WorkManager,
    ) {
        assertNull(sessions.getSession(), "The isolated app must start without a session")
        sessions.saveExplicitSession(
            UserSession(
                accessToken = "synthetic-restore-work-access",
                refreshToken = "synthetic-restore-work-refresh",
                accountId = "synthetic-restore-work-owner",
            ),
        )
        val session = assertNotNull(sessions.getOriginBoundSession())
        val delivery = assertNotNull(PostRestoreDetector(context).pendingContentRecoveryId())
        assertEquals(deliveryIdFile.readText(), delivery, "Startup must retain the unresolved native delivery")
        val expectedName = CloudRestoreScheduler.workName(session, delivery)
        CloudRestoreScheduler(context, sessions).enqueueRestore()
        val request = awaitSinglePending(workManager, expectedName)
        assertEquals(WorkInfo.State.ENQUEUED, request.state, "Offline work must remain queued")
        assertEquals(NetworkType.CONNECTED, request.constraints.requiredNetworkType, "Metered networks must remain eligible")
        assertTrue(request.constraints.requiresStorageNotLow(), "Cloud archive download must wait for usable storage")
        workNameFile.writeText(expectedName)
        workIdFile.writeText(request.id.toString())
    }

    private suspend fun verifyAfterProcessRestart(
        sessions: SessionStorage,
        workManager: WorkManager,
    ) {
        val expectedName = assertNotNull(workNameFile.takeIf(File::isFile)?.readText())
        val expectedWorkId = assertNotNull(workIdFile.takeIf(File::isFile)?.readText())
        val expectedDelivery = assertNotNull(deliveryIdFile.takeIf(File::isFile)?.readText())
        assertTrue(sessions.hasValidSession(), "The synthetic session must load from secure storage after process death")
        val session = assertNotNull(sessions.getOriginBoundSession(), "The synthetic session must survive process death")
        val delivery = assertNotNull(PostRestoreDetector(context).pendingContentRecoveryId())
        assertEquals(expectedDelivery, delivery, "Process death must not dismiss unresolved native recovery")
        assertEquals(expectedName, CloudRestoreScheduler.workName(session, delivery))
        val before = awaitSinglePending(workManager, expectedName)
        assertEquals(expectedWorkId, before.id.toString(), "The original request must survive process death")
        assertEquals(WorkInfo.State.ENQUEUED, before.state, "The offline request must remain queued after restart")

        CloudRestoreScheduler(context, sessions).enqueueRestore()

        val after = awaitSinglePending(workManager, expectedName)
        assertEquals(before.id, after.id, "Retrying enqueue after process death must retain the pending work")
        assertEquals(expectedDelivery, PostRestoreDetector(context).pendingContentRecoveryId())
    }

    private suspend fun awaitSinglePending(
        workManager: WorkManager,
        uniqueName: String,
    ): WorkInfo =
        withTimeout(TimeUnit.SECONDS.toMillis(20)) {
            while (true) {
                val active =
                    workManager
                        .getWorkInfosForUniqueWork(uniqueName)
                        .get()
                        .filterNot { it.state.isFinished }
                if (active.size == 1) return@withTimeout active.single()
                delay(100)
            }
            error("Unreachable")
        }

    private suspend fun cleanup(
        sessions: SessionStorage,
        workManager: WorkManager,
    ) {
        workNameFile.takeIf(File::isFile)?.readText()?.let { uniqueName ->
            workManager.cancelUniqueWork(uniqueName).result.get(10, TimeUnit.SECONDS)
        }
        sessions.clearSession()
        assertNull(sessions.getSession(), "The synthetic session must be removed")
        workNameFile.delete()
        workIdFile.delete()
        deliveryIdFile.delete()
    }
}
