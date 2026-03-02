package app.logdate.server.routes

import app.logdate.server.auth.JwtTokenService
import app.logdate.server.configureSyncTestApp
import app.logdate.server.sync.SyncMetricsSnapshot
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SyncMetricsRoutesTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun authHeader(tokenService: JwtTokenService): String {
        val accountId = UUID.randomUUID().toString()
        return "Bearer ${tokenService.generateAccessToken(accountId)}"
    }

    @Serializable
    private data class SyncMetricsPayload(
        val data: SyncMetricsSnapshot,
    )

    @Test
    fun `sync metrics endpoint reports upload counts`() =
        testApplication {
            val env = configureSyncTestApp()
            val authHeader = authHeader(env.tokenService)

            val upload =
                client.post("/api/v1/sync/content") {
                    header(HttpHeaders.Authorization, authHeader)
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                          "id": "note-metrics",
                          "type": "TEXT",
                          "content": "metrics",
                          "mediaUri": null,
                          "createdAt": 1,
                          "lastUpdated": 1,
                          "deviceId": "dev-1"
                        }
                        """.trimIndent(),
                    )
                }
            assertEquals(HttpStatusCode.OK, upload.status)

            val metricsResponse =
                client.get("/api/v1/sync/metrics") {
                    header(HttpHeaders.Authorization, authHeader)
                }
            assertEquals(HttpStatusCode.OK, metricsResponse.status)

            val payload = json.decodeFromString<SyncMetricsPayload>(metricsResponse.bodyAsText())
            val uploadMetric = payload.data.operations.firstOrNull { it.name == "sync.content.upload" }
            assertTrue(uploadMetric != null)
            assertTrue(uploadMetric.successCount >= 1)
        }
}
