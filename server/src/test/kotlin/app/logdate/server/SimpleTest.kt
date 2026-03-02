package app.logdate.server

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SimpleTest {
    @Test
    fun testBasicServerSetup() =
        testApplication {
            application {
                module()
            }

            // Test root endpoint
            val response = client.get("/")

            // Debug: print response details
            println("Status: ${response.status}")
            println("Body: ${response.bodyAsText()}")

            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(response.bodyAsText().contains("LogDate Server"))
        }
}
