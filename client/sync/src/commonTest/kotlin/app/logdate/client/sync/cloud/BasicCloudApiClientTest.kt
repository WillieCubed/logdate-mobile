package app.logdate.client.sync.cloud

import app.logdate.shared.model.BeginAccountCreationRequest
import app.logdate.shared.model.CompleteAccountCreationRequest
import app.logdate.shared.model.PasskeyAuthenticatorResponse
import app.logdate.shared.model.PasskeyCredentialResponse
import app.logdate.util.UuidSerializer
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * Smoke tests for auth-relevant API mappings in [LogDateCloudApiClient].
 */
class BasicCloudApiClientTest {
    private val baseUrl = "https://api.logdate.example.com/v1"
    private val json =
        Json {
            ignoreUnknownKeys = true
            prettyPrint = false
            serializersModule =
                SerializersModule {
                    contextual(Uuid::class, UuidSerializer)
                }
        }

    @Test
    fun `username availability uses auth v1 path`() =
        runTest {
            val client =
                createApiClient(
                    MockEngine { request ->
                        assertEquals("$baseUrl/auth/signup/username/testuser/available", request.url.toString())
                        respond(
                            content = """{"success":true,"data":{"username":"testuser","available":true}}""",
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                        )
                    },
                )

            val result = client.checkUsernameAvailability("testuser")
            assertTrue(result.isSuccess)
            assertEquals("testuser", result.getOrThrow().username)
            assertTrue(result.getOrThrow().available)
        }

    @Test
    fun `begin account creation uses auth v1 passkey signup path`() =
        runTest {
            val client =
                createApiClient(
                    MockEngine { request ->
                        assertEquals("$baseUrl/auth/signup/passkey/begin", request.url.toString())
                        respond(
                            content =
                                """
                                {
                                  "success": true,
                                  "data": {
                                    "sessionToken": "sess123",
                                    "registrationOptions": {
                                      "challenge": "challenge123",
                                      "user": {"id":"u","name":"newuser","displayName":"New User"},
                                      "timeout": 300000
                                    }
                                  }
                                }
                                """.trimIndent(),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                        )
                    },
                )

            val result = client.beginAccountCreation(BeginAccountCreationRequest("newuser", "New User"))
            assertTrue(result.isSuccess)
            assertEquals("sess123", result.getOrThrow().data.sessionToken)
        }

    @Test
    fun `complete account creation maps auth response to legacy response model`() =
        runTest {
            val client =
                createApiClient(
                    MockEngine { request ->
                        assertEquals("$baseUrl/auth/signup/passkey/complete", request.url.toString())
                        respond(
                            content =
                                """
                                {
                                  "success": true,
                                  "data": {
                                    "account": {
                                      "id": "550e8400-e29b-41d4-a716-446655440000",
                                      "username": "newuser",
                                      "displayName": "New User",
                                      "passkeyCredentialIds": ["cred-1"],
                                      "createdAt": "2026-03-05T00:00:00Z",
                                      "updatedAt": "2026-03-05T00:00:00Z"
                                    },
                                    "tokens": {"accessToken":"access","refreshToken":"refresh"}
                                  }
                                }
                                """.trimIndent(),
                            status = HttpStatusCode.Created,
                            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                        )
                    },
                )

            val result =
                client.completeAccountCreation(
                    CompleteAccountCreationRequest(
                        sessionToken = "sess123",
                        credential =
                            PasskeyCredentialResponse(
                                id = "cred-1",
                                rawId = "cred-1",
                                response = PasskeyAuthenticatorResponse("client", "attestation"),
                            ),
                    ),
                )

            assertTrue(result.isSuccess)
            assertEquals(
                "newuser",
                result
                    .getOrThrow()
                    .data
                    .account
                    .username,
            )
            assertEquals(
                "access",
                result
                    .getOrThrow()
                    .data
                    .tokens
                    .accessToken,
            )
        }

    @Test
    fun `refresh token uses auth v1 path`() =
        runTest {
            val client =
                createApiClient(
                    MockEngine { request ->
                        assertEquals("$baseUrl/auth/token/refresh", request.url.toString())
                        respond(
                            content = """{"success":true,"data":{"accessToken":"new-access"}}""",
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                        )
                    },
                )

            val result = client.refreshAccessToken("refresh")
            assertTrue(result.isSuccess)
            assertEquals("new-access", result.getOrThrow())
        }

    @Test
    fun `get account info uses auth me path`() =
        runTest {
            val client =
                createApiClient(
                    MockEngine { request ->
                        assertEquals("$baseUrl/auth/me", request.url.toString())
                        assertEquals("Bearer token-123", request.headers[HttpHeaders.Authorization])
                        respond(
                            content =
                                """
                                {
                                  "success": true,
                                  "data": {
                                    "account": {
                                      "id": "550e8400-e29b-41d4-a716-446655440001",
                                      "username": "tester",
                                      "displayName": "Tester",
                                      "passkeyCredentialIds": ["cred-1"],
                                      "createdAt": "2026-03-05T00:00:00Z",
                                      "updatedAt": "2026-03-05T00:00:00Z"
                                    },
                                    "tokens": {"accessToken":"access","refreshToken":"refresh"}
                                  }
                                }
                                """.trimIndent(),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                        )
                    },
                )

            val result = client.getAccountInfo("token-123")
            assertTrue(result.isSuccess)
            assertEquals("tester", result.getOrThrow().username)
        }

    private fun createApiClient(mockEngine: MockEngine): LogDateCloudApiClient {
        val httpClient =
            HttpClient(mockEngine) {
                install(ContentNegotiation) {
                    json(json)
                }
            }
        return LogDateCloudApiClient(baseUrl, httpClient)
    }
}
