package app.logdate.restore

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import app.logdate.client.data.account.RestoreCredentialCleanupScheduler
import app.logdate.client.data.account.RestoreCredentialLifecycle
import app.logdate.client.datastore.NativeRestorePolicy
import app.logdate.client.datastore.UserSession
import app.logdate.client.device.storage.AndroidSecureStorage
import app.logdate.client.device.storage.SecureSessionStorage
import app.logdate.client.permissions.RestoreCredentialManager
import app.logdate.feature.core.restore.AndroidRestoreCredentialCleanupScheduler
import app.logdate.feature.core.restore.RestoreCredentialCleanupWorker
import app.logdate.shared.config.DefaultLogDateConfigRepository
import app.logdate.shared.model.PasskeyAuthenticationOptions
import app.logdate.shared.model.PasskeyRegistrationOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.uuid.Uuid

@RunWith(AndroidJUnit4::class)
class RestoreCredentialCleanupRuntimeTest {
    @Test
    fun productionWiringSchedulesDurablyBeforeReturning() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            KoinJavaComponent.getKoin().get<RestoreCredentialLifecycle>()
            val ticket = Uuid.random().toString()
            AndroidRestoreCredentialCleanupScheduler(context).enqueue(ticket)
            val work =
                WorkManager
                    .getInstance(context)
                    .getWorkInfosForUniqueWork("restore-credential-cleanup-$ticket")
                    .get(10, TimeUnit.SECONDS)
            assertEquals(1, work.size)
            KoinJavaComponent.getKoin().get<CoroutineScope>()
            withTimeout(30_000) {
                while (true) {
                    val state =
                        WorkManager
                            .getInstance(context)
                            .getWorkInfoById(work.single().id)
                            .get(10, TimeUnit.SECONDS)
                            ?.state
                    if (state != null && state.isFinished) {
                        assertEquals(WorkInfo.State.SUCCEEDED, state)
                        break
                    }
                    delay(100)
                }
            }
        }

    @Test
    fun providerFailureSurvivesStorageRecreationAndWorkerRetry() =
        runBlocking {
            val f = Fixture()
            try {
                val lifecycle = f.lifecycle()
                f.sessions.saveExplicitSession(f.account)
                lifecycle.prepareClear(f.origin) { true }
                f.sessions.clearSession()
                val ticket = assertNotNull(lifecycle.pendingCleanupId())
                f.failClear = true
                assertEquals(ListenableWorker.Result.retry(), f.worker(ticket, lifecycle).doWork())
                val restarted = f.lifecycle()
                restarted.reconcile()
                assertEquals(ticket, f.scheduled.last())
                f.failClear = false
                assertEquals(ListenableWorker.Result.success(), f.worker(ticket, restarted).doWork())
                assertNull(f.lifecycle().pendingCleanupId())
                assertEquals(2, f.clears)
            } finally {
                f.scope.cancel()
            }
        }

    @Test
    fun interruptedLocalSignOutFinishesButLaterLoginSupersedesCleanup() =
        runBlocking {
            val f = Fixture()
            try {
                f.sessions.saveExplicitSession(f.account)
                f.lifecycle().prepareClear(f.origin) { true }
                val interrupted = assertNotNull(f.lifecycle().pendingCleanupId())
                assertEquals(ListenableWorker.Result.success(), f.worker(interrupted, f.lifecycle()).doWork())
                assertNull(f.sessions.getSession())
                assertEquals(1, f.clears)
                f.sessions.saveExplicitSession(f.account)
                f.lifecycle().prepareClear(f.origin) { true }
                val obsolete = assertNotNull(f.lifecycle().pendingCleanupId())
                val newer = f.account.copy(refreshToken = "new-login")
                f.sessions.saveExplicitSession(newer)
                assertEquals(ListenableWorker.Result.success(), f.worker(obsolete, f.lifecycle()).doWork())
                assertEquals(newer, f.sessions.getSession())
                assertEquals(1, f.clears)
            } finally {
                f.scope.cancel()
            }
        }

    @Test
    fun cleanupSurvivesAppProcessRestart(): Unit =
        runBlocking {
            val args = InstrumentationRegistry.getArguments()
            val namespace = args.getString("cleanupProcessNamespace")
            assumeTrue(namespace != null)
            val f = Fixture(requireNotNull(namespace))
            try {
                if (args.getString("cleanupProcessStage") == "seed") {
                    f.sessions.saveExplicitSession(f.account)
                    f.lifecycle().prepareClear(f.origin) { true }
                    f.sessions.clearSession()
                    f.failClear = true
                    val ticket = assertNotNull(f.lifecycle().pendingCleanupId())
                    assertEquals(ListenableWorker.Result.retry(), f.worker(ticket, f.lifecycle()).doWork())
                    assertNotNull(f.lifecycle().pendingCleanupId())
                } else {
                    f.sessions.hasValidSession()
                    assertNull(f.sessions.getSession())
                    val ticket = assertNotNull(f.lifecycle().pendingCleanupId())
                    assertEquals(ListenableWorker.Result.success(), f.worker(ticket, f.lifecycle()).doWork())
                    assertNull(f.lifecycle().pendingCleanupId())
                    assertEquals(1, f.clears)
                }
            } finally {
                f.scope.cancel()
            }
        }

    private class Fixture(
        private val namespace: String = "cleanup-runtime-${Uuid.random()}",
    ) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        private val isolatedContext =
            object : ContextWrapper(context) {
                override fun getApplicationContext(): Context = this

                override fun getSharedPreferences(
                    name: String,
                    mode: Int,
                ): SharedPreferences = super.getSharedPreferences("$namespace-$name", mode)
            }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val origin = "https://cleanup-runtime.example"
        val config = DefaultLogDateConfigRepository(initialBackendUrl = origin)
        private val preferences = isolatedContext.getSharedPreferences("policy", Context.MODE_PRIVATE)
        val policy =
            NativeRestorePolicy(
                { preferences.getString(it, null) },
                { key, value -> check(preferences.edit().putString(key, value).commit()) },
            )
        val sessions = SecureSessionStorage(AndroidSecureStorage(isolatedContext), config, scope, nativeRestorePolicy = policy)
        val account = UserSession("synthetic-access", "synthetic-refresh", "synthetic-owner")
        var failClear = false
        var clears = 0
        val scheduled = mutableListOf<String>()
        private val provider =
            object : RestoreCredentialManager {
                override suspend fun createRestoreKey(options: PasskeyRegistrationOptions) = Result.success("synthetic-key")

                override suspend fun getRestoreCredential(options: PasskeyAuthenticationOptions) = Result.success("synthetic-key")

                override suspend fun clearRestoreCredential(): Result<Unit> {
                    clears++
                    return if (failClear) Result.failure(IllegalStateException("Provider unavailable")) else Result.success(Unit)
                }
            }

        fun lifecycle() =
            RestoreCredentialLifecycle(
                provider,
                AndroidSecureStorage(isolatedContext),
                sessions,
                policy,
                config,
                RestoreCredentialCleanupScheduler { scheduled += it },
            )

        fun worker(
            ticket: String,
            lifecycle: RestoreCredentialLifecycle,
        ): RestoreCredentialCleanupWorker =
            TestListenableWorkerBuilder<RestoreCredentialCleanupWorker>(context)
                .setInputData(workDataOf(RestoreCredentialCleanupWorker.TICKET_ID to ticket))
                .setWorkerFactory(
                    object : WorkerFactory() {
                        override fun createWorker(
                            appContext: Context,
                            workerClassName: String,
                            workerParameters: WorkerParameters,
                        ): ListenableWorker = RestoreCredentialCleanupWorker(appContext, workerParameters, lifecycle)
                    },
                ).build()
    }
}
