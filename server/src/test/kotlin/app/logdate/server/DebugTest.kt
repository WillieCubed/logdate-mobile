package app.logdate.server

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.*
import kotlin.test.*

class DebugTest {
    
    private val json = Json { ignoreUnknownKeys = true }
    
    @Test
    fun debugApiV1BaseRoute() = testApplication {
        application { module() }
        
        val response = client.get("/api/v1/journals")
        
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
    fun debugJsonStructure() = testApplication {
        application { module() }
        
        val response = client.get("/api/v1/journals/{id}".replace("{id}", "test123"))
        
        println("Status: ${response.status}")
        println("Body: ${response.bodyAsText()}")
        
        assertEquals(HttpStatusCode.OK, response.status)
    }
}