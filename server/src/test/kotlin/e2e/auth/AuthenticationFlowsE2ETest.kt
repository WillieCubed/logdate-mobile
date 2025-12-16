package app.logdate.server.e2e.auth

import app.logdate.server.module
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Comprehensive E2E tests for authentication and authorization flows,
 * including session management, token validation, and security scenarios.
 */
class AuthenticationFlowsE2ETest {
    
    private val json = Json { ignoreUnknownKeys = true }
    
    @Test
    fun `complete authentication flow with username hint`() = testApplication {
        application {
            module()
        }
        
        // Step 1: Begin authentication with username
        val beginResponse = client.post("/api/v1/accounts/auth/begin") {
            contentType(ContentType.Application.Json)
            setBody("""{"username": "testuser"}""")
        }
        
        assertEquals(HttpStatusCode.OK, beginResponse.status)
        val beginBody = beginResponse.bodyAsText()
        
        // Parse response to extract session details
        val beginJson = json.parseToJsonElement(beginBody).jsonObject
        val beginData = beginJson["data"]?.jsonObject
        assertNotNull(beginData)
        
        val sessionId = beginData["sessionId"]?.toString()?.removeSurrounding("\"")
        val challenge = beginData["challenge"]?.toString()?.removeSurrounding("\"")
        
        assertNotNull(sessionId)
        assertNotNull(challenge)
        
        // Step 2: Complete authentication (will fail due to invalid credentials, but tests the flow)
        val completeResponse = client.post("/api/v1/accounts/auth/complete") {
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "sessionId": "$sessionId",
                    "credential": {
                        "id": "test-credential-id",
                        "rawId": "test-raw-id",
                        "response": {
                            "clientDataJSON": "test-client-data",
                            "authenticatorData": "test-auth-data",
                            "signature": "test-signature",
                            "userHandle": null
                        },
                        "type": "public-key"
                    }
                }
            """.trimIndent())
        }
        
        // Should fail authentication but not crash
        assertTrue(completeResponse.status.value >= 400)
        val completeBody = completeResponse.bodyAsText()
        assertTrue(completeBody.isNotBlank())
    }
    
    @Test
    fun `authentication flow without username hint`() = testApplication {
        application {
            module()
        }
        
        // Step 1: Begin authentication without username (general challenge)
        val beginResponse = client.post("/api/v1/accounts/auth/begin") {
            contentType(ContentType.Application.Json)
            setBody("{}")
        }
        
        assertEquals(HttpStatusCode.OK, beginResponse.status)
        val beginBody = beginResponse.bodyAsText()
        
        // Parse response to verify structure
        val beginJson = json.parseToJsonElement(beginBody).jsonObject
        val beginData = beginJson["data"]?.jsonObject
        assertNotNull(beginData)
        
        assertNotNull(beginData["sessionId"])
        assertNotNull(beginData["challenge"])
        assertNotNull(beginData["authenticationOptions"])
        
        val authOptions = beginData["authenticationOptions"]?.jsonObject
        assertNotNull(authOptions)
        assertNotNull(authOptions["challenge"])
    }
    
    @Test
    fun `authentication session validation`() = testApplication {
        application {
            module()
        }
        
        // Test with completely invalid session ID
        val invalidSessionResponse = client.post("/api/v1/accounts/auth/complete") {
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "sessionId": "completely-invalid-session-id",
                    "credential": {
                        "id": "test-id",
                        "rawId": "test-raw-id",
                        "response": {
                            "clientDataJSON": "test-data",
                            "authenticatorData": "test-auth",
                            "signature": "test-sig",
                            "userHandle": null
                        },
                        "type": "public-key"
                    }
                }
            """.trimIndent())
        }
        
        assertEquals(HttpStatusCode.BadRequest, invalidSessionResponse.status)
        val responseBody = invalidSessionResponse.bodyAsText()
        assertTrue(responseBody.contains("INVALID_SESSION") || responseBody.contains("Invalid"))
    }
    
    @Test
    fun `token refresh with various invalid tokens`() = testApplication {
        application {
            module()
        }
        
        // Test with malformed JWT
        val malformedTokenResponse = client.post("/api/v1/accounts/token/refresh") {
            header("Authorization", "Bearer invalid.jwt.token")
        }
        assertEquals(HttpStatusCode.Unauthorized, malformedTokenResponse.status)
        
        // Test with expired-looking token
        val expiredTokenResponse = client.post("/api/v1/accounts/token/refresh") {
            header("Authorization", "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c")
        }
        assertEquals(HttpStatusCode.Unauthorized, expiredTokenResponse.status)
        
        // Test with empty token after Bearer
        val emptyTokenResponse = client.post("/api/v1/accounts/token/refresh") {
            header("Authorization", "Bearer ")
        }
        assertEquals(HttpStatusCode.Unauthorized, emptyTokenResponse.status)
    }
    
    @Test
    fun `access token validation with various invalid tokens`() = testApplication {
        application {
            module()
        }
        
        // Test with malformed access token
        val malformedResponse = client.get("/api/v1/accounts/me") {
            header("Authorization", "Bearer invalid.access.token")
        }
        assertEquals(HttpStatusCode.Unauthorized, malformedResponse.status)
        
        // Test with very short token
        val shortTokenResponse = client.get("/api/v1/accounts/me") {
            header("Authorization", "Bearer abc")
        }
        assertEquals(HttpStatusCode.Unauthorized, shortTokenResponse.status)
        
        // Test with very long random token
        val longRandomToken = "a".repeat(500)
        val longTokenResponse = client.get("/api/v1/accounts/me") {
            header("Authorization", "Bearer $longRandomToken")
        }
        assertEquals(HttpStatusCode.Unauthorized, longTokenResponse.status)
    }
    
    @Test
    fun `authorization header format validation`() = testApplication {
        application {
            module()
        }
        
        // Test with wrong case
        val wrongCaseResponse = client.get("/api/v1/accounts/me") {
            header("Authorization", "bearer valid-token")
        }
        assertEquals(HttpStatusCode.Unauthorized, wrongCaseResponse.status)
        
        // Test with different auth scheme
        val basicAuthResponse = client.get("/api/v1/accounts/me") {
            header("Authorization", "Basic dXNlcjpwYXNz")
        }
        assertEquals(HttpStatusCode.Unauthorized, basicAuthResponse.status)
        
        // Test with just the token (no Bearer)
        val noSchemeResponse = client.get("/api/v1/accounts/me") {
            header("Authorization", "some-token")
        }
        assertEquals(HttpStatusCode.Unauthorized, noSchemeResponse.status)
        
        // Test with extra spaces
        val extraSpacesResponse = client.get("/api/v1/accounts/me") {
            header("Authorization", "Bearer  token-with-spaces")
        }
        assertEquals(HttpStatusCode.Unauthorized, extraSpacesResponse.status)
    }
    
    @Test
    fun `authentication endpoint input validation`() = testApplication {
        application {
            module()
        }
        
        // Test auth/begin with invalid JSON
        val invalidJsonResponse = client.post("/api/v1/accounts/auth/begin") {
            contentType(ContentType.Application.Json)
            setBody("{invalid json}")
        }
        assertTrue(invalidJsonResponse.status.value >= 400)
        
        // Test auth/complete with missing required fields
        val missingFieldsResponse = client.post("/api/v1/accounts/auth/complete") {
            contentType(ContentType.Application.Json)
            setBody("""{"sessionId": "test"}""")
        }
        assertTrue(missingFieldsResponse.status.value >= 400)
        
        // Test auth/complete with invalid credential structure
        val invalidCredentialResponse = client.post("/api/v1/accounts/auth/complete") {
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "sessionId": "test-session",
                    "credential": "invalid-credential"
                }
            """.trimIndent())
        }
        assertTrue(invalidCredentialResponse.status.value >= 400)
    }
    
    @Test
    fun `concurrent authentication attempts`() = testApplication {
        application {
            module()
        }
        
        // Start multiple authentication sessions
        val sessions = mutableListOf<String>()
        
        repeat(3) { i ->
            val response = client.post("/api/v1/accounts/auth/begin") {
                contentType(ContentType.Application.Json)
                setBody("""{"username": "user$i"}""")
            }
            
            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            val jsonResponse = json.parseToJsonElement(body).jsonObject
            val data = jsonResponse["data"]?.jsonObject
            val sessionId = data?.get("sessionId")?.toString()?.removeSurrounding("\"")
            
            if (sessionId != null) {
                sessions.add(sessionId)
            }
        }
        
        // Verify all sessions are unique
        assertEquals(3, sessions.toSet().size, "All sessions should be unique")
        
        // Try to complete authentication with each session
        sessions.forEach { sessionId ->
            val completeResponse = client.post("/api/v1/accounts/auth/complete") {
                contentType(ContentType.Application.Json)
                setBody("""
                    {
                        "sessionId": "$sessionId",
                        "credential": {
                            "id": "test-credential",
                            "rawId": "test-raw",
                            "response": {
                                "clientDataJSON": "test-data",
                                "authenticatorData": "test-auth",
                                "signature": "test-sig",
                                "userHandle": null
                            },
                            "type": "public-key"
                        }
                    }
                """.trimIndent())
            }
            
            // Should fail authentication but handle the request properly
            assertTrue(completeResponse.status.value >= 400)
        }
    }
    
    @Test
    fun `session lifecycle and cleanup`() = testApplication {
        application {
            module()
        }
        
        // Create a session
        val beginResponse = client.post("/api/v1/accounts/auth/begin") {
            contentType(ContentType.Application.Json)
            setBody("""{"username": "testuser"}""")
        }
        
        assertEquals(HttpStatusCode.OK, beginResponse.status)
        val beginBody = beginResponse.bodyAsText()
        val beginJson = json.parseToJsonElement(beginBody).jsonObject
        val beginData = beginJson["data"]?.jsonObject
        val sessionId = beginData?.get("sessionId")?.toString()?.removeSurrounding("\"")
        
        assertNotNull(sessionId)
        
        // Try to use session immediately
        val firstAttempt = client.post("/api/v1/accounts/auth/complete") {
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "sessionId": "$sessionId",
                    "credential": {
                        "id": "test-id",
                        "rawId": "test-raw-id",
                        "response": {
                            "clientDataJSON": "test-data",
                            "authenticatorData": "test-auth",
                            "signature": "test-sig",
                            "userHandle": null
                        },
                        "type": "public-key"
                    }
                }
            """.trimIndent())
        }
        
        // Should fail authentication but recognize the session
        assertTrue(firstAttempt.status.value >= 400)
        
        // Try to use the same session again (should still be invalid due to bad credentials)
        val secondAttempt = client.post("/api/v1/accounts/auth/complete") {
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "sessionId": "$sessionId",
                    "credential": {
                        "id": "different-id",
                        "rawId": "different-raw-id",
                        "response": {
                            "clientDataJSON": "different-data",
                            "authenticatorData": "different-auth",
                            "signature": "different-sig", 
                            "userHandle": null
                        },
                        "type": "public-key"
                    }
                }
            """.trimIndent())
        }
        
        // Should also fail
        assertTrue(secondAttempt.status.value >= 400)
    }
}