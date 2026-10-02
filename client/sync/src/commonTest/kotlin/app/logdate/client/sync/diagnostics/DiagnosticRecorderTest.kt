package app.logdate.client.sync.diagnostics

import app.logdate.client.sync.metadata.UploadScope
import app.logdate.shared.model.diagnostics.DiagnosticOutcome
import app.logdate.shared.model.diagnostics.DiagnosticPhase
import app.logdate.shared.model.diagnostics.DiagnosticReportCodec
import app.logdate.shared.model.diagnostics.SyncDiagnosticEvent
import io.github.aakira.napier.Antilog
import io.github.aakira.napier.LogLevel
import io.github.aakira.napier.Napier
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DiagnosticRecorderTest {
    @Test
    fun `coarse network and scheduler states reflect observed transitions without inspecting connectivity details`() =
        runTest {
            val storage =
                object : DiagnosticStorage {
                    var data: String? = null

                    override suspend fun read() = data

                    override suspend fun write(value: String) {
                        data = value
                    }

                    override suspend fun clear() {
                        data = null
                    }
                }
            val events = mutableListOf<SyncDiagnosticEvent>()
            val recorder =
                SyncDiagnosticRecorder(
                    DiagnosticHistory(storage, { 1L }),
                    backgroundScope,
                    onEvent = { event, _ -> events += event },
                    context = {
                        app.logdate.shared.model.diagnostics
                            .DiagnosticContext()
                    },
                )
            recorder.record(SyncDiagnosticEvent(DiagnosticPhase.FETCH, DiagnosticOutcome.STARTED))
            recorder.record(SyncDiagnosticEvent(DiagnosticPhase.FETCH, DiagnosticOutcome.SUCCEEDED, httpStatus = 200))
            recorder.record(
                SyncDiagnosticEvent(
                    DiagnosticPhase.FETCH,
                    DiagnosticOutcome.RETRY_SCHEDULED,
                    reason = app.logdate.shared.model.diagnostics.DiagnosticReason.OFFLINE,
                ),
            )
            assertEquals(app.logdate.shared.model.diagnostics.DiagnosticSchedulerState.RUNNING, events[0].context?.scheduler)
            assertEquals(app.logdate.shared.model.diagnostics.DiagnosticNetworkState.ONLINE, events[1].context?.network)
            assertEquals(app.logdate.shared.model.diagnostics.DiagnosticNetworkState.OFFLINE, events[2].context?.network)
            assertEquals(app.logdate.shared.model.diagnostics.DiagnosticSchedulerState.WAITING, events[2].context?.scheduler)
        }

    @Test
    fun `trusted context and finite event code reach every sink while a broken provider is isolated`() =
        runTest {
            val storage =
                object : DiagnosticStorage {
                    var data: String? = null

                    override suspend fun read() = data

                    override suspend fun write(value: String) {
                        data = value
                    }

                    override suspend fun clear() {
                        data = null
                    }
                }
            val captured = mutableListOf<SyncDiagnosticEvent>()
            var broken = false
            val expected =
                app.logdate.shared.model.diagnostics.DiagnosticContext(
                    appBuild = 17,
                    appVersion = listOf(1, 0),
                    platform = app.logdate.shared.model.diagnostics.DiagnosticPlatform.ANDROID,
                )
            val recorder =
                SyncDiagnosticRecorder(
                    DiagnosticHistory(storage, { 1L }),
                    backgroundScope,
                    onEvent = { event, _ -> captured += event },
                    context = { if (broken) error("private-provider-marker") else expected },
                )
            recorder.record(SyncDiagnosticEvent(DiagnosticPhase.UPLOAD, DiagnosticOutcome.STARTED))
            val first = recorder.report().events.first()
            assertEquals(expected.copy(scheduler = app.logdate.shared.model.diagnostics.DiagnosticSchedulerState.RUNNING), first.context)
            assertEquals(app.logdate.shared.model.diagnostics.DiagnosticCode.ATTEMPT_STARTED, first.code)
            assertEquals(first.code, captured.first().code)
            broken = true
            assertTrue(recorder.record(SyncDiagnosticEvent(DiagnosticPhase.FETCH, DiagnosticOutcome.FAILED)))
            assertTrue(!DiagnosticReportCodec.encode(recorder.report()).contains("private-provider-marker"))
        }

    @Test
    fun `startup classifies interruption and idle maintenance expires stored history without sync`() =
        runTest {
            val storage =
                object : DiagnosticStorage {
                    var data: String? = null

                    override suspend fun read() = data

                    override suspend fun write(value: String) {
                        data = value
                    }

                    override suspend fun clear() {
                        data = null
                    }
                }
            var now = 1_000L
            DiagnosticHistory(storage, { now }).append(
                SyncDiagnosticEvent(
                    DiagnosticPhase.APPLY,
                    DiagnosticOutcome.STARTED,
                    attemptId = "00000000-0000-4000-8000-000000000001",
                ),
            )
            SyncDiagnosticRecorder(DiagnosticHistory(storage, { now }), backgroundScope)
            runCurrent()
            assertTrue(storage.data.orEmpty().contains("INTERRUPTED"))
            now += 8 * 24 * 60 * 60 * 1000L
            advanceTimeBy(60_001)
            runCurrent()
            assertTrue(!storage.data.orEmpty().contains("STARTED"))
            assertTrue(!storage.data.orEmpty().contains("INTERRUPTED"))
        }

    @Test
    fun `recorder created in an already cancelled scope rejects events and does not hang export`() =
        runTest {
            val storage =
                object : DiagnosticStorage {
                    override suspend fun read(): String? = null

                    override suspend fun write(value: String) = Unit

                    override suspend fun clear() = Unit
                }
            val job = kotlinx.coroutines.Job().also { it.cancel() }
            val scope = kotlinx.coroutines.CoroutineScope(backgroundScope.coroutineContext + job)
            val recorder = SyncDiagnosticRecorder(DiagnosticHistory(storage, { 1L }), scope)
            runCurrent()
            kotlin.test.assertFalse(recorder.record(SyncDiagnosticEvent(DiagnosticPhase.FETCH, DiagnosticOutcome.STARTED)))
            val failure = runCatching { kotlinx.coroutines.withTimeout(1000) { recorder.report() } }.exceptionOrNull()
            assertTrue(failure != null && failure !is kotlinx.coroutines.TimeoutCancellationException)
        }

    @Test
    fun `export waits for accepted events and clear cannot be undone by queued writes`() =
        runTest {
            val storage =
                object : DiagnosticStorage {
                    var data: String? = null

                    override suspend fun read() = data

                    override suspend fun write(value: String) {
                        data = value
                    }

                    override suspend fun clear() {
                        data = null
                    }
                }
            val recorder = SyncDiagnosticRecorder(DiagnosticHistory(storage, { 1L }), backgroundScope)
            recorder.record(SyncDiagnosticEvent(DiagnosticPhase.FETCH, DiagnosticOutcome.FAILED))
            assertEquals(1, recorder.report().events.size)
            recorder.record(SyncDiagnosticEvent(DiagnosticPhase.APPLY, DiagnosticOutcome.FAILED))
            recorder.clear()
            runCurrent()
            assertTrue(recorder.report().events.isEmpty())
        }

    @Test
    fun `broken log sinks and a full buffer cannot fail sync and dropped events remain visible`() =
        runTest {
            val storage =
                object : DiagnosticStorage {
                    var data: String? = null

                    override suspend fun read() = data

                    override suspend fun write(value: String) {
                        data = value
                    }

                    override suspend fun clear() {
                        data = null
                    }
                }
            val history = DiagnosticHistory(storage, { 1L })
            val recorder = SyncDiagnosticRecorder(history, backgroundScope)
            val brokenSink =
                object : Antilog() {
                    override fun performLog(
                        priority: LogLevel,
                        tag: String?,
                        throwable: Throwable?,
                        message: String?,
                    ) {
                        error("private-log-path")
                    }
                }
            Napier.base(brokenSink)
            try {
                repeat(1000) { recorder.record(SyncDiagnosticEvent(DiagnosticPhase.FETCH, DiagnosticOutcome.FAILED)) }
                runCurrent()
                assertTrue(history.report().droppedEvents >= 872)
            } finally {
                Napier.takeLogarithm(brokenSink)
            }
        }

    @Test
    fun `verbose window retains progress and scoped callback never enters export`() =
        runTest {
            class Storage : DiagnosticStorage {
                var data: String? = null

                override suspend fun read() = data

                override suspend fun write(value: String) {
                    data = value
                }

                override suspend fun clear() {
                    data = null
                }
            }
            var now = 1_000L
            val mode = VerboseDiagnosticMode(Storage(), { now })
            mode.enable()
            val delivered = mutableListOf<UploadScope?>()
            val recorder =
                SyncDiagnosticRecorder(
                    DiagnosticHistory(Storage(), { now }),
                    backgroundScope,
                    mode,
                    onEvent = { _, scope -> delivered += scope },
                )
            val event =
                SyncDiagnosticEvent(
                    DiagnosticPhase.FETCH,
                    DiagnosticOutcome.STARTED,
                    attemptId = "00000000-0000-4000-8000-000000000001",
                )
            val scope = UploadScope("private-owner", "https://private-origin.example")

            recorder.record(event, scope)
            recorder.record(event.copy(pendingCount = 2), scope)
            assertEquals(2, recorder.report().events.size)
            now += 30 * 60 * 1000L
            recorder.record(event.copy(pendingCount = 3), scope)
            val report = recorder.report()

            assertEquals(2, report.events.size)
            assertEquals(3, report.events.last().pendingCount)
            assertEquals(listOf<UploadScope?>(scope, scope, scope), delivered)
            val encoded = DiagnosticReportCodec.encode(report)
            kotlin.test.assertFalse("private-owner" in encoded)
            kotlin.test.assertFalse("private-origin" in encoded)
        }

    @Test
    fun `scoped callback runs at record acceptance before the actor drains`() =
        runTest {
            val storage =
                object : DiagnosticStorage {
                    override suspend fun read(): String? = null

                    override suspend fun write(value: String) = Unit

                    override suspend fun clear() = Unit
                }
            val delivered = mutableListOf<UploadScope?>()
            val recorder =
                SyncDiagnosticRecorder(DiagnosticHistory(storage, { 1L }), backgroundScope, onEvent = { _, scope ->
                    delivered += scope
                })
            val event = SyncDiagnosticEvent(DiagnosticPhase.FETCH, DiagnosticOutcome.QUEUED)
            val scope = UploadScope("owner", "https://origin.example")

            assertTrue(recorder.record(event, scope))
            assertEquals(listOf<UploadScope?>(scope), delivered)
        }
}
