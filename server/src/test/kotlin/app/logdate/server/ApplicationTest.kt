package app.logdate.server

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
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

            client.get("/swagger").apply {
                assertEquals(HttpStatusCode.OK, status)
                val responseBody = bodyAsText()
                assertTrue(responseBody.contains("SwaggerUIBundle"))
            }
        }
}
