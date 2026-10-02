package app.logdate.client.sync.diagnostics

import app.logdate.client.sync.cloud.CloudRequestLocation
import app.logdate.client.sync.cloud.CloudRequestLocationProvider
import app.logdate.client.sync.metadata.UploadScope
import app.logdate.client.sync.test.FakeCloudAccountRepository
import app.logdate.client.sync.test.FakeSessionStorage
import app.logdate.shared.config.DefaultLogDateConfigRepository
import app.logdate.shared.model.CloudAccountRepository
import app.logdate.shared.model.diagnostics.DiagnosticReportCodec
import app.logdate.shared.model.diagnostics.SyncDiagnosticReport
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class DiagnosticReportGatewayTest {
    @Test
    fun `report refresh remains scoped and suppresses recursive events through both attempts`() =
        runTest {
            val origin = "https://fixture.invalid"
            val config = DefaultLogDateConfigRepository(initialBackendUrl = origin)
            val sessions = FakeSessionStorage().apply { currentOrigin = origin }
            val ids = mutableListOf<String?>()
            var refreshes = 0
            val accounts =
                object : CloudAccountRepository by FakeCloudAccountRepository(), CloudRequestLocationProvider {
                    override fun captureLocation() = CloudRequestLocation(origin, "$origin/api/v1")

                    override fun isCurrentOrigin(origin: String) = config.getCurrentBackendUrl() == origin

                    override suspend fun refreshAccessToken(refreshToken: String): Result<String> {
                        assertNotNull(currentCoroutineContext()[SuppressDiagnosticReporting.Key])
                        refreshes++
                        return Result.success("rotated-access")
                    }
                }
            val client =
                HttpClient(
                    MockEngine { request ->
                        ids += request.headers["X-Request-ID"]
                        assertEquals("/api/v1/diagnostics/reports", request.url.encodedPath)
                        if (ids.size == 1) {
                            respond("private-response-marker", HttpStatusCode.Unauthorized)
                        } else {
                            respond("", HttpStatusCode.Created)
                        }
                    },
                )
            try {
                val gateway = DiagnosticReportGateway(client, config, sessions, accounts)
                val result = gateway.send(UploadScope("test-account-id", origin), SyncDiagnosticReport(Uuid.random().toString()))
                assertEquals(DiagnosticDelivery.ACCEPTED, result)
                assertEquals(1, refreshes)
                assertEquals(2, ids.distinct().size)
                assertTrue(ids.all { it != null && DiagnosticReportCodec.isCorrelationId(it) })
            } finally {
                client.close()
            }
        }

    @Test
    fun `old scope is refused before any report or delete request`() =
        runTest {
            val config = DefaultLogDateConfigRepository(initialBackendUrl = "https://b.invalid")
            val sessions = FakeSessionStorage().apply { currentOrigin = "https://b.invalid" }
            var requests = 0
            val accounts =
                object : CloudAccountRepository by FakeCloudAccountRepository(), CloudRequestLocationProvider {
                    override fun captureLocation() = CloudRequestLocation("https://b.invalid", "https://b.invalid/api/v1")

                    override fun isCurrentOrigin(origin: String) = origin == config.getCurrentBackendUrl()
                }
            val client =
                HttpClient(
                    MockEngine {
                        requests++
                        respond("", HttpStatusCode.OK)
                    },
                )
            try {
                val gateway = DiagnosticReportGateway(client, config, sessions, accounts)
                val old = UploadScope("test-account-id", "https://a.invalid")
                gateway.send(old, SyncDiagnosticReport(Uuid.random().toString()))
                gateway.deleteAll(old)
                assertEquals(0, requests)
            } finally {
                client.close()
            }
        }
}
