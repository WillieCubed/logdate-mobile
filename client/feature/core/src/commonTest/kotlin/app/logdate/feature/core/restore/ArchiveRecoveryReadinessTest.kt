package app.logdate.feature.core.restore

import app.logdate.shared.model.diagnostics.DiagnosticReason
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ArchiveRecoveryReadinessTest {
    @Test
    fun `network permission changes wake retained recovery without new credentials`() =
        runTest {
            val network = MutableStateFlow<Any>("metered")
            var attempts = 0
            val changedStates = mutableListOf<ArchiveRecoveryReadinessChange>()
            backgroundScope.launch {
                observeArchiveRecoveryReadiness(
                    MutableStateFlow("origin"),
                    MutableStateFlow("session"),
                    networkChanges = network,
                ) { changed ->
                    attempts++
                    changedStates += changed
                }
            }
            runCurrent()
            val deferred = attempts
            network.value = "unrestricted"
            runCurrent()
            assertEquals(deferred + 1, attempts)
            assertEquals(
                listOf(
                    ArchiveRecoveryReadinessChange(),
                    ArchiveRecoveryReadinessChange(restartRunningWork = true),
                ),
                changedStates,
            )
            assertTrue(DiagnosticReason.NETWORK_POLICY.shouldRetryArchiveMedia())
        }

    @Test
    fun `media inventory changes do not cancel useful running recovery`() =
        runTest {
            val inventory = MutableStateFlow(0L)
            val changes = mutableListOf<ArchiveRecoveryReadinessChange>()
            backgroundScope.launch {
                observeArchiveRecoveryReadiness(
                    MutableStateFlow("origin"),
                    MutableStateFlow("session"),
                    inventory = inventory,
                ) { changes += it }
            }
            runCurrent()
            inventory.value = 1L
            runCurrent()

            assertEquals(
                listOf(
                    ArchiveRecoveryReadinessChange(),
                    ArchiveRecoveryReadinessChange(mediaInventoryChanged = true),
                ),
                changes,
            )
        }

    @Test
    fun `cancelled readiness enqueue retains its hard change for the next event`() =
        runTest {
            val session = MutableStateFlow("session-1")
            val inventory = MutableStateFlow(0L)
            val changes = mutableListOf<ArchiveRecoveryReadinessChange>()
            val hardEnqueueStarted = CompletableDeferred<Unit>()
            var holdHardEnqueue = true
            backgroundScope.launch {
                observeArchiveRecoveryReadiness(
                    MutableStateFlow("origin"),
                    session,
                    inventory = inventory,
                ) { change ->
                    changes += change
                    if (change.restartRunningWork && holdHardEnqueue) {
                        holdHardEnqueue = false
                        hardEnqueueStarted.complete(Unit)
                        CompletableDeferred<Unit>().await()
                    }
                }
            }
            runCurrent()

            session.value = "session-2"
            runCurrent()
            assertTrue(hardEnqueueStarted.isCompleted)

            inventory.value = 1L
            runCurrent()

            assertEquals(
                ArchiveRecoveryReadinessChange(restartRunningWork = true, mediaInventoryChanged = true),
                changes.last(),
            )
        }

    @Test
    fun `change during the initial enqueue retains the new readiness state`() =
        runTest {
            val session = MutableStateFlow("session-1")
            val initialEnqueueStarted = CompletableDeferred<Unit>()
            val changes = mutableListOf<ArchiveRecoveryReadinessChange>()
            var holdInitialEnqueue = true
            backgroundScope.launch {
                observeArchiveRecoveryReadiness(
                    MutableStateFlow("origin"),
                    session,
                ) { change ->
                    changes += change
                    if (holdInitialEnqueue) {
                        holdInitialEnqueue = false
                        initialEnqueueStarted.complete(Unit)
                        CompletableDeferred<Unit>().await()
                    }
                }
            }
            runCurrent()
            assertTrue(initialEnqueueStarted.isCompleted)

            session.value = "session-2"
            runCurrent()

            assertEquals(ArchiveRecoveryReadinessChange(), changes.first())
            assertEquals(ArchiveRecoveryReadinessChange(restartRunningWork = true), changes.last())
        }

    @Test
    fun `local persistence retries without requiring Cloud sign in`() {
        assertTrue(DiagnosticReason.LOCAL_STORAGE.shouldRetryArchiveMedia(hasSession = false))
        assertTrue(DiagnosticReason.PERSISTENCE_FAILED.shouldRetryArchiveMedia(hasSession = false))
        assertFalse(DiagnosticReason.UNKNOWN.shouldRetryArchiveMedia(hasSession = false))
        assertFalse(DiagnosticReason.SIGN_IN_REQUIRED.shouldRetryArchiveMedia(hasSession = false))
    }

    @Test
    fun `recovered keys wake retained originals without a new session or media mapping`() =
        runTest {
            val keys = MutableSharedFlow<Unit>()
            var attempts = 0
            backgroundScope.launch {
                observeArchiveRecoveryReadiness(
                    MutableStateFlow("a"),
                    MutableStateFlow("same-session"),
                    identityChanges = keys,
                ) { _ -> attempts++ }
            }
            runCurrent()
            val beforeRecovery = attempts
            keys.emit(Unit)
            runCurrent()
            assertEquals(beforeRecovery + 1, attempts)
        }

    @Test
    fun `changed durable mapping wakes recovery without a session or configuration change`() =
        runTest {
            val inventory = MutableStateFlow(0L)
            var attempts = 0
            backgroundScope.launch {
                observeArchiveRecoveryReadiness(
                    MutableStateFlow("a"),
                    MutableStateFlow("same-session"),
                    inventory = inventory,
                ) { _ -> attempts++ }
            }
            runCurrent()
            val beforeRecovery = attempts
            inventory.value = 2L
            runCurrent()
            assertEquals(beforeRecovery + 1, attempts)
        }

    @Test
    fun `readiness changes queue only one successor while an enqueue commit is suspended`() =
        runTest {
            val gate = ArchiveRecoveryScheduleGate()
            val release = CompletableDeferred<Unit>()
            var waiting = false
            var enqueues = 0
            val first =
                launch {
                    gate.enqueue(change = ArchiveRecoveryReadinessChange(), hasQueuedWork = { waiting }, hasRunningWork = { false }) {
                        enqueues++
                        release.await()
                        waiting = true
                    }
                }
            runCurrent()
            val second =
                launch {
                    gate.enqueue(change = ArchiveRecoveryReadinessChange(), hasQueuedWork = { waiting }, hasRunningWork = { false }) {
                        enqueues++
                        waiting = true
                    }
                }
            runCurrent()
            release.complete(Unit)
            first.join()
            second.join()
            assertEquals(1, enqueues)
            waiting = false
            gate.enqueue(change = ArchiveRecoveryReadinessChange(), hasQueuedWork = { waiting }, hasRunningWork = { false }) {
                enqueues++
                waiting = true
            }
            assertEquals(2, enqueues)
        }

    @Test
    fun `readiness changes replace a delayed retry without duplicating queued or running work`() =
        runTest {
            val gate = ArchiveRecoveryScheduleGate()
            var delayedRetry = true
            var waiting = true
            var replacements = 0
            var enqueues = 0

            gate.enqueue(
                change = ArchiveRecoveryReadinessChange(restartRunningWork = true),
                hasQueuedWork = { waiting },
                hasRunningWork = { false },
                enqueueSuccessor = { enqueues++ },
                isDelayedRetry = { delayedRetry },
                replaceDelayedRetry = {
                    replacements++
                    delayedRetry = false
                },
            )
            gate.enqueue(
                change = ArchiveRecoveryReadinessChange(restartRunningWork = true),
                hasQueuedWork = { waiting },
                hasRunningWork = { false },
                enqueueSuccessor = { enqueues++ },
                isDelayedRetry = { delayedRetry },
                replaceDelayedRetry = { replacements++ },
            )

            assertEquals(1, replacements)
            assertEquals(0, enqueues)
            waiting = false
            gate.enqueue(
                change = ArchiveRecoveryReadinessChange(restartRunningWork = true),
                hasQueuedWork = { waiting },
                hasRunningWork = { false },
                enqueueSuccessor = { enqueues++ },
                isDelayedRetry = { delayedRetry },
                replaceDelayedRetry = { replacements++ },
            )
            assertEquals(1, enqueues)
        }

    @Test
    fun `startup leaves running recovery alone but new readiness replaces its retry chain`() =
        runTest {
            val gate = ArchiveRecoveryScheduleGate()
            val running = true
            var queued = true
            var enqueues = 0
            var replacements = 0

            suspend fun schedule(change: ArchiveRecoveryReadinessChange) =
                gate.enqueue(
                    change = change,
                    hasQueuedWork = { queued },
                    hasRunningWork = { running },
                    replaceRunningWork = {
                        replacements++
                        queued = false
                    },
                    enqueueSuccessor = {
                        enqueues++
                        queued = true
                    },
                )

            schedule(ArchiveRecoveryReadinessChange())
            assertEquals(0, enqueues, "Startup must not queue a duplicate behind active work")
            assertEquals(0, replacements)
            schedule(ArchiveRecoveryReadinessChange(restartRunningWork = true))
            schedule(ArchiveRecoveryReadinessChange(restartRunningWork = true))
            assertEquals(2, replacements, "Each later readiness change must interrupt stale work and clear its chain")
            assertEquals(0, enqueues)
        }

    @Test
    fun `inventory readiness queues one follow-up without replacing running recovery`() =
        runTest {
            val gate = ArchiveRecoveryScheduleGate()
            var queued = false
            var enqueues = 0
            var replacements = 0

            suspend fun schedule() =
                gate.enqueue(
                    change = ArchiveRecoveryReadinessChange(mediaInventoryChanged = true),
                    hasQueuedWork = { queued },
                    hasRunningWork = { true },
                    replaceRunningWork = { replacements++ },
                    enqueueSuccessor = {
                        enqueues++
                        queued = true
                    },
                )

            schedule()
            schedule()

            assertEquals(0, replacements)
            assertEquals(1, enqueues)
        }

    @Test
    fun `startup coalesces queued work while no active recovery is running`() =
        runTest {
            val gate = ArchiveRecoveryScheduleGate()
            var queued = true
            var enqueues = 0
            var replacements = 0

            gate.enqueue(
                change = ArchiveRecoveryReadinessChange(restartRunningWork = true),
                hasQueuedWork = { queued },
                hasRunningWork = { false },
                replaceRunningWork = { replacements++ },
                enqueueSuccessor = { enqueues++ },
            )

            assertEquals(0, enqueues)
            assertEquals(0, replacements)
        }

    @Test
    fun `a delayed retry can be replaced only when no worker is running`() {
        val delayed = ArchiveRecoveryWorkState(ArchiveRecoveryWorkPhase.ENQUEUED, runAttemptCount = 1)
        val running = ArchiveRecoveryWorkState(ArchiveRecoveryWorkPhase.RUNNING, runAttemptCount = 0)

        assertTrue(listOf(delayed).hasDelayedArchiveRecoveryRetry())
        assertFalse(listOf(running, delayed).hasDelayedArchiveRecoveryRetry())
    }

    @Test
    fun `a newly appended delayed successor is recognized before its first attempt`() {
        val queuedRetry = ArchiveRecoveryWorkState(ArchiveRecoveryWorkPhase.ENQUEUED, runAttemptCount = 0, explicitDelay = true)

        assertTrue(listOf(queuedRetry).hasDelayedArchiveRecoveryRetry())
    }

    @Test
    fun `unchanged unavailable or corrupt content waits for readiness rather than spinning`() {
        listOf(
            DiagnosticReason.MISSING_MEDIA,
            DiagnosticReason.CORRUPT_PAYLOAD,
            DiagnosticReason.SIGN_IN_REQUIRED,
            DiagnosticReason.KEY_RECOVERY_REQUIRED,
            DiagnosticReason.INCOMPATIBLE_SERVER,
            DiagnosticReason.UNSUPPORTED_FORMAT,
        ).forEach {
            assertFalse(it.shouldRetryArchiveMedia())
        }
        listOf(
            DiagnosticReason.OFFLINE,
            DiagnosticReason.SERVER_UNAVAILABLE,
            DiagnosticReason.RATE_LIMITED,
            DiagnosticReason.LOCAL_STORAGE,
            DiagnosticReason.PERSISTENCE_FAILED,
            DiagnosticReason.UNKNOWN,
        ).forEach {
            assertTrue(it.shouldRetryArchiveMedia())
        }
    }

    @Test
    fun `a later Cloud inventory resumes recovery after an earlier missing original`() =
        runTest {
            val readiness = MutableStateFlow("signed-in-a" to 0)
            var attempts = 0
            backgroundScope.launch {
                observeArchiveRecoveryReadiness(MutableStateFlow("a"), readiness) { _ -> attempts++ }
            }
            runCurrent()
            readiness.value = "signed-in-a" to 1
            runCurrent()
            assertEquals(2, attempts)
        }

    @Test
    fun `retained work resumes on server return and sign in without opening restore UI`() =
        runTest {
            val origin = MutableStateFlow("a")
            val session = MutableStateFlow<String?>(null)
            val scheduled = mutableListOf<Pair<String, String?>>()
            backgroundScope.launch {
                observeArchiveRecoveryReadiness(origin, session) { _ -> scheduled += origin.value to session.value }
            }
            runCurrent()
            origin.value = "b"
            runCurrent()
            origin.value = "a"
            runCurrent()
            session.value = "signed-in-a"
            runCurrent()
            assertEquals(listOf("a" to null, "b" to null, "a" to null, "a" to "signed-in-a"), scheduled)
        }

    @Test
    fun `temporary scheduling failure retries without another readiness change`() =
        runTest {
            var attempts = 0
            backgroundScope.launch {
                observeArchiveRecoveryReadiness(MutableStateFlow("a"), MutableStateFlow<String?>(null)) { _ ->
                    attempts++
                    if (attempts == 1) error("storage temporarily unavailable")
                }
            }
            runCurrent()
            assertEquals(1, attempts)
            advanceTimeBy(30_000)
            runCurrent()
            assertEquals(2, attempts)
        }

    @Test
    fun `a cancelled scheduling attempt is not retried as a storage failure`() =
        runTest {
            var attempts = 0
            backgroundScope.launch {
                observeArchiveRecoveryReadiness(MutableStateFlow("a"), MutableStateFlow<String?>(null)) { _ ->
                    attempts++
                    throw CancellationException("cancelled")
                }
            }
            runCurrent()
            advanceTimeBy(60_000)
            runCurrent()
            assertEquals(1, attempts)
        }

    @Test
    fun `missing originals without an available source retain state without spinning`() {
        val summary =
            RestoreSummary(
                "archive",
                journalsImported = 0,
                notesImported = 1,
                draftsImported = 0,
                journalLinksImported = 0,
                mediaImported = 0,
                unavailableOriginals = 1,
            )
        assertFalse(summary.shouldRetryMediaRecovery())
        assertTrue(summary.copy(recoveringMedia = true).shouldRetryMediaRecovery())
        assertFalse(summary.copy(unavailableOriginals = 0, recoveringMedia = true).shouldRetryMediaRecovery())
        assertTrue(summary.copy(unavailableOriginals = 0, unavailablePreviews = 1, recoveringMedia = true).shouldRetryMediaRecovery())
    }
}
