package app.logdate.server

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.*

class ApplicationTest {

    private val json = Json { ignoreUnknownKeys = true }
    
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
            val payload = json.parseToJsonElement(bodyAsText()).jsonObject
            assertEquals("healthy", payload["status"]?.jsonPrimitive?.content)
        }
    }
    
    @Test
    fun testApiV1BaseRoute() = testApplication {
        application {
            module()
        }
        
        // Test that API routes are properly mounted
        client.get("/api/v1/accounts/username/testuser/available").apply {
            assertEquals(HttpStatusCode.OK, status)
            val responseBody = bodyAsText()
            assertTrue(responseBody.contains("success"))
            assertTrue(responseBody.contains("data"))
        }
    }
}
