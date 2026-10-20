package app.logdate.client.e2e

import android.content.Context
import android.content.pm.ApplicationInfo
import android.net.ConnectivityManager
import android.os.Build
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.logdate.client.datastore.LogdatePreferencesDataSource
import app.logdate.client.datastore.OriginBoundSession
import app.logdate.client.datastore.SessionStorage
import app.logdate.client.datastore.UserSession
import app.logdate.client.networking.DataRestriction
import app.logdate.client.networking.DataUsageMode
import app.logdate.client.networking.DataUsagePolicy
import app.logdate.client.networking.NetworkAvailabilityMonitor
import app.logdate.client.networking.NetworkState
import app.logdate.client.sync.AndroidLogDateSyncWorker
import app.logdate.client.sync.AndroidSyncManager
import app.logdate.client.sync.DefaultSyncManager
import app.logdate.client.sync.SyncStatus
import app.logdate.shared.config.DefaultLogDateConfigRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Opt-in, offline synthetic fixture; never reads or replaces the application's real session. */
@RunWith(AndroidJUnit4::class)
class AndroidSyncAccountTransitionRuntimeTest {
    @Test
    fun directAccountSwitchReplacesQueuedSyncButTokenRefreshDoesNot() =
        runBlocking {
            withOwnedSyncWork { fixture ->
                val initial = fixture.awaitInitialWork()
                fixture.sessions.current.value =
                    fixture.sessions.current.value!!
                        .copy(accountId = "synthetic-owner-b")

                val replacement = fixture.awaitReplacement(initial.immediate)
                fixture.assertPeriodicId(initial.periodic)
                fixture.assertOldRequestFinished(initial.immediate)

                fixture.sessions.current.value =
                    fixture.sessions.current.value!!
                        .copy(accessToken = "refreshed-access")
                fixture.assertImmediateIdRemains(replacement)
                fixture.assertPeriodicId(initial.periodic)
            }
        }

    @Test
    fun backendSwitchWithUnchangedSessionCreatesNewQueuedSync() =
        runBlocking {
            withOwnedSyncWork { fixture ->
                val initial = fixture.awaitInitialWork()
                val unchangedSession = fixture.sessions.current.value
                fixture.sessions.origin = SECOND_ORIGIN
                fixture.config.updateBackendUrl(SECOND_ORIGIN)

                fixture.awaitReplacement(initial.immediate)
                assertTrue(fixture.sessions.current.value === unchangedSession, "The session flow must not emit a change")
                fixture.assertPeriodicId(initial.periodic)
                fixture.assertOldRequestFinished(initial.immediate)
            }
        }

    @Test
    fun interruptedAccountSchedulingRetriesWithoutAnotherAuthenticationEvent() =
        runBlocking {
            withOwnedSyncWork { fixture ->
                val initial = fixture.awaitInitialWork()
                val attempts = fixture.failNextBackoffRelease()
                val replacementSession =
                    fixture.sessions.current.value!!
                        .copy(accountId = "synthetic-owner-b")
                fixture.sessions.current.value = replacementSession

                val replacement = fixture.awaitReplacement(initial.immediate)
                assertTrue(attempts.get() >= 2, "The interrupted scheduling step must retry without another flow event")
                assertTrue(fixture.sessions.current.value === replacementSession, "Authentication must remain unchanged during retry")
                fixture.assertOldRequestFinished(initial.immediate)
                fixture.assertPeriodicId(initial.periodic)
                fixture.assertImmediateIdRemains(replacement)
            }
        }

    @Test
    fun failedRunningPredecessorDoesNotFailTheNewAccountRequest() =
        runBlocking {
            assertRunningPredecessorCannotBlockNewOwner(ListenableWorker.Result.failure())
        }

    @Test
    fun retryingRunningPredecessorDoesNotBlockTheNewAccountRequest() =
        runBlocking {
            assertRunningPredecessorCannotBlockNewOwner(ListenableWorker.Result.retry())
        }

    @Test
    fun failedRunningPredecessorDoesNotLoseSameAccountSignIn() =
        runBlocking {
            assertRunningPredecessorCannotBlockReauthentication(ListenableWorker.Result.failure())
        }

    @Test
    fun retryingRunningPredecessorDoesNotDelaySameAccountSignIn() =
        runBlocking {
            assertRunningPredecessorCannotBlockReauthentication(ListenableWorker.Result.retry())
        }

    private suspend fun assertRunningPredecessorCannotBlockReauthentication(result: ListenableWorker.Result) {
        withOwnedSyncWork { fixture ->
            fixture.awaitInitialWork()
            val sameAccount = requireNotNull(fixture.sessions.current.value)
            fixture.signOutAndAwaitDisabled()
            val gate = AccountTransitionWorkerGate()
            AccountTransitionGateWorker.gate = gate
            try {
                val predecessor = fixture.startRunningPredecessor(gate)
                fixture.sessions.current.value = sameAccount.copy(accessToken = "signed-in-access", refreshToken = "signed-in-refresh")
                val successor = fixture.awaitEnlistedSuccessor(predecessor)
                gate.release.complete(result)
                fixture.assertIndependentSuccessor(predecessor, successor)
                fixture.assertImmediateIdRemains(successor)
            } finally {
                gate.release.complete(ListenableWorker.Result.failure())
                AccountTransitionGateWorker.gate = null
            }
        }
    }

    private suspend fun assertRunningPredecessorCannotBlockNewOwner(result: ListenableWorker.Result) {
        withOwnedSyncWork { fixture ->
            val initial = fixture.awaitInitialWork()
            val gate = AccountTransitionWorkerGate()
            AccountTransitionGateWorker.gate = gate
            try {
                val predecessor = fixture.startRunningPredecessor(gate)
                fixture.sessions.current.value =
                    fixture.sessions.current.value!!
                        .copy(accountId = "synthetic-owner-b")
                val successor = fixture.awaitEnlistedSuccessor(predecessor)
                gate.release.complete(result)
                fixture.assertIndependentSuccessor(predecessor, successor)
                fixture.assertPeriodicId(initial.periodic)
            } finally {
                gate.release.complete(ListenableWorker.Result.failure())
                AccountTransitionGateWorker.gate = null
            }
        }
    }

    private suspend fun withOwnedSyncWork(block: suspend (AccountTransitionFixture) -> Unit) {
        val context = requireOfflineFixture()
        val workManager = WorkManager.getInstance(context)
        WORK_NAMES.forEach { name ->
            check(workManager.getWorkInfosForUniqueWork(name).get(10, TimeUnit.SECONDS).none { !it.state.isFinished }) {
                "Fixture does not own existing unfinished work: $name"
            }
        }
        val fixture = AccountTransitionFixture(context, workManager)
        try {
            block(fixture)
        } finally {
            fixture.manager.cancel()
            WORK_NAMES.forEach { workManager.cancelUniqueWork(it).result.get(10, TimeUnit.SECONDS) }
        }
    }

    private fun requireOfflineFixture(): Context {
        assumeTrue(InstrumentationRegistry.getArguments().getString("syncAccountTransitions") == "true")
        check(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish") { "Owned emulator required" }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) { "Debug fixture required" }
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        check(connectivity.activeNetwork == null) { "Host must hold the owned emulator offline before this test" }
        return context
    }
}

private class AccountTransitionFixture(
    context: Context,
    private val workManager: WorkManager,
) {
    val sessions = AccountTransitionSessions()
    val config = DefaultLogDateConfigRepository(initialBackendUrl = FIRST_ORIGIN)
    private val defaultManager =
        mockk<DefaultSyncManager> {
            every { syncStatusFlow } returns MutableStateFlow(SyncStatus(true, null, 0, false, false))
            coEvery { releaseUploadBackoff() } returns Unit
        }
    val manager =
        AndroidSyncManager(
            applicationContext = context,
            defaultSyncManager = defaultManager,
            sessionStorage = sessions,
            preferencesDataSource = mockk<LogdatePreferencesDataSource>(),
            networkMonitor = EmptyTransitionNetworkMonitor(),
            dataUsagePolicy = TransitionDataUsagePolicy(),
            configRepository = config,
        )

    suspend fun awaitInitialWork(): TransitionWorkIds {
        awaitCondition("One immediate and one periodic request should be queued for account A") {
            unfinished(IMMEDIATE).size == 1 && unfinished(PERIODIC).size == 1
        }
        val immediate = unfinished(IMMEDIATE).single()
        val periodic = unfinished(PERIODIC).single()
        assertEquals(WorkInfo.State.ENQUEUED, immediate.state)
        assertEquals(WorkInfo.State.ENQUEUED, periodic.state)
        return TransitionWorkIds(immediate.id, periodic.id)
    }

    fun failNextBackoffRelease(): AtomicInteger {
        val attempts = AtomicInteger()
        coEvery { defaultManager.releaseUploadBackoff() } coAnswers {
            if (attempts.incrementAndGet() == 1) throw IOException("Synthetic recovery checkpoint storage interruption")
            Unit
        }
        return attempts
    }

    suspend fun signOutAndAwaitDisabled() {
        sessions.current.value = null
        awaitCondition("Signing out must retire periodic sync before reauthentication") { unfinished(PERIODIC).isEmpty() }
    }

    suspend fun awaitReplacement(previous: UUID): UUID {
        awaitCondition("A direct authenticated scope change must replace the pending immediate request") {
            unfinished(IMMEDIATE).let { it.size == 1 && it.single().id != previous }
        }
        return unfinished(IMMEDIATE)
            .single()
            .also {
                assertEquals(WorkInfo.State.ENQUEUED, it.state)
                assertNotEquals(previous, it.id)
            }.id
    }

    suspend fun startRunningPredecessor(gate: AccountTransitionWorkerGate): UUID {
        val request = OneTimeWorkRequestBuilder<AccountTransitionGateWorker>().build()
        workManager.enqueueUniqueWork(IMMEDIATE, ExistingWorkPolicy.REPLACE, request).result.get(10, TimeUnit.SECONDS)
        withTimeout(10_000) { gate.started.await() }
        awaitCondition("The networkless predecessor must actually reach RUNNING") {
            workManager.getWorkInfoById(request.id).get(10, TimeUnit.SECONDS)?.state == WorkInfo.State.RUNNING
        }
        return request.id
    }

    suspend fun awaitEnlistedSuccessor(predecessor: UUID): UUID {
        awaitCondition("The new owner must receive a distinct immediate request while the old writer runs") {
            unfinished(IMMEDIATE).any { it.id != predecessor }
        }
        return unfinished(IMMEDIATE).single { it.id != predecessor }.id
    }

    suspend fun assertIndependentSuccessor(
        predecessor: UUID,
        successor: UUID,
    ) {
        awaitCondition("The released predecessor must stop running") {
            workManager.getWorkInfoById(predecessor).get(10, TimeUnit.SECONDS)?.state != WorkInfo.State.RUNNING
        }
        val replacement = workManager.getWorkInfoById(successor).get(10, TimeUnit.SECONDS)
        assertEquals(WorkInfo.State.ENQUEUED, replacement?.state, "Old-owner failure/backoff must not propagate to new-owner work")
        val old = workManager.getWorkInfoById(predecessor).get(10, TimeUnit.SECONDS)
        assertTrue(old == null || old.state == WorkInfo.State.CANCELLED, "Scope replacement must retire the old owner's request")
        assertEquals(listOf(successor), unfinished(IMMEDIATE).map { it.id })
    }

    fun assertPeriodicId(expected: UUID) {
        val request = unfinished(PERIODIC).single()
        assertEquals(expected, request.id, "UPDATE must preserve the existing periodic request UUID")
        assertEquals(WorkInfo.State.ENQUEUED, request.state)
    }

    fun assertOldRequestFinished(previous: UUID) {
        val old = workManager.getWorkInfoById(previous).get(10, TimeUnit.SECONDS)
        assertTrue(old == null || old.state.isFinished, "The superseded pending request must not remain active")
    }

    suspend fun assertImmediateIdRemains(expected: UUID) {
        val deadline = SystemClock.elapsedRealtime() + 1000
        while (SystemClock.elapsedRealtime() < deadline) {
            val request = unfinished(IMMEDIATE).single()
            assertEquals(expected, request.id, "Refreshing the same owner's token must not enqueue another sync")
            assertEquals(WorkInfo.State.ENQUEUED, request.state)
            delay(25)
        }
    }

    private fun unfinished(name: String): List<WorkInfo> =
        workManager.getWorkInfosForUniqueWork(name).get(10, TimeUnit.SECONDS).filterNot { it.state.isFinished }

    private suspend fun awaitCondition(
        description: String,
        condition: () -> Boolean,
    ) {
        val deadline = SystemClock.elapsedRealtime() + 10_000
        while (!condition()) {
            check(SystemClock.elapsedRealtime() < deadline) { description }
            delay(25)
        }
    }
}

private data class TransitionWorkIds(
    val immediate: UUID,
    val periodic: UUID,
)

internal class AccountTransitionWorkerGate {
    val started = CompletableDeferred<Unit>()
    val release = CompletableDeferred<ListenableWorker.Result>()
}

/** Public so WorkManager's normal reflection factory can construct this instrumentation-only worker. */
class AccountTransitionGateWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val snapshot = checkNotNull(gate) { "An opted-in test must own this worker's gate" }
        snapshot.started.complete(Unit)
        return snapshot.release.await()
    }

    companion object {
        @Volatile
        internal var gate: AccountTransitionWorkerGate? = null
    }
}

private class AccountTransitionSessions : SessionStorage {
    val current = MutableStateFlow<UserSession?>(UserSession("synthetic-access", "synthetic-refresh", "synthetic-owner-a"))

    @Volatile
    var origin = FIRST_ORIGIN

    @Volatile
    private var hydratedOrigin = FIRST_ORIGIN

    override fun getSession(): UserSession? = current.value

    override fun getOriginBoundSession(): OriginBoundSession? =
        current.value?.takeIf { hydratedOrigin == origin }?.let { OriginBoundSession(origin, it) }

    override fun getSessionFlow(): StateFlow<UserSession?> = current

    override suspend fun hasValidSession(): Boolean {
        hydratedOrigin = origin
        return current.value != null
    }

    override suspend fun saveSession(session: UserSession) {
        current.value = session
    }

    override suspend fun clearSession() {
        current.value = null
    }
}

private class EmptyTransitionNetworkMonitor : NetworkAvailabilityMonitor {
    private val states = MutableSharedFlow<NetworkState>()

    override fun isNetworkAvailable(): Boolean = false

    override fun observeNetwork() = states
}

private class TransitionDataUsagePolicy : DataUsagePolicy {
    override val policy = MutableStateFlow<DataUsageMode>(DataUsageMode.Unrestricted)

    override suspend fun currentMode(): DataUsageMode = policy.value

    override suspend fun currentRestriction(): DataRestriction = DataRestriction.OFFLINE
}

private const val FIRST_ORIGIN = "https://sync-transition-a.example"
private const val SECOND_ORIGIN = "https://sync-transition-b.example"
private const val IMMEDIATE = AndroidLogDateSyncWorker.WORK_NAME_IMMEDIATE_SYNC
private const val PERIODIC = AndroidLogDateSyncWorker.WORK_NAME_PERIODIC_SYNC
private val WORK_NAMES = listOf(IMMEDIATE, PERIODIC)
