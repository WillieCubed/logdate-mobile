package app.logdate.server

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.*

class ApplicationTest {
    
    @Test
    fun testRoot() = testApplication {
        application {
            module()
        }
        
        client.get("/").apply {
            assertEquals(HttpStatusCode.OK, status)
            assertEquals("LogDate Server API v1.0", bodyAsText())
        }
    }
    
    @Test
    fun testHealth() = testApplication {
        application {
            module()
        }
        
        client.get("/health").apply {
            assertEquals(HttpStatusCode.OK, status)
            assertEquals("OK", bodyAsText())
        }
    }
    
    @Test
    fun testApiV1BaseRoute() = testApplication {
        application {
            module()
        }
        
        // Test that API routes are properly mounted
        client.get("/api/v1/journals").apply {
            assertEquals(HttpStatusCode.OK, status)
            val responseBody = bodyAsText()
            assertTrue(responseBody.contains("success"))
            assertTrue(responseBody.contains("data"))
        }
    }
}