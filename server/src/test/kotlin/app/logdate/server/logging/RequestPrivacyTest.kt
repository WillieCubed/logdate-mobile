package app.logdate.server.logging

import app.logdate.shared.model.diagnostics.DiagnosticReportCodec
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

@OptIn(kotlin.uuid.ExperimentalUuidApi::class)
class RequestPrivacyTest {
    @Test
    fun `request diagnostics classify attachment routes without retaining record paths`() =
        testApplication {
            val events = mutableListOf<app.logdate.shared.model.diagnostics.SyncDiagnosticEvent>()
            application {
                installRequestDiagnostics { events += it }
                routing { get("/api/v1/media/private-record-marker/binary") { call.respondText("Ready") } }
            }
            client.get("/api/v1/media/private-record-marker/binary?token=private-token-marker").bodyAsText()
            assertEquals(2, events.size)
            assertTrue(events.all { it.route == app.logdate.shared.model.diagnostics.DiagnosticRoute.MEDIA_BINARY })
            assertTrue(events.all { it.phase == app.logdate.shared.model.diagnostics.DiagnosticPhase.MEDIA })
            assertFalse(events.toString().contains("private-"))
        }

    @Test
    fun `server events include finite protocol context and semantic event codes`() {
        val requestId = Uuid.random().toString()
        var captured: app.logdate.shared.model.diagnostics.SyncDiagnosticEvent? = null
        val sink =
            object : io.github.aakira.napier.Antilog() {
                override fun performLog(
                    priority: io.github.aakira.napier.LogLevel,
                    tag: String?,
                    throwable: Throwable?,
                    message: String?,
                ) {
                    if (tag == SAFE_SERVER_EVENT_TAG && message != null) {
                        runCatching {
                            kotlinx.serialization.json.Json
                                .decodeFromString<app.logdate.shared.model.diagnostics.SyncDiagnosticEvent>(
                                    message,
                                )
                        }.getOrNull()
                            ?.takeIf { it.requestId == requestId }
                            ?.let { captured = it }
                    }
                }
            }
        io.github.aakira.napier.Napier
            .base(sink)
        try {
            recordServerDiagnostic(
                app.logdate.shared.model.diagnostics.SyncDiagnosticEvent(
                    app.logdate.shared.model.diagnostics.DiagnosticPhase.FETCH,
                    app.logdate.shared.model.diagnostics.DiagnosticOutcome.SUCCEEDED,
                    requestId = requestId,
                ),
            )
            assertEquals(app.logdate.shared.model.diagnostics.DiagnosticCode.REQUEST_COMPLETED, captured?.code)
            assertEquals(app.logdate.shared.model.diagnostics.DiagnosticPlatform.SERVER, captured?.context?.platform)
            assertTrue(
                captured?.context?.protocols?.contains(app.logdate.shared.model.diagnostics.DiagnosticProtocol.DIAGNOSTICS_V1) == true,
            )
        } finally {
            io.github.aakira.napier.Napier
                .takeLogarithm(sink)
        }
    }

    @Test
    fun `diagnostic observer failure cannot fail a service request`() =
        testApplication {
            application {
                installRequestDiagnostics { throw IllegalStateException("private-observer-marker") }
                routing { get("/ready") { call.respondText("Ready") } }
            }
            val response = client.get("/ready")
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("Ready", response.bodyAsText())
        }

    @Test
    fun `request phases feed bounded metrics without request or route identifiers`() =
        testApplication {
            val metrics =
                app.logdate.server.sync
                    .SyncMetricsRegistry()
            application {
                installRequestDiagnostics(metrics::recordDiagnostic)
                routing { get("/private-route-marker") { call.respondText("Ready") } }
            }
            val id = Uuid.random().toString()
            client.get("/private-route-marker") { header("X-Request-ID", id) }.bodyAsText()

            val snapshot = metrics.snapshot()
            assertEquals(2L, snapshot.diagnostics.sumOf { it.count })
            assertTrue(snapshot.diagnostics.any { it.outcome == app.logdate.shared.model.diagnostics.DiagnosticOutcome.SUCCEEDED })
            assertFalse(snapshot.toString().contains(id))
            assertFalse(snapshot.toString().contains("private-route-marker"))
        }

    @Test
    fun `failure after a sent response still has a correlated terminal failure`() =
        testApplication {
            val id = Uuid.random().toString()
            val captured = mutableListOf<app.logdate.shared.model.diagnostics.SyncDiagnosticEvent>()
            val sink =
                object : io.github.aakira.napier.Antilog() {
                    override fun performLog(
                        priority: io.github.aakira.napier.LogLevel,
                        tag: String?,
                        throwable: Throwable?,
                        message: String?,
                    ) {
                        if (tag == SAFE_SERVER_EVENT_TAG && message != null) {
                            runCatching {
                                kotlinx.serialization.json.Json
                                    .decodeFromString<app.logdate.shared.model.diagnostics.SyncDiagnosticEvent>(
                                        message,
                                    )
                            }.getOrNull()
                                ?.takeIf { it.requestId == id }
                                ?.let(captured::add)
                        }
                    }
                }
            io.github.aakira.napier.Napier
                .base(sink)
            application {
                installRequestDiagnostics()
                routing {
                    get("/late-failure") {
                        call.respondText("Complete")
                        throw IllegalStateException("PRIVATE_LATE_EXCEPTION_SENTINEL")
                    }
                }
            }
            client.get("/late-failure") { header("X-Request-ID", id) }.bodyAsText()
            assertTrue(captured.any { it.outcome == app.logdate.shared.model.diagnostics.DiagnosticOutcome.FAILED })
        }

    @Test
    fun `validated request references correlate responses without affecting authorization`() =
        testApplication {
            application {
                installRequestDiagnostics()
                routing { get("/private") { call.respondText("Unauthorized", status = HttpStatusCode.Unauthorized) } }
            }
            val id = Uuid.random().toString()
            val response = client.get("/private") { header("X-Request-ID", id) }
            assertEquals(HttpStatusCode.Unauthorized, response.status)
            assertEquals(id, response.headers["X-Request-ID"])
            val invalid = client.get("/private") { header("X-Request-ID", "PRIVATE_PATH_TOKEN_SENTINEL") }
            assertEquals(HttpStatusCode.Unauthorized, invalid.status)
            assertTrue(DiagnosticReportCodec.isCorrelationId(invalid.headers["X-Request-ID"].orEmpty()))
        }

    @Test
    fun `unexpected exception cannot expose body path or nested cause through response`() =
        testApplication {
            val marker = "PRIVATE_PATH_TOKEN_SENTINEL"
            application {
                installRequestDiagnostics()
                routing { get("/failure") { throw IllegalStateException(marker, IllegalArgumentException(marker)) } }
            }
            val response = client.get("/failure?content=$marker") { header("Authorization", marker) }
            assertEquals(HttpStatusCode.InternalServerError, response.status)
            assertFalse(response.bodyAsText().contains(marker))
            assertTrue(response.bodyAsText().contains("INTERNAL_ERROR"))
            assertTrue(DiagnosticReportCodec.isCorrelationId(response.headers["X-Request-ID"].orEmpty()))
        }
}
