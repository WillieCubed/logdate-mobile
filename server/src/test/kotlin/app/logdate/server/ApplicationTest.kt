package app.logdate.server

import app.logdate.shared.model.ServerCapability
import app.logdate.shared.model.ServerInfoResponse
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApplicationTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun testRoot() =
        testApplication {
            application {
                module()
            }

            client.get("/").apply {
                assertEquals(HttpStatusCode.OK, status)
                assertEquals("LogDate Server API v1.0", bodyAsText())
            }
        }

    @Test
    fun testHealth() =
        testApplication {
            application {
                module()
            }

            client.get("/health").apply {
                assertEquals(HttpStatusCode.OK, status)
                val payload = json.parseToJsonElement(bodyAsText()).jsonObject
                assertEquals("healthy", payload["status"]?.jsonPrimitive?.content)
            }
        }

    @Test
    fun testOAuthDiscoveryRoutes() =
        testApplication {
            application {
                module()
            }

            client.get("/.well-known/oauth-authorization-server").apply {
                assertEquals(HttpStatusCode.OK, status)
                val payload = json.parseToJsonElement(bodyAsText()).jsonObject
                assertEquals("https://logdate.app/oauth/authorize", payload["authorization_endpoint"]?.jsonPrimitive?.content)
            }

            client.get("/oauth/jwks").apply {
                assertEquals(HttpStatusCode.OK, status)
                val payload = json.parseToJsonElement(bodyAsText()).jsonObject
                assertTrue(payload["keys"]?.jsonArray?.isNotEmpty() == true)
            }
        }

    @Test
    fun testApiV1BaseRoute() =
        testApplication {
            application {
                module()
            }

            // Test that API routes are properly mounted
            client.get("/api/v1/auth/signup/username/testuser/available").apply {
                assertEquals(HttpStatusCode.OK, status)
                val responseBody = bodyAsText()
                assertTrue(responseBody.contains("success"))
                assertTrue(responseBody.contains("data"))
            }

            client.get("/api/v1/server/info").apply {
                assertEquals(HttpStatusCode.OK, status)
                val payload = json.decodeFromString<ServerInfoResponse>(bodyAsText())
                assertTrue(payload.success)
                assertTrue(payload.data.capabilities.contains(ServerCapability.AUTH_PASSKEY))
                assertTrue(payload.data.capabilities.contains(ServerCapability.SYNC_CONTENT))
            }
        }

    @Test
    fun testOpenApiJson() =
        testApplication {
            application {
                module()
            }

            client.get("/openapi.json").apply {
                assertEquals(HttpStatusCode.OK, status)
                val responseBody = bodyAsText()
                assertTrue(responseBody.contains("\"openapi\""))
                assertTrue(responseBody.contains("\"/api/v1/auth/signup/google\""))
                assertTrue(responseBody.contains("\"/api/v1/media\""))
                assertTrue(responseBody.contains("\"bearerAuth\""))
            }
        }

    @Test
    fun testOpenApiYaml() =
        testApplication {
            application {
                module()
            }

            client.get("/openapi.yaml").apply {
                assertEquals(HttpStatusCode.OK, status)
                val responseBody = bodyAsText()
                assertTrue(responseBody.contains("openapi:"))
                assertTrue(responseBody.contains("/api/v1/backups:"))
            }
        }

    @Test
    fun testSwaggerUi() =
        testApplication {
            application {
                module()
            }

            // SwaggerUI plugin serves the bundle from /swagger/index.html and 302s requests
            // to bare `/swagger`. Follow the redirect so the test exercises the actual page.
            val followingClient = createClient { followRedirects = true }
            followingClient.get("/swagger").apply {
                assertEquals(HttpStatusCode.OK, status)
                val responseBody = bodyAsText()
                assertTrue(responseBody.contains("SwaggerUIBundle"))
            }
        }
}
