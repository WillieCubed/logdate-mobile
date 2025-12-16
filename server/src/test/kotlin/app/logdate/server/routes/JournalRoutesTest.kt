package app.logdate.server.routes

import app.logdate.server.module
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.*
import kotlin.test.*

class JournalRoutesTest {
    
    private val json = Json { ignoreUnknownKeys = true }
    
    @Test
    fun testListJournals() = testApplication {
        application { module() }
        
        val response = client.get("/api/v1/journals") {
            parameter("page", "1")
            parameter("limit", "20")
            parameter("sort", "lastUpdated")
            parameter("order", "desc")
        }
        
        assertEquals(HttpStatusCode.OK, response.status)
        
        val responseBody = json.parseToJsonElement(response.bodyAsText()).jsonObject
        val data = responseBody["data"]?.jsonObject
        assertNotNull(data)
        assertTrue(data.containsKey("items"))
        assertTrue(data.containsKey("page"))
        assertTrue(data.containsKey("limit"))
        assertTrue(data.containsKey("total"))
        assertTrue(data.containsKey("hasMore"))
        
        assertEquals(1, data["page"]?.jsonPrimitive?.int)
        assertEquals(20, data["limit"]?.jsonPrimitive?.int)
        assertEquals(1, data["total"]?.jsonPrimitive?.int)
        assertEquals(false, data["hasMore"]?.jsonPrimitive?.boolean)
    }
    
    @Test
    fun testCreateJournal() = testApplication {
        application { module() }
        
        val response = client.post("/api/v1/journals") {
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "title": "My Test Journal",
                    "description": "A journal for testing",
                    "isFavorited": true
                }
            """.trimIndent())
        }
        
        assertEquals(HttpStatusCode.Created, response.status)
        
        val responseBody = json.parseToJsonElement(response.bodyAsText()).jsonObject
        val data = responseBody["data"]?.jsonObject
        assertNotNull(data)
        assertEquals("new_journal_id", data["id"]?.jsonPrimitive?.content)
        assertEquals("My Test Journal", data["title"]?.jsonPrimitive?.content)
        assertEquals("A journal for testing", data["description"]?.jsonPrimitive?.content)
        assertEquals(true, data["isFavorited"]?.jsonPrimitive?.boolean)
    }
    
    @Test
    fun testGetSpecificJournal() = testApplication {
        application { module() }
        
        val journalId = "test_journal_123"
        val response = client.get("/api/v1/journals/$journalId")
        
        assertEquals(HttpStatusCode.OK, response.status)
        
        val responseBody = json.parseToJsonElement(response.bodyAsText()).jsonObject
        val data = responseBody["data"]?.jsonObject
        assertNotNull(data)
        assertEquals(journalId, data["id"]?.jsonPrimitive?.content)
        assertEquals("Sample Journal", data["title"]?.jsonPrimitive?.content)
    }
    
    @Test
    fun testGetJournalWithoutId() = testApplication {
        application { module() }
        
        // This should return the list of journals (no ID means list endpoint)
        val response = client.get("/api/v1/journals")
        
        // Should return the list of journals
        assertEquals(HttpStatusCode.OK, response.status)
    }
    
    @Test
    fun testUpdateJournal() = testApplication {
        application { module() }
        
        val journalId = "test_journal_123"
        val response = client.put("/api/v1/journals/$journalId") {
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "title": "Updated Journal Title",
                    "description": "Updated description",
                    "isFavorited": false
                }
            """.trimIndent())
        }
        
        assertEquals(HttpStatusCode.OK, response.status)
        
        val responseBody = json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("Journal updated successfully", responseBody["message"]?.jsonPrimitive?.content)
    }
    
    @Test
    fun testDeleteJournal() = testApplication {
        application { module() }
        
        val journalId = "test_journal_123"
        val response = client.delete("/api/v1/journals/$journalId")
        
        assertEquals(HttpStatusCode.OK, response.status)
        
        val responseBody = json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("Journal deleted successfully", responseBody["message"]?.jsonPrimitive?.content)
    }
    
    @Test
    fun testToggleJournalFavorite() = testApplication {
        application { module() }
        
        val journalId = "test_journal_123"
        val response = client.post("/api/v1/journals/$journalId/favorite") {
            contentType(ContentType.Application.Json)
        }
        
        assertEquals(HttpStatusCode.OK, response.status)
        
        val responseBody = json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("Journal favorite status toggled", responseBody["message"]?.jsonPrimitive?.content)
    }
    
    @Test
    fun testGetJournalNotes() = testApplication {
        application { module() }
        
        val journalId = "test_journal_123"
        val response = client.get("/api/v1/journals/$journalId/notes") {
            parameter("page", "1")
            parameter("limit", "20")
        }
        
        assertEquals(HttpStatusCode.OK, response.status)
        
        val responseBody = json.parseToJsonElement(response.bodyAsText()).jsonObject
        val data = responseBody["data"]?.jsonObject
        assertNotNull(data)
        assertTrue(data.containsKey("items"))
        assertTrue(data.containsKey("page"))
        assertTrue(data.containsKey("total"))
        
        assertEquals(1, data["page"]?.jsonPrimitive?.int)
        assertEquals(20, data["limit"]?.jsonPrimitive?.int)
        assertEquals(0, data["total"]?.jsonPrimitive?.int)
    }
    
    @Test
    fun testInvalidJournalOperations() = testApplication {
        application { module() }
        
        // Test PUT without a specific journal ID - this should hit the journals list endpoint
        val updateResponse = client.put("/api/v1/journals") {
            contentType(ContentType.Application.Json)
            setBody("{}")
        }
        
        // PUT on the list endpoint should return MethodNotAllowed
        assertEquals(HttpStatusCode.MethodNotAllowed, updateResponse.status)
    }
}