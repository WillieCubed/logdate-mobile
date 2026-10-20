package app.logdate.client.e2e

import android.content.Context
import android.content.pm.ApplicationInfo
import android.net.ConnectivityManager
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ListenableWorker
import androidx.work.WorkManager
import app.logdate.client.networking.DataUsageMode
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit
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

    @Test
    fun policyLiftResumesMediaAfterRunningWorkRetries() = runBlocking { assertPolicyLiftResumesMedia(ListenableWorker.Result.retry()) }

    @Test
    fun successfulPartialWorkCannotDiscardMediaReadiness() = runBlocking { assertPolicyLiftResumesMedia(ListenableWorker.Result.success()) }

    private suspend fun assertPolicyLiftResumesMedia(result: ListenableWorker.Result) {
        withOwnedSyncWork(initialMode = DataUsageMode.Conservative) { fixture ->
            val initial = fixture.awaitInitialWork()
            val gate = AccountTransitionWorkerGate()
            AccountTransitionGateWorker.gate = gate
            try {
                val predecessor = fixture.startRunningPredecessor(gate)
                fixture.policy.policy.value = DataUsageMode.Unrestricted
                withTimeout(10_000) { fixture.policy.unrestrictedObserved.await() }
                fixture.assertRunning(predecessor)
                gate.release.complete(result)
                val replacement = fixture.awaitReplacement(predecessor)
                fixture.assertImmediateIdRemains(replacement)
                fixture.assertPeriodicId(initial.periodic)
            } finally {
                gate.release.complete(ListenableWorker.Result.failure())
                AccountTransitionGateWorker.gate = null
            }
        }
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

    private suspend fun withOwnedSyncWork(
        initialMode: DataUsageMode = DataUsageMode.Unrestricted,
        block: suspend (AccountTransitionFixture) -> Unit,
    ) {
        val context = requireOfflineFixture()
        val workManager = WorkManager.getInstance(context)
        WORK_NAMES.forEach { name ->
            check(workManager.getWorkInfosForUniqueWork(name).get(10, TimeUnit.SECONDS).none { !it.state.isFinished }) {
                "Fixture does not own existing unfinished work: $name"
            }
        }
        val fixture = AccountTransitionFixture(context, workManager, initialMode)
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
