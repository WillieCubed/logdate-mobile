package app.logdate.server.e2e.auth

import app.logdate.server.module
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Comprehensive E2E tests for authentication and authorization flows,
 * including session management, token validation, and security scenarios.
 */
class AuthenticationFlowsE2ETest {
    
    private val json = Json { ignoreUnknownKeys = true }

    private fun errorCode(responseBody: String): String? {
        val jsonResponse = json.parseToJsonElement(responseBody).jsonObject
        return jsonResponse["error"]?.jsonObject?.get("code")?.jsonPrimitive?.content
    }
    
    @Test
    fun `complete authentication flow with username hint`() = testApplication {
        application {
            module()
        }
        
        // Step 1: Begin authentication with username
        val beginResponse = client.post("/api/v1/accounts/authenticate/begin") {
            contentType(ContentType.Application.Json)
            setBody("""{"username": "testuser"}""")
        }
        
        assertEquals(HttpStatusCode.OK, beginResponse.status)
        val beginBody = beginResponse.bodyAsText()
        
        // Parse response to extract challenge
        val beginJson = json.parseToJsonElement(beginBody).jsonObject
        val beginData = beginJson["data"]?.jsonObject
        assertNotNull(beginData)
        
        val challenge = beginData["challenge"]?.toString()?.removeSurrounding("\"")
        
        assertNotNull(challenge)
        
        // Step 2: Complete authentication (will fail due to invalid credentials, but tests the flow)
        val completeResponse = client.post("/api/v1/accounts/authenticate/complete") {
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "credential": {
                        "id": "test-credential-id",
                        "rawId": "test-raw-id",
                        "response": {
                            "clientDataJSON": "test-client-data",
                            "authenticatorData": "test-auth-data",
                            "signature": "test-signature",
                            "userHandle": ""
                        },
                        "type": "public-key"
                    },
                    "challenge": "$challenge"
                }
            """.trimIndent())
        }
        
        // Should fail authentication but not crash
        assertEquals(HttpStatusCode.Unauthorized, completeResponse.status)
        val completeBody = completeResponse.bodyAsText()
        assertTrue(completeBody.isNotBlank())
    }
    
    @Test
    fun `authentication flow without username hint`() = testApplication {
        application {
            module()
        }
        
        // Step 1: Begin authentication without username (general challenge)
        val beginResponse = client.post("/api/v1/accounts/authenticate/begin") {
            contentType(ContentType.Application.Json)
            setBody("{}")
        }
        
        assertEquals(HttpStatusCode.OK, beginResponse.status)
        val beginBody = beginResponse.bodyAsText()
        
        // Parse response to verify structure
        val beginJson = json.parseToJsonElement(beginBody).jsonObject
        val beginData = beginJson["data"]?.jsonObject
        assertNotNull(beginData)
        
        assertNotNull(beginData["challenge"])
        assertNotNull(beginData["rpId"])
        assertNotNull(beginData["allowCredentials"])
        assertNotNull(beginData["timeout"])
        assertNotNull(beginData["userVerification"])
    }
    
    @Test
    fun `authentication challenge validation`() = testApplication {
        application {
            module()
        }
        
        // Test with completely invalid challenge
        val invalidSessionResponse = client.post("/api/v1/accounts/authenticate/complete") {
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "credential": {
                        "id": "test-id",
                        "rawId": "test-raw-id",
                        "response": {
                            "clientDataJSON": "test-data",
                            "authenticatorData": "test-auth",
                            "signature": "test-sig",
                            "userHandle": ""
                        },
                        "type": "public-key"
                    },
                    "challenge": "completely-invalid-challenge"
                }
            """.trimIndent())
        }
        
        assertEquals(HttpStatusCode.Unauthorized, invalidSessionResponse.status)
        val responseBody = invalidSessionResponse.bodyAsText()
        assertEquals("AUTHENTICATION_FAILED", errorCode(responseBody))
    }
    
    @Test
    fun `token refresh with various invalid tokens`() = testApplication {
        application {
            module()
        }
        
        // Test with malformed token
        val malformedTokenResponse = client.post("/api/v1/accounts/refresh") {
            contentType(ContentType.Application.Json)
            setBody("""{"refreshToken": "invalid.jwt.token"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, malformedTokenResponse.status)
        
        // Test with expired-looking token
        val expiredTokenResponse = client.post("/api/v1/accounts/refresh") {
            contentType(ContentType.Application.Json)
            setBody("""{"refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, expiredTokenResponse.status)
        
        // Test with empty token
        val emptyTokenResponse = client.post("/api/v1/accounts/refresh") {
            contentType(ContentType.Application.Json)
            setBody("""{"refreshToken": ""}""")
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
        val invalidJsonResponse = client.post("/api/v1/accounts/authenticate/begin") {
            contentType(ContentType.Application.Json)
            setBody("{invalid json}")
        }
        assertEquals(HttpStatusCode.BadRequest, invalidJsonResponse.status)
        
        // Test auth/complete with missing required fields
        val missingFieldsResponse = client.post("/api/v1/accounts/authenticate/complete") {
            contentType(ContentType.Application.Json)
            setBody("""{"challenge": "test"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, missingFieldsResponse.status)
        
        // Test auth/complete with invalid credential structure
        val invalidCredentialResponse = client.post("/api/v1/accounts/authenticate/complete") {
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "credential": "invalid-credential",
                    "challenge": "test-session"
                }
            """.trimIndent())
        }
        assertEquals(HttpStatusCode.BadRequest, invalidCredentialResponse.status)
    }
    
    @Test
    fun `concurrent authentication attempts`() = testApplication {
        application {
            module()
        }
        
        // Start multiple authentication sessions
        val challenges = mutableListOf<String>()
        
        repeat(3) { i ->
            val response = client.post("/api/v1/accounts/authenticate/begin") {
                contentType(ContentType.Application.Json)
                setBody("""{"username": "user$i"}""")
            }
            
            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            val jsonResponse = json.parseToJsonElement(body).jsonObject
            val data = jsonResponse["data"]?.jsonObject
            val challenge = data?.get("challenge")?.toString()?.removeSurrounding("\"")
            
            if (challenge != null) {
                challenges.add(challenge)
            }
        }
        
        assertEquals(3, challenges.size, "All challenges should be collected")
        challenges.forEach { challenge ->
            assertTrue(challenge.isNotBlank(), "Challenge should not be blank")
        }
        
        // Try to complete authentication with each challenge
        challenges.forEach { challenge ->
            val completeResponse = client.post("/api/v1/accounts/authenticate/complete") {
                contentType(ContentType.Application.Json)
                setBody("""
                    {
                        "credential": {
                            "id": "test-credential",
                            "rawId": "test-raw",
                            "response": {
                                "clientDataJSON": "test-data",
                                "authenticatorData": "test-auth",
                                "signature": "test-sig",
                                "userHandle": ""
                            },
                            "type": "public-key"
                        },
                        "challenge": "$challenge"
                    }
                """.trimIndent())
            }
            
            // Should fail authentication but handle the request properly
            assertEquals(HttpStatusCode.Unauthorized, completeResponse.status)
        }
    }
    
    @Test
    fun `session lifecycle and cleanup`() = testApplication {
        application {
            module()
        }
        
        // Create a challenge
        val beginResponse = client.post("/api/v1/accounts/authenticate/begin") {
            contentType(ContentType.Application.Json)
            setBody("""{"username": "testuser"}""")
        }
        
        assertEquals(HttpStatusCode.OK, beginResponse.status)
        val beginBody = beginResponse.bodyAsText()
        val beginJson = json.parseToJsonElement(beginBody).jsonObject
        val beginData = beginJson["data"]?.jsonObject
        val challenge = beginData?.get("challenge")?.toString()?.removeSurrounding("\"")
        
        assertNotNull(challenge)
        
        // Try to use challenge immediately
        val firstAttempt = client.post("/api/v1/accounts/authenticate/complete") {
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "credential": {
                        "id": "test-id",
                        "rawId": "test-raw-id",
                        "response": {
                            "clientDataJSON": "test-data",
                            "authenticatorData": "test-auth",
                            "signature": "test-sig",
                            "userHandle": ""
                        },
                        "type": "public-key"
                    },
                    "challenge": "$challenge"
                }
            """.trimIndent())
        }
        
        // Should fail authentication but handle the challenge
        assertEquals(HttpStatusCode.Unauthorized, firstAttempt.status)
        
        // Try to use the same challenge again (should be rejected)
        val secondAttempt = client.post("/api/v1/accounts/authenticate/complete") {
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "credential": {
                        "id": "different-id",
                        "rawId": "different-raw-id",
                        "response": {
                            "clientDataJSON": "different-data",
                            "authenticatorData": "different-auth",
                            "signature": "different-sig", 
                            "userHandle": ""
                        },
                        "type": "public-key"
                    },
                    "challenge": "$challenge"
                }
            """.trimIndent())
        }
        
        // Should also fail
        assertEquals(HttpStatusCode.Unauthorized, secondAttempt.status)
    }
}
