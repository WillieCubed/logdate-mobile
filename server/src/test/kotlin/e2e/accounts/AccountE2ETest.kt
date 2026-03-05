package app.logdate.server.e2e.accounts

import app.logdate.server.module
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Comprehensive E2E tests for account management endpoints with valid request bodies,
 * authentication flows, and detailed scenarios.
 */
class AccountE2ETest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun errorCode(responseBody: String): String? {
        val jsonResponse = json.parseToJsonElement(responseBody).jsonObject
        return jsonResponse["error"]
            ?.jsonObject
            ?.get("code")
            ?.jsonPrimitive
            ?.content
    }

    @Test
    fun `username availability check with valid username returns available`() =
        testApplication {
            application {
                module()
            }

            val response = client.get("/api/v1/accounts/username/testuser123/available")

            assertEquals(HttpStatusCode.OK, response.status)
            val responseBody = response.bodyAsText()
            assertTrue(responseBody.contains("testuser123"))
            assertTrue(responseBody.contains("available"))
        }

    @Test
    fun `username availability check with invalid username returns bad request`() =
        testApplication {
            application {
                module()
            }

            // Test empty username (URL-encoded space)
            val emptyResponse = client.get("/api/v1/accounts/username/%20/available")
            assertEquals(HttpStatusCode.BadRequest, emptyResponse.status)

            // Test too short username
            val shortResponse = client.get("/api/v1/accounts/username/ab/available")
            assertEquals(HttpStatusCode.BadRequest, shortResponse.status)

            // Test too long username
            val longUsername = "a".repeat(51)
            val longResponse = client.get("/api/v1/accounts/username/$longUsername/available")
            assertEquals(HttpStatusCode.BadRequest, longResponse.status)

            // Test invalid characters
            val invalidResponse = client.get("/api/v1/accounts/username/test%40user/available")
            assertEquals(HttpStatusCode.BadRequest, invalidResponse.status)
        }

    @Test
    fun `account creation begin with valid data returns registration options`() =
        testApplication {
            application {
                module()
            }

            val response =
                client.post("/api/v1/accounts/create/begin") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                            "username": "newtestuser",
                            "displayName": "New Test User",
                            "bio": "A test user for E2E testing"
                        }
                        """.trimIndent(),
                    )
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val responseBody = response.bodyAsText()

            // Parse response to verify structure
            val jsonResponse = json.parseToJsonElement(responseBody).jsonObject
            val data = jsonResponse["data"]?.jsonObject

            assertNotNull(data)
            assertNotNull(data["sessionToken"])
            assertNotNull(data["registrationOptions"])

            val registrationOptions = data["registrationOptions"]?.jsonObject
            assertNotNull(registrationOptions)
            assertNotNull(registrationOptions["challenge"])
            assertNotNull(registrationOptions["user"])
        }

    @Test
    fun `account creation begin with invalid data returns error`() =
        testApplication {
            application {
                module()
            }

            // Test missing username - may return 500 due to deserialization or service issues
            val missingUsernameResponse =
                client.post("/api/v1/accounts/create/begin") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                            "displayName": "Test User"
                        }
                        """.trimIndent(),
                    )
                }
            assertEquals(HttpStatusCode.BadRequest, missingUsernameResponse.status)

            // Test empty username - should return BadRequest if validation works
            val emptyUsernameResponse =
                client.post("/api/v1/accounts/create/begin") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                            "username": "",
                            "displayName": "Test User"
                        }
                        """.trimIndent(),
                    )
                }
            assertEquals(HttpStatusCode.BadRequest, emptyUsernameResponse.status)

            // Test missing display name - may return 500 due to deserialization or service issues
            val missingDisplayNameResponse =
                client.post("/api/v1/accounts/create/begin") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                            "username": "testuser"
                        }
                        """.trimIndent(),
                    )
                }
            assertEquals(HttpStatusCode.BadRequest, missingDisplayNameResponse.status)
        }

    @Test
    fun `account creation begin with existing username behavior`() =
        testApplication {
            application {
                module()
            }

            val username = "existinguser"

            // Create an account with this username
            val beginResponse =
                client.post("/api/v1/accounts/create/begin") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                            "username": "$username",
                            "displayName": "First User"
                        }
                        """.trimIndent(),
                    )
                }
            assertEquals(HttpStatusCode.OK, beginResponse.status)

            val beginBody = beginResponse.bodyAsText()
            val beginJson = json.parseToJsonElement(beginBody).jsonObject
            val beginData = beginJson["data"]?.jsonObject
            val sessionToken = beginData?.get("sessionToken")?.toString()?.removeSurrounding("\"")
            assertNotNull(sessionToken)

            val completeResponse =
                client.post("/api/v1/accounts/create/complete") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                            "sessionToken": "$sessionToken",
                            "credential": {
                                "id": "test-credential-id",
                                "rawId": "test-raw-id",
                                "response": {
                                    "clientDataJSON": "test-client-data",
                                    "attestationObject": "test-attestation"
                                },
                                "type": "public-key"
                            }
                        }
                        """.trimIndent(),
                    )
                }
            assertEquals(HttpStatusCode.Created, completeResponse.status)

            // Try to create another account with the same username
            val conflictResponse =
                client.post("/api/v1/accounts/create/begin") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                            "username": "$username", 
                            "displayName": "Second User"
                        }
                        """.trimIndent(),
                    )
                }

            assertEquals(HttpStatusCode.Conflict, conflictResponse.status)

            val responseBody = conflictResponse.bodyAsText()
            // Just verify we get some kind of response
            assertTrue(responseBody.isNotBlank(), "Expected non-empty response body")
        }

    @Test
    fun `account creation complete with invalid session returns unauthorized`() =
        testApplication {
            application {
                module()
            }

            val response =
                client.post("/api/v1/accounts/create/complete") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                            "sessionToken": "invalid-session-id",
                            "credential": {
                                "id": "test-credential-id",
                                "rawId": "test-raw-id",
                                "response": {
                                    "clientDataJSON": "test-client-data",
                                    "attestationObject": "test-attestation"
                                },
                                "type": "public-key"
                            }
                        }
                        """.trimIndent(),
                    )
                }

            assertEquals(HttpStatusCode.Unauthorized, response.status)
            val responseBody = response.bodyAsText()
            assertEquals("INVALID_SESSION_TOKEN", errorCode(responseBody))
        }

    @Test
    fun `authentication begin without username returns challenge`() =
        testApplication {
            application {
                module()
            }

            val response =
                client.post("/api/v1/accounts/authenticate/begin") {
                    contentType(ContentType.Application.Json)
                    setBody("{}")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val responseBody = response.bodyAsText()

            // Parse response to verify structure
            val jsonResponse = json.parseToJsonElement(responseBody).jsonObject
            val data = jsonResponse["data"]?.jsonObject

            assertNotNull(data)
            assertNotNull(data["challenge"])
            assertNotNull(data["rpId"])
            assertNotNull(data["allowCredentials"])
            assertNotNull(data["timeout"])
            assertNotNull(data["userVerification"])
        }

    @Test
    fun `authentication begin with username returns user-specific challenge`() =
        testApplication {
            application {
                module()
            }

            val response =
                client.post("/api/v1/accounts/authenticate/begin") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"username": "testuser"}""")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val responseBody = response.bodyAsText()

            // Parse response to verify structure
            val jsonResponse = json.parseToJsonElement(responseBody).jsonObject
            val data = jsonResponse["data"]?.jsonObject

            assertNotNull(data)
            assertNotNull(data["challenge"])
            assertNotNull(data["rpId"])
            assertNotNull(data["allowCredentials"])
        }

    @Test
    fun `authentication complete with invalid session returns unauthorized`() =
        testApplication {
            application {
                module()
            }

            val response =
                client.post("/api/v1/accounts/authenticate/complete") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
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
                            "challenge": "invalid-challenge"
                        }
                        """.trimIndent(),
                    )
                }

            assertEquals(HttpStatusCode.Unauthorized, response.status)
            val responseBody = response.bodyAsText()
            assertEquals("AUTHENTICATION_FAILED", errorCode(responseBody))
        }

    @Test
    fun `token refresh with empty refresh token returns unauthorized`() =
        testApplication {
            application {
                module()
            }

            val response =
                client.post("/api/v1/accounts/refresh") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"refreshToken": ""}""")
                }

            assertEquals(HttpStatusCode.Unauthorized, response.status)
            val responseBody = response.bodyAsText()
            assertEquals("INVALID_REFRESH_TOKEN", errorCode(responseBody))
        }

    @Test
    fun `token refresh with invalid refresh token returns unauthorized`() =
        testApplication {
            application {
                module()
            }

            val response =
                client.post("/api/v1/accounts/refresh") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"refreshToken": "invalid-token"}""")
                }

            assertEquals(HttpStatusCode.Unauthorized, response.status)
            val responseBody = response.bodyAsText()
            assertEquals("INVALID_REFRESH_TOKEN", errorCode(responseBody))
        }

    @Test
    fun `token refresh with missing refresh token field returns error`() =
        testApplication {
            application {
                module()
            }

            val response =
                client.post("/api/v1/accounts/refresh") {
                    contentType(ContentType.Application.Json)
                    setBody("""{}""")
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }

    @Test
    fun `get account info with missing authorization header returns unauthorized`() =
        testApplication {
            application {
                module()
            }

            val response = client.get("/api/v1/accounts/me")

            assertEquals(HttpStatusCode.Unauthorized, response.status)
            val responseBody = response.bodyAsText()
            assertEquals("INVALID_TOKEN", errorCode(responseBody))
        }

    @Test
    fun `get account info with invalid access token returns unauthorized`() =
        testApplication {
            application {
                module()
            }

            val response =
                client.get("/api/v1/accounts/me") {
                    header("Authorization", "Bearer invalid-access-token")
                }

            assertEquals(HttpStatusCode.Unauthorized, response.status)
            val responseBody = response.bodyAsText()
            assertEquals("INVALID_TOKEN", errorCode(responseBody))
        }

    @Test
    fun `account update returns method not allowed`() =
        testApplication {
            application {
                module()
            }

            val response =
                client.patch("/api/v1/accounts/me") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"displayName": "Updated Name"}""")
                }

            assertEquals(HttpStatusCode.MethodNotAllowed, response.status)
        }

    @Test
    fun `account deletion returns method not allowed`() =
        testApplication {
            application {
                module()
            }

            val response = client.delete("/api/v1/accounts/me")

            assertEquals(HttpStatusCode.MethodNotAllowed, response.status)
        }
}
