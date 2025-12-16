package app.logdate.server

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.*

class SimpleTest {
    
    @Test
    fun testBasicServerSetup() = testApplication {
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