package app.logdate.client.sync.diagnostics

import app.logdate.client.sync.test.testDefaultSyncManager
import app.logdate.shared.model.diagnostics.DiagnosticOutcome
import app.logdate.shared.model.diagnostics.DiagnosticPhase
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SyncRunDiagnosticsTest {
    @Test
    fun `requests inherit their coordinator run while keeping unique attempts`() =
        runTest {
            val events = mutableListOf<app.logdate.shared.model.diagnostics.SyncDiagnosticEvent>()
            val storage =
                object : DiagnosticStorage {
                    override suspend fun read(): String? = null

                    override suspend fun write(value: String) {}

                    override suspend fun clear() {}
                }
            val recorder =
                SyncDiagnosticRecorder(DiagnosticHistory(storage, { 1L }), backgroundScope, onEvent = {
                    event,
                    _,
                    ->
                    events += event
                })
            val client =
                io.ktor.client.HttpClient(
                    io.ktor.client.engine.mock.MockEngine {
                        respond("", io.ktor.http.HttpStatusCode.OK)
                    },
                )
            try {
                val run = SyncRunDiagnostics(recorder)
                run.phase(DiagnosticPhase.UPLOAD) {
                    val transport =
                        app.logdate.client.sync.cloud
                            .SafeCloudTransport(client, record = { events += it })
                    repeat(2) { transport.post("https://fixture.test") }
                    app.logdate.client.sync
                        .SyncResult(success = true)
                }
                val requests = events.filter { it.requestId != null }
                assertEquals(4, requests.size)
                val runId = assertNotNull(events.first().runId)
                assertTrue(requests.all { it.runId == runId })
                assertEquals(2, requests.map { it.attemptId }.toSet().size)
            } finally {
                client.close()
            }
        }

    @Test
    fun `production coordinator records correlated phases and explicit Retry`() =
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
            val manager = testDefaultSyncManager(diagnostics = recorder, syncScope = backgroundScope)
            manager.fullSync()
            runCurrent()
            val events = history.report().events
            val start = events.firstOrNull { it.phase == DiagnosticPhase.RECOVERY && it.outcome == DiagnosticOutcome.STARTED }
            assertNotNull(start)
            assertNotNull(start.runId)
            assertTrue(events.any { it.phase == DiagnosticPhase.FETCH && it.outcome == DiagnosticOutcome.STARTED })
            assertTrue(events.any { it.phase == DiagnosticPhase.UPLOAD && it.outcome == DiagnosticOutcome.STARTED })
            assertTrue(events.all { it.runId == start.runId })
            assertEquals(DiagnosticPhase.RECOVERY, events.last().phase)
            assertTrue(events.last().outcome != DiagnosticOutcome.STARTED)
            manager.releaseUploadBackoff()
            runCurrent()
            assertEquals(
                DiagnosticOutcome.QUEUED,
                history
                    .report()
                    .events
                    .last()
                    .outcome,
            )
        }
}
