package app.logdate.server

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Basic smoke test for the LogDate server application.
 *
 * This test ensures that the server can start up correctly using the Ktor test engine
 * and that the root endpoint is accessible and returning the expected branding information.
 * It serves as a minimal verification of the server's routing and module initialization.
 */
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
