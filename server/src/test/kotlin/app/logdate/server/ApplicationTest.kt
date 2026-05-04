package app.logdate.server

import app.logdate.shared.model.ServerCapability
import app.logdate.shared.model.ServerInfoResponse
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ApplicationTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun testRootJsonForNonBrowsers() =
        testApplication {
            application {
                module()
            }

            client.get("/").apply {
                assertEquals(HttpStatusCode.OK, status)
                val payload = json.parseToJsonElement(bodyAsText()).jsonObject
                assertEquals("LogDate Server API", payload["name"]?.jsonPrimitive?.content)
                assertEquals("/swagger", payload["docs"]?.jsonPrimitive?.content)
            }
        }

    @Test
    fun testRootHtmlForBrowsers() =
        testApplication {
            application {
                module()
            }

            client
                .get("/") {
                    header(HttpHeaders.Accept, "text/html")
                }.apply {
                    assertEquals(HttpStatusCode.OK, status)
                    val body = bodyAsText()
                    assertTrue(body.contains("<title>LogDate API</title>"))
                    assertTrue(body.contains("/swagger"))
                }
        }

    @Test
    fun testHealthOmitsInternalDetails() =
        testApplication {
            application {
                module()
            }

            client.get("/health").apply {
                assertEquals(HttpStatusCode.OK, status)
                val payload = json.parseToJsonElement(bodyAsText()).jsonObject
                assertEquals("healthy", payload["status"]?.jsonPrimitive?.content)
                assertNull(
                    payload["db_connected"],
                    "public /health must not leak deployment internals",
                )
            }
        }

    @Test
    fun testHealthInternal404sWithoutToken() =
        testApplication {
            application {
                module(isDatabaseAvailable = true, healthInternalToken = "shh-secret")
            }

            client.get("/health/internal").apply {
                assertEquals(HttpStatusCode.NotFound, status)
            }
            client
                .get("/health/internal") {
                    header("X-LogDate-Health-Token", "wrong")
                }.apply {
                    assertEquals(HttpStatusCode.NotFound, status)
                }
        }

    @Test
    fun testHealthInternal404sWhenTokenUnconfigured() =
        testApplication {
            application {
                module(isDatabaseAvailable = true, healthInternalToken = "")
            }

            // Even with the right "any" header, no token configured = no endpoint.
            client
                .get("/health/internal") {
                    header("X-LogDate-Health-Token", "anything")
                }.apply {
                    assertEquals(HttpStatusCode.NotFound, status)
                }
        }

    @Test
    fun testHealthInternalReturnsDbConnectedWithCorrectToken() =
        testApplication {
            application {
                module(isDatabaseAvailable = true, healthInternalToken = "shh-secret")
            }

            client
                .get("/health/internal") {
                    header("X-LogDate-Health-Token", "shh-secret")
                }.apply {
                    assertEquals(HttpStatusCode.OK, status)
                    val payload = json.parseToJsonElement(bodyAsText()).jsonObject
                    assertEquals("healthy", payload["status"]?.jsonPrimitive?.content)
                    assertEquals(true, payload["db_connected"]?.jsonPrimitive?.content?.toBoolean())
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

            client.get("/swagger/index.html").apply {
                assertEquals(HttpStatusCode.OK, status)
                val responseBody = bodyAsText()
                assertTrue(responseBody.contains("Swagger UI"))
                assertTrue(responseBody.contains("swagger-ui-bundle.js"))
            }
        }
}
