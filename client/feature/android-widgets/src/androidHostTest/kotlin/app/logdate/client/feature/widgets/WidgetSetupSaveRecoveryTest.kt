package app.logdate.client.feature.widgets

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class WidgetSetupSaveRecoveryTest {
    @Test
    fun `persisted refresh with rejected wake retries before completing setup`() =
        runTest {
            var wakeAttempts = 0
            val saved = mutableListOf<String>()
            val retained = mutableListOf<String>()
            val recovery =
                WidgetSetupSaveRecovery<String>(
                    backgroundScope,
                    persist = { choice, _ ->
                        val ready =
                            retainWidgetLifecycleCustody(
                                persist = { retained += choice },
                                schedule = { ++wakeAttempts > 1 },
                                persistedFallbackSupported = true,
                                onFailure = { throw it },
                            )
                        if (ready) WidgetSetupSaveResult.Saved else WidgetSetupSaveResult.Retry
                    },
                    onSaved = saved::add,
                    onFailure = { throw it },
                )
            recovery.setActive(true)
            recovery.submit("chosen widget")
            runCurrent()
            assertEquals(listOf("chosen widget"), retained)
            assertTrue(saved.isEmpty())
            assertEquals("chosen widget", recovery.pending)
            advanceTimeBy(1_000)
            runCurrent()
            assertEquals(2, wakeAttempts)
            assertEquals(listOf("chosen widget"), saved)
            assertNull(recovery.pending)
        }

    @Test
    fun `reused choice values cannot revive an older authorization fence`() =
        runTest {
            val guards = mutableListOf<() -> Boolean>()
            val recovery =
                WidgetSetupSaveRecovery<String>(
                    backgroundScope,
                    persist = { _, canCommit ->
                        guards += canCommit
                        WidgetSetupSaveResult.Retry
                    },
                    onSaved = { error("The write has not completed") },
                    onFailure = { throw it },
                )
            recovery.setActive(true)
            recovery.submit("same")
            runCurrent()
            recovery.submit("same")
            runCurrent()
            assertFalse(guards.first()())
            assertTrue(guards.last()())
            recovery.setActive(false)
            assertFalse(guards.last()())
        }

    @Test
    fun `removed widgets cancel without reporting a successful configuration`() =
        runTest {
            val canceled = mutableListOf<String>()
            val recovery =
                WidgetSetupSaveRecovery<String>(
                    backgroundScope,
                    persist = { _, _ -> WidgetSetupSaveResult.TargetRemoved },
                    onSaved = { error("The widget was removed") },
                    onFailure = { throw it },
                    onCanceled = canceled::add,
                )
            recovery.setActive(true)
            recovery.submit("removed widget")
            runCurrent()
            assertEquals(listOf("removed widget"), canceled)
            assertNull(recovery.pending)
            advanceTimeBy(60_000)
            runCurrent()
            assertEquals(WidgetSetupSaveState.Idle, recovery.state.value)
        }

    @Test
    fun `storage rejection retains the exact choice and retries without another tap`() =
        runTest {
            val saved = mutableListOf<String>()
            var attempts = 0
            val recovery =
                WidgetSetupSaveRecovery<String>(
                    backgroundScope,
                    persist = { _, _ -> if (++attempts == 1) WidgetSetupSaveResult.StorageFull else WidgetSetupSaveResult.Saved },
                    onSaved = saved::add,
                    onFailure = { throw it },
                )
            recovery.setActive(true)
            recovery.submit("selected entry")
            runCurrent()
            assertEquals("selected entry", recovery.pending)
            assertEquals(WidgetSetupSaveState.WaitingForStorage, recovery.state.value)
            assertTrue(saved.isEmpty())
            advanceTimeBy(1_000)
            runCurrent()
            assertEquals(listOf("selected entry"), saved)
            assertNull(recovery.pending)
        }

    @Test
    fun `a request restored while locked waits for authentication before any write`() =
        runTest {
            val saved = mutableListOf<String>()
            val recovery =
                WidgetSetupSaveRecovery<String>(
                    backgroundScope,
                    persist = { _, _ -> WidgetSetupSaveResult.Saved },
                    onSaved = saved::add,
                    onFailure = { throw it },
                )
            recovery.submit("restored choice")
            advanceTimeBy(60_000)
            runCurrent()
            assertTrue(saved.isEmpty())
            assertEquals("restored choice", recovery.pending)
            recovery.setActive(true)
            runCurrent()
            assertEquals(listOf("restored choice"), saved)
        }

    @Test
    fun `leaving for storage settings suspends writes and return retries the retained choice`() =
        runTest {
            var attempts = 0
            val saved = mutableListOf<String>()
            val recovery =
                WidgetSetupSaveRecovery<String>(
                    backgroundScope,
                    persist = { _, _ -> if (++attempts == 1) WidgetSetupSaveResult.StorageFull else WidgetSetupSaveResult.Saved },
                    onSaved = saved::add,
                    onFailure = { throw it },
                )
            recovery.setActive(true)
            recovery.submit("photo shape")
            runCurrent()
            recovery.setActive(false)
            advanceTimeBy(60_000)
            runCurrent()
            assertEquals(1, attempts)
            assertEquals("photo shape", recovery.pending)
            recovery.setActive(true)
            runCurrent()
            assertEquals(listOf("photo shape"), saved)
        }

    @Test
    fun `a replacement choice supersedes a delayed retry without duplicate completion`() =
        runTest {
            val saved = mutableListOf<String>()
            val recovery =
                WidgetSetupSaveRecovery<String>(
                    backgroundScope,
                    persist = { choice, _ -> if (choice == "old") WidgetSetupSaveResult.Retry else WidgetSetupSaveResult.Saved },
                    onSaved = saved::add,
                    onFailure = { throw it },
                )
            recovery.setActive(true)
            recovery.submit("old")
            runCurrent()
            recovery.submit("new")
            runCurrent()
            advanceTimeBy(60_000)
            runCurrent()
            assertEquals(listOf("new"), saved)
        }

    @Test
    fun `editing cancels the old submission and cannot close the changed form`() =
        runTest {
            val saved = mutableListOf<String>()
            val recovery =
                WidgetSetupSaveRecovery<String>(
                    backgroundScope,
                    persist = { _, _ -> WidgetSetupSaveResult.Retry },
                    onSaved = saved::add,
                    onFailure = { throw it },
                )
            recovery.setActive(true)
            recovery.submit("old")
            runCurrent()
            recovery.cancel()
            advanceTimeBy(60_000)
            runCurrent()
            assertNull(recovery.pending)
            assertTrue(saved.isEmpty())
            assertEquals(WidgetSetupSaveState.Idle, recovery.state.value)
        }

    @Test
    fun `an interrupted write finishes before a newer submission writes its replacement`() =
        runTest {
            val release = CompletableDeferred<Unit>()
            val writes = mutableListOf<String>()
            val saved = mutableListOf<String>()
            val recovery =
                WidgetSetupSaveRecovery<String>(
                    backgroundScope,
                    persist = { choice, _ ->
                        if (choice == "old") withContext(NonCancellable) { release.await() }
                        writes += choice
                        WidgetSetupSaveResult.Saved
                    },
                    onSaved = saved::add,
                    onFailure = { throw it },
                )
            recovery.setActive(true)
            recovery.submit("old")
            runCurrent()
            recovery.submit("new")
            runCurrent()
            assertTrue(writes.isEmpty())
            release.complete(Unit)
            runCurrent()
            assertEquals(listOf("old", "new"), writes)
            assertEquals(listOf("new"), saved)
        }

    @Test
    fun `an IO failure keeps the submission pending and retries automatically`() =
        runTest {
            val saved = mutableListOf<String>()
            val errors = mutableListOf<Exception>()
            var attempts = 0
            val recovery =
                WidgetSetupSaveRecovery<String>(
                    backgroundScope,
                    persist = { _, _ -> if (++attempts == 1) throw IOException("Storage unavailable") else WidgetSetupSaveResult.Saved },
                    onSaved = saved::add,
                    onFailure = errors::add,
                )
            recovery.setActive(true)
            recovery.submit("entry")
            runCurrent()
            assertEquals("entry", recovery.pending)
            advanceTimeBy(1_000)
            runCurrent()
            assertEquals(listOf("entry"), saved)
            assertEquals(1, errors.size)
        }
}
