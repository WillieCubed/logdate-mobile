package app.logdate.server

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class DebugTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun debugApiV1BaseRoute() =
        testApplication {
            application { module() }

            val response = client.get("/api/v1/accounts/username/testuser/available")

            println("Status: ${response.status}")
            println("Body: ${response.bodyAsText()}")

            // Let's see the actual structure
            val responseBody = response.bodyAsText()
            if (responseBody.isNotEmpty()) {
                try {
                    val parsed = json.parseToJsonElement(responseBody)
                    println("Parsed JSON: $parsed")
                } catch (e: Exception) {
                    println("JSON parsing failed: ${e.message}")
                }
            }

            assertEquals(HttpStatusCode.OK, response.status)
        }

    @Test
    fun debugJsonStructure() =
        testApplication {
            application { module() }

            val response = client.get("/api/v1/accounts/username/invalid!/available")

            println("Status: ${response.status}")
            println("Body: ${response.bodyAsText()}")

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }
}
