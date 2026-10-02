@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package app.logdate.server.routes.sync

import app.logdate.server.auth.JwtTokenService
import app.logdate.server.crypto.EncryptionKey
import app.logdate.server.crypto.EncryptionKeyring
import app.logdate.server.diagnostics.DiagnosticReportService
import app.logdate.server.diagnostics.InMemoryDiagnosticReportStore
import app.logdate.shared.model.diagnostics.DiagnosticReportCodec
import app.logdate.shared.model.diagnostics.SyncDiagnosticReport
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class DiagnosticReportRoutesTest {
    private val tokenService = JwtTokenService("diagnostic-report-test-secret-key-32-chars-min")
    private val alice = Uuid.parse("11111111-1111-1111-1111-111111111111")
    private val bob = Uuid.parse("22222222-2222-2222-2222-222222222222")
    private val keyring =
        object : EncryptionKeyring {
            private val key = EncryptionKey("test", ByteArray(32) { it.toByte() })

            override fun getActiveKey(): EncryptionKey = key

            override fun getKey(keyId: String): EncryptionKey? = key.takeIf { it.keyId == keyId }
        }

    @Test
    fun `authenticated owner can create list read and delete reports without exposing another owner`() =
        testApplication {
            val service = DiagnosticReportService(InMemoryDiagnosticReportStore(), keyring)
            application {
                install(ContentNegotiation) { json() }
                routing { route("/api/v1") { diagnosticReportRoutes(tokenService, service) } }
            }
            val report = SyncDiagnosticReport(reportId = Uuid.random().toString())
            val aliceToken = tokenService.generateAccessToken(alice.toString())
            val bobToken = tokenService.generateAccessToken(bob.toString())

            val unauthenticated =
                client.post("/api/v1/diagnostics/reports") {
                    contentType(ContentType.Application.Json)
                    setBody(DiagnosticReportCodec.encode(report))
                }
            assertEquals(HttpStatusCode.Unauthorized, unauthenticated.status)

            val created =
                client.post("/api/v1/diagnostics/reports") {
                    header(HttpHeaders.Authorization, "Bearer $aliceToken")
                    contentType(ContentType.Application.Json)
                    setBody(DiagnosticReportCodec.encode(report))
                }
            assertEquals(HttpStatusCode.Created, created.status, created.bodyAsText())

            val conflict =
                client.post("/api/v1/diagnostics/reports") {
                    header(HttpHeaders.Authorization, "Bearer $aliceToken")
                    contentType(ContentType.Application.Json)
                    setBody(DiagnosticReportCodec.encode(report.copy(droppedEvents = 1)))
                }
            assertEquals(HttpStatusCode.Conflict, conflict.status)

            val foreignRead =
                client.get("/api/v1/diagnostics/reports/${report.reportId}") {
                    header(HttpHeaders.Authorization, "Bearer $bobToken")
                }
            assertEquals(HttpStatusCode.NotFound, foreignRead.status)
            val foreignDelete =
                client.delete("/api/v1/diagnostics/reports/${report.reportId}") {
                    header(HttpHeaders.Authorization, "Bearer $bobToken")
                }
            assertEquals(HttpStatusCode.NotFound, foreignDelete.status)

            val ownerRead =
                client.get("/api/v1/diagnostics/reports/${report.reportId}") {
                    header(HttpHeaders.Authorization, "Bearer $aliceToken")
                }
            assertEquals(HttpStatusCode.OK, ownerRead.status)
            assertTrue(ownerRead.bodyAsText().contains(report.reportId))
            val ownerList =
                client.get("/api/v1/diagnostics/reports") {
                    header(HttpHeaders.Authorization, "Bearer $aliceToken")
                }
            assertEquals(HttpStatusCode.OK, ownerList.status)
            assertTrue(ownerList.bodyAsText().contains(report.reportId))
            assertFalse(ownerList.bodyAsText().contains("events"))

            val ownerDelete =
                client.delete("/api/v1/diagnostics/reports/${report.reportId}") {
                    header(HttpHeaders.Authorization, "Bearer $aliceToken")
                }
            assertEquals(HttpStatusCode.NoContent, ownerDelete.status)
            assertEquals(
                HttpStatusCode.NotFound,
                client
                    .get("/api/v1/diagnostics/reports/${report.reportId}") {
                        header(HttpHeaders.Authorization, "Bearer $aliceToken")
                    }.status,
            )
        }

    @Test
    fun `malformed and oversized reports are rejected without reflecting private input`() =
        testApplication {
            val service = DiagnosticReportService(InMemoryDiagnosticReportStore(), keyring)
            application {
                install(ContentNegotiation) { json() }
                routing { route("/api/v1") { diagnosticReportRoutes(tokenService, service) } }
            }
            val token = tokenService.generateAccessToken(alice.toString())
            val marker = "private-content-path-token-marker"
            val invalid =
                client.post("/api/v1/diagnostics/reports") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody("""{"reportId":"${Uuid.random()}","secret":"$marker"}""")
                }
            assertEquals(HttpStatusCode.BadRequest, invalid.status)
            assertFalse(invalid.bodyAsText().contains(marker))

            val oversized =
                client.post("/api/v1/diagnostics/reports") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody("x".repeat(DiagnosticReportCodec.MAX_REPORT_BYTES + 1))
                }
            assertEquals(HttpStatusCode.PayloadTooLarge, oversized.status)
            assertTrue(service.list(alice).isEmpty())
        }

    @Test
    fun `collection delete removes only authenticated owner's reports`() =
        testApplication {
            val service = DiagnosticReportService(InMemoryDiagnosticReportStore(), keyring)
            application {
                install(ContentNegotiation) { json() }
                routing { route("/api/v1") { diagnosticReportRoutes(tokenService, service) } }
            }
            val aliceReport = SyncDiagnosticReport(reportId = Uuid.random().toString())
            val bobReport = SyncDiagnosticReport(reportId = Uuid.random().toString())
            val aliceToken = tokenService.generateAccessToken(alice.toString())
            val bobToken = tokenService.generateAccessToken(bob.toString())
            service.create(alice, DiagnosticReportCodec.encode(aliceReport))
            service.create(bob, DiagnosticReportCodec.encode(bobReport))

            val response =
                client.delete("/api/v1/diagnostics/reports") {
                    header(HttpHeaders.Authorization, "Bearer $aliceToken")
                }
            assertEquals(HttpStatusCode.NoContent, response.status)
            assertEquals(
                HttpStatusCode.NotFound,
                client
                    .get("/api/v1/diagnostics/reports/${aliceReport.reportId}") {
                        header(HttpHeaders.Authorization, "Bearer $aliceToken")
                    }.status,
            )
            assertEquals(
                HttpStatusCode.OK,
                client
                    .get("/api/v1/diagnostics/reports/${bobReport.reportId}") {
                        header(HttpHeaders.Authorization, "Bearer $bobToken")
                    }.status,
            )
        }

    @Test
    fun `a server without reports answers unavailable after authentication`() =
        testApplication {
            application {
                install(ContentNegotiation) { json() }
                routing { route("/api/v1") { diagnosticReportRoutes(tokenService, null) } }
            }
            val token = tokenService.generateAccessToken(alice.toString())

            assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v1/diagnostics/reports").status)
            val response =
                client.post("/api/v1/diagnostics/reports") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody(DiagnosticReportCodec.encode(SyncDiagnosticReport(reportId = Uuid.random().toString())))
                }
            assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
            assertTrue(response.bodyAsText().contains("DIAGNOSTIC_REPORTS_UNAVAILABLE"))
        }
}
