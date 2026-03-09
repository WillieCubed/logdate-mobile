package app.logdate.client.networking

import app.logdate.shared.model.DeploymentKind
import app.logdate.shared.model.ServerCapability
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ServerDiscoveryClientTest {
    @Test
    fun `discoverServer reads descriptor from selected origin`() =
        runTest {
            val mockEngine =
                MockEngine { request ->
                    assertEquals("https://journal.example.com/api/v1/server/info", request.url.toString())
                    respond(
                        content =
                            """
                            {
                              "success": true,
                              "data": {
                                "serverOrigin": "https://journal.example.com",
                                "apiBaseUrl": "https://journal.example.com/api/v1",
                                "apiVersion": "v1",
                                "deploymentKind": "SELF_HOSTED",
                                "displayName": "Willie's LogDate",
                                "handleDomain": "journal.example.com",
                                "passkey": {
                                  "rpId": "journal.example.com",
                                  "rpName": "Willie's LogDate"
                                },
                                "capabilities": [
                                  "AUTH_PASSKEY",
                                  "SYNC_CONTENT",
                                  "SYNC_MEDIA",
                                  "ATPROTO_IDENTITY",
                                  "ATPROTO_OAUTH"
                                ]
                              }
                            }
                            """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }
            val httpClient =
                HttpClient(mockEngine) {
                    install(ContentNegotiation) {
                        json(Json { ignoreUnknownKeys = true })
                    }
                }

            val result = DefaultServerDiscoveryClient(httpClient).discoverServer("https://journal.example.com")

            assertTrue(result.isSuccess)
            val descriptor = result.getOrThrow()
            assertEquals(DeploymentKind.SELF_HOSTED, descriptor.deploymentKind)
            assertEquals("https://journal.example.com/api/v1", descriptor.apiBaseUrl)
            assertTrue(descriptor.capabilities.contains(ServerCapability.ATPROTO_OAUTH))
        }
}
