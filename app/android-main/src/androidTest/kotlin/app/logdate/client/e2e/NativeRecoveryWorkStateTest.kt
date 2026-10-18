package app.logdate.client.e2e

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Data
import androidx.work.WorkerParameters
import app.logdate.client.datastore.OriginBoundSession
import app.logdate.client.datastore.SessionStorage
import app.logdate.client.datastore.UserSession
import app.logdate.client.device.restore.PostRestoreDetector
import app.logdate.client.device.restore.PostRestoreType
import app.logdate.feature.core.restore.CloudRestoreWorker
import app.logdate.feature.core.restore.NativeRestoreWorkState
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.koin.androidx.workmanager.factory.KoinWorkerFactory
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

@RunWith(AndroidJUnit4::class)
class NativeRecoveryWorkStateTest {
    private val application = ApplicationProvider.getApplicationContext<Context>()
    private val folder = File(application.cacheDir, "native-reconciliation-${Uuid.random()}").apply { mkdirs() }
    private val context =
        object : ContextWrapper(application) {
            override fun getNoBackupFilesDir(): File = folder

            override fun getDatabasePath(name: String): File = File(folder, name)
        }
    private val detector = PostRestoreDetector(context)
    private val sessions = TestSessions()

    @AfterTest
    fun cleanup() {
        folder.deleteRecursively()
    }

    @Test
    fun `successful import acknowledges its native delivery and cannot replay at restart`() =
        runTest {
            detector.recordRestoreCompleted()
            val delivery = assertNotNull(detector.pendingContentRecoveryId())
            val data = NativeRestoreWorkState.bind(Data.Builder(), delivery, sessions.getOriginBoundSession()).build()
            val work = NativeRestoreWorkState(context, data, sessions)
            assertTrue(work.isCurrent())
            work.complete()
            assertFalse(work.isCurrent())
            assertEquals(PostRestoreType.NONE, PostRestoreDetector(context).detect(true))
        }

    @Test
    fun `changed account cannot apply or acknowledge queued native recovery`() =
        runTest {
            detector.recordRestoreCompleted()
            val delivery = assertNotNull(detector.pendingContentRecoveryId())
            val data = NativeRestoreWorkState.bind(Data.Builder(), delivery, sessions.getOriginBoundSession()).build()
            sessions.current = UserSession("synthetic-access", "synthetic-refresh", "another-owner")
            val work = NativeRestoreWorkState(context, data, sessions)
            assertFalse(work.isCurrent())
            work.complete()
            assertEquals(delivery, detector.pendingContentRecoveryId())
        }

    @Test
    fun `a newer delivery cannot be completed by an older queued worker`() =
        runTest {
            detector.recordRestoreCompleted()
            val old = assertNotNull(detector.pendingContentRecoveryId())
            val data = NativeRestoreWorkState.bind(Data.Builder(), old, sessions.getOriginBoundSession()).build()
            context.getDatabasePath("logdate").writeBytes(byteArrayOf(1, 2, 3))
            detector.beginNativeRestoreSession()
            detector.markNativeRestorePayloadReceived(databaseDelivered = true)
            detector.recordRestoreCompleted()
            val current = detector.pendingContentRecoveryId()
            val work = NativeRestoreWorkState(context, data, sessions)
            assertFalse(work.isCurrent())
            work.complete()
            assertEquals(current, detector.pendingContentRecoveryId())
        }

    @Test
    fun `the production dependency graph constructs the cloud restore worker`() {
        val params = mockk<WorkerParameters>(relaxed = true)
        every { params.id } returns Uuid.random().toJavaUuid()
        assertIs<CloudRestoreWorker>(KoinWorkerFactory().createWorker(application, CloudRestoreWorker::class.java.name, params))
    }

    @Test
    fun `queued recovery waits for persisted session to load before checking its owner`() =
        runTest {
            val persistedSession = UserSession("synthetic-access", "synthetic-refresh", "retained-owner")
            val delayedSessions = DelayedSessions(persistedSession)
            val input =
                Data
                    .Builder()
                    .putString(NativeRestoreWorkState.OWNER_KEY, persistedSession.accountId)
                    .putString(NativeRestoreWorkState.ORIGIN_KEY, "https://example.invalid")
                    .build()

            assertTrue(NativeRestoreWorkState(context, input, delayedSessions).isCurrent())
            assertEquals(1, delayedSessions.loadCount)
        }

    @Test
    fun `queued recovery retries while its persisted owner is temporarily unavailable`() =
        runTest {
            val persistedSession = UserSession("synthetic-access", "synthetic-refresh", "retained-owner")
            val delayedSessions = DelayedSessions(persistedSession, canLoad = false)
            val input =
                Data
                    .Builder()
                    .putString(NativeRestoreWorkState.OWNER_KEY, persistedSession.accountId)
                    .putString(NativeRestoreWorkState.ORIGIN_KEY, "https://example.invalid")
                    .build()

            assertEquals(
                NativeRestoreWorkState.Validation.WAITING_FOR_SESSION,
                NativeRestoreWorkState(context, input, delayedSessions).validation(),
            )
            assertEquals(1, delayedSessions.loadCount)
        }

    private class DelayedSessions(
        private val persisted: UserSession,
        private val canLoad: Boolean = true,
    ) : SessionStorage {
        private var loaded: UserSession? = null
        var loadCount = 0
            private set

        override fun getSession() = loaded

        override fun getOriginBoundSession() = loaded?.let { OriginBoundSession("https://example.invalid", it) }

        override fun getSessionFlow() = MutableStateFlow<UserSession?>(loaded)

        override suspend fun hasValidSession(): Boolean {
            loadCount++
            if (canLoad) loaded = persisted
            return canLoad
        }

        override suspend fun saveSession(session: UserSession) {
            loaded = session
        }

        override suspend fun clearSession() {
            loaded = null
        }
    }

    private class TestSessions : SessionStorage {
        var current = UserSession("synthetic-access", "synthetic-refresh", "retained-owner")

        override fun getSession(): UserSession = current

        override suspend fun hasValidSession(): Boolean = true

        override fun getOriginBoundSession() = OriginBoundSession("https://example.invalid", current)

        override fun getSessionFlow() = MutableStateFlow<UserSession?>(current)

        override suspend fun saveSession(session: UserSession) {
            current = session
        }

        override suspend fun clearSession() = Unit
    }
}
