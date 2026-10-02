package app.logdate.client.sync.cloud

import app.logdate.shared.model.diagnostics.DiagnosticOutcome
import app.logdate.shared.model.diagnostics.DiagnosticReportCodec
import app.logdate.shared.model.diagnostics.SyncDiagnosticEvent
import app.logdate.shared.model.diagnostics.SyncDiagnosticReport
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class SafeCloudTransportTest {
    @Test
    fun `known archive and media routes keep their phase without exposing identifiers`() =
        runTest {
            val events = mutableListOf<SyncDiagnosticEvent>()
            val client = HttpClient(MockEngine { respond("", HttpStatusCode.OK) })
            try {
                val transport = SafeCloudTransport(client, record = { events += it })
                transport.get("https://private-host-marker/api/v1/media/private-record-marker/binary?token=private-token-marker")
                transport.post("https://private-host-marker/api/v1/backups")
                transport.streamGet("https://private-host-marker/api/v1/backups/private-record-marker/binary", {}) { }
                assertEquals(
                    listOf(
                        app.logdate.shared.model.diagnostics.DiagnosticRoute.MEDIA_BINARY,
                        app.logdate.shared.model.diagnostics.DiagnosticRoute.BACKUPS,
                        app.logdate.shared.model.diagnostics.DiagnosticRoute.BACKUP_BINARY,
                    ),
                    events.filter { it.outcome == DiagnosticOutcome.STARTED }.map { it.route },
                )
                assertEquals(
                    listOf(
                        app.logdate.shared.model.diagnostics.DiagnosticPhase.MEDIA,
                        app.logdate.shared.model.diagnostics.DiagnosticPhase.ARCHIVE,
                        app.logdate.shared.model.diagnostics.DiagnosticPhase.ARCHIVE,
                    ),
                    events.filter { it.outcome == DiagnosticOutcome.STARTED }.map { it.phase },
                )
                assertFalse(
                    DiagnosticReportCodec.encode(SyncDiagnosticReport(Uuid.random().toString(), events = events)).contains("private-"),
                )
            } finally {
                client.close()
            }
        }

    @Test
    fun `content endpoints use finite templates and custom paths stay unknown`() {
        val route = app.logdate.shared.model.diagnostics.DiagnosticRoute.NOTE
        assertEquals(
            route,
            app.logdate.shared.model.diagnostics
                .diagnosticRoute("/api/v1/contents/private-record-marker"),
        )
        assertEquals(
            app.logdate.shared.model.diagnostics.DiagnosticRoute.NOTES,
            app.logdate.shared.model.diagnostics
                .diagnosticRoute("/api/v1/contents"),
        )
        assertEquals(
            app.logdate.shared.model.diagnostics.DiagnosticRoute.UNKNOWN,
            app.logdate.shared.model.diagnostics
                .diagnosticRoute("/private-path-marker/media/record/binary"),
        )
    }

    @Test
    fun `mutation requests report the upload phase`() =
        runTest {
            val events = mutableListOf<SyncDiagnosticEvent>()
            val client = HttpClient(MockEngine { respond("", HttpStatusCode.OK) })
            try {
                val transport = SafeCloudTransport(client, record = { events += it })
                transport.post("https://fixture.test")
                transport.put("https://fixture.test")
                transport.patch("https://fixture.test")
                transport.delete("https://fixture.test")
                assertEquals(8, events.size)
                assertTrue(events.all { it.phase == app.logdate.shared.model.diagnostics.DiagnosticPhase.UPLOAD })
            } finally {
                client.close()
            }
        }

    @Test
    fun `each request has a fresh safe reference with correlated transitions and no URL or body`() =
        runTest {
            val ids = mutableListOf<String?>()
            val events = mutableListOf<SyncDiagnosticEvent>()
            val client =
                HttpClient(
                    MockEngine { request ->
                        ids += request.headers["X-Request-ID"]
                        respond("private-body-marker", HttpStatusCode.ServiceUnavailable)
                    },
                )
            try {
                val transport = SafeCloudTransport(client, { events += it })
                repeat(2) { transport.get("https://private-host-marker.test/private-path-marker?token=private-token-marker") }
                assertTrue(ids.all { it != null && DiagnosticReportCodec.isCorrelationId(it) })
                assertEquals(2, ids.toSet().size)
                assertEquals(4, events.size)
                assertEquals(ids, events.filter { it.outcome == DiagnosticOutcome.STARTED }.map { it.requestId })
                assertEquals(ids, events.filter { it.outcome == DiagnosticOutcome.FAILED }.map { it.requestId })
                val report = DiagnosticReportCodec.encode(SyncDiagnosticReport(Uuid.random().toString(), events = events))
                listOf("private-host-marker", "private-path-marker", "private-token-marker", "private-body-marker").forEach {
                    assertFalse(report.contains(it))
                }
            } finally {
                client.close()
            }
        }

    @Test
    fun `cancellation records interruption without forwarding its message`() =
        runTest {
            val events = mutableListOf<SyncDiagnosticEvent>()
            val client = HttpClient(MockEngine { throw CancellationException("private-cause-marker") })
            try {
                assertFailsWith<CancellationException> { SafeCloudTransport(client, { events += it }).get("https://fixture.test") }
                assertEquals(DiagnosticOutcome.INTERRUPTED, events.last().outcome)
                assertFalse(
                    DiagnosticReportCodec
                        .encode(
                            SyncDiagnosticReport(Uuid.random().toString(), events = events),
                        ).contains("private-cause-marker"),
                )
            } finally {
                client.close()
            }
        }

    @Test
    fun `sink cancellation is isolated and cannot prevent either transport or the other sink`() =
        runTest {
            var requests = 0
            val scopedEvents = mutableListOf<SyncDiagnosticEvent>()
            val client =
                HttpClient(
                    MockEngine {
                        requests++
                        respond("", HttpStatusCode.OK)
                    },
                )
            try {
                val response =
                    SafeCloudTransport(
                        client,
                        record = { throw CancellationException("private-sink-marker") },
                        scopedRecord = { event, _ -> scopedEvents += event },
                    ).get("https://fixture.test")
                assertEquals(HttpStatusCode.OK, response.status)
                assertEquals(1, requests)
                assertEquals(listOf(DiagnosticOutcome.STARTED, DiagnosticOutcome.SUCCEEDED), scopedEvents.map { it.outcome })
            } finally {
                client.close()
            }
        }

    @Test
    fun `broken diagnostic sink cannot fail a request`() =
        runTest {
            val client = HttpClient(MockEngine { respond("", HttpStatusCode.OK) })
            try {
                val response = SafeCloudTransport(client, record = { error("private-sink-marker") }).get("https://fixture.test")
                assertEquals(HttpStatusCode.OK, response.status)
            } finally {
                client.close()
            }
        }

    @Test
    fun `source ownership is captured before dispatch and never adopted from a later selection`() =
        runTest {
            val before =
                app.logdate.client.sync.diagnostics.DiagnosticSource(
                    app.logdate.client.sync.metadata
                        .UploadScope("owner-a", "https://a.invalid"),
                    "epoch-a",
                )
            var selected = before
            val observed = mutableListOf<app.logdate.client.sync.diagnostics.DiagnosticSource?>()
            val client =
                HttpClient(
                    MockEngine {
                        selected =
                            app.logdate.client.sync.diagnostics.DiagnosticSource(
                                app.logdate.client.sync.metadata
                                    .UploadScope("owner-b", "https://b.invalid"),
                                "epoch-b",
                            )
                        respond("", HttpStatusCode.ServiceUnavailable)
                    },
                )
            try {
                SafeCloudTransport(client, source = { selected }, scopedRecord = { _, source -> observed += source })
                    .get("https://a.invalid")
                assertEquals(listOf<app.logdate.client.sync.diagnostics.DiagnosticSource?>(before, before), observed)
            } finally {
                client.close()
            }
        }

    @Test
    fun `diagnostic delivery and nested refresh can suppress recursive diagnostic events`() =
        runTest {
            val events = mutableListOf<SyncDiagnosticEvent>()
            val client = HttpClient(MockEngine { respond("", HttpStatusCode.ServiceUnavailable) })
            try {
                kotlinx.coroutines.withContext(app.logdate.client.sync.diagnostics.SuppressDiagnosticReporting) {
                    SafeCloudTransport(client, record = { events += it }).get("https://fixture.test")
                }
                assertTrue(events.isEmpty())
            } finally {
                client.close()
            }
        }
}
