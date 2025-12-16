package app.logdate.server.routes

import app.logdate.server.module
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.*
import kotlin.test.*

class AuthRoutesTest {
    
    private val json = Json { ignoreUnknownKeys = true }
    
    @Test
    fun testUserRegistration() = testApplication {
        application { module() }
        
        val response = client.post("/api/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "email": "test@example.com",
                    "password": "test123",
                    "firstName": "John",
                    "lastName": "Doe"
                }
            """.trimIndent())
        }
        
        assertEquals(HttpStatusCode.Created, response.status)
        
        val responseBody = json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertTrue(responseBody.containsKey("data"))
        
        val data = responseBody["data"]?.jsonObject
        assertNotNull(data)
        assertTrue(data.containsKey("accessToken"))
        assertTrue(data.containsKey("refreshToken"))
        assertEquals("stub_access_token", data["accessToken"]?.jsonPrimitive?.content)
    }
    
    @Test
    fun testUserLogin() = testApplication {
        application { module() }
        
        val response = client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "email": "test@example.com",
                    "password": "test123"
                }
            """.trimIndent())
        }
        
        assertEquals(HttpStatusCode.OK, response.status)
        
        val responseBody = json.parseToJsonElement(response.bodyAsText()).jsonObject
        val data = responseBody["data"]?.jsonObject
        assertNotNull(data)
        assertTrue(data.containsKey("accessToken"))
        assertTrue(data.containsKey("refreshToken"))
        assertTrue(data.containsKey("expiresIn"))
    }
    
    @Test
    fun testTokenRefresh() = testApplication {
        application { module() }
        
        val response = client.post("/api/v1/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "refreshToken": "valid_refresh_token"
                }
            """.trimIndent())
        }
        
        assertEquals(HttpStatusCode.OK, response.status)
        
        val responseBody = json.parseToJsonElement(response.bodyAsText()).jsonObject
        val data = responseBody["data"]?.jsonObject
        assertNotNull(data)
        assertEquals("new_stub_access_token", data["accessToken"]?.jsonPrimitive?.content)
    }
    
    @Test
    fun testLogout() = testApplication {
        application { module() }
        
        val response = client.post("/api/v1/auth/logout") {
            contentType(ContentType.Application.Json)
        }
        
        assertEquals(HttpStatusCode.OK, response.status)
        
        val responseBody = json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("Logged out successfully", responseBody["message"]?.jsonPrimitive?.content)
    }
    
    @Test
    fun testForgotPassword() = testApplication {
        application { module() }
        
        val response = client.post("/api/v1/auth/forgot-password") {
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "email": "test@example.com"
                }
            """.trimIndent())
        }
        
        assertEquals(HttpStatusCode.OK, response.status)
        
        val responseBody = json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("Password reset email sent", responseBody["message"]?.jsonPrimitive?.content)
    }
    
    @Test
    fun testResetPassword() = testApplication {
        application { module() }
        
        val response = client.post("/api/v1/auth/reset-password") {
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "token": "valid_reset_token",
                    "newPassword": "newPassword123"
                }
            """.trimIndent())
        }
        
        assertEquals(HttpStatusCode.OK, response.status)
        
        val responseBody = json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("Password reset successfully", responseBody["message"]?.jsonPrimitive?.content)
    }
    
    @Test
    fun testGetUserProfile() = testApplication {
        application { module() }
        
        val response = client.get("/api/v1/user/profile")
        
        assertEquals(HttpStatusCode.OK, response.status)
        
        val responseBody = json.parseToJsonElement(response.bodyAsText()).jsonObject
        val data = responseBody["data"]?.jsonObject
        assertNotNull(data)
        assertEquals("stub_user_id", data["id"]?.jsonPrimitive?.content)
        assertEquals("user@example.com", data["email"]?.jsonPrimitive?.content)
        assertEquals("John", data["firstName"]?.jsonPrimitive?.content)
        assertEquals("Doe", data["lastName"]?.jsonPrimitive?.content)
    }
    
    @Test
    fun testUpdateUserProfile() = testApplication {
        application { module() }
        
        val response = client.put("/api/v1/user/profile") {
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "firstName": "Jane",
                    "lastName": "Smith",
                    "email": "jane@example.com"
                }
            """.trimIndent())
        }
        
        assertEquals(HttpStatusCode.OK, response.status)
        
        val responseBody = json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("Profile updated successfully", responseBody["message"]?.jsonPrimitive?.content)
    }
    
    @Test
    fun testUpdateUserPreferences() = testApplication {
        application { module() }
        
        val response = client.put("/api/v1/user/preferences") {
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "birthday": "1990-01-01T00:00:00Z",
                    "isOnboarded": true,
                    "securityLevel": "BIOMETRIC",
                    "favoriteNotes": ["note1", "note2"]
                }
            """.trimIndent())
        }
        
        assertEquals(HttpStatusCode.OK, response.status)
        
        val responseBody = json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("Preferences updated successfully", responseBody["message"]?.jsonPrimitive?.content)
    }
    
    @Test
    fun testDeleteUserAccount() = testApplication {
        application { module() }
        
        val response = client.delete("/api/v1/user/account")
        
        assertEquals(HttpStatusCode.OK, response.status)
        
        val responseBody = json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("Account deleted successfully", responseBody["message"]?.jsonPrimitive?.content)
    }
}