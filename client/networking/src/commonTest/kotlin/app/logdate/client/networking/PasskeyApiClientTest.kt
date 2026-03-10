package app.logdate.client.networking

import app.logdate.shared.config.LogDateConfigRepository
import app.logdate.shared.model.BeginAccountCreationRequest
import app.logdate.shared.model.BeginAuthenticationRequest
import app.logdate.shared.model.CompleteAccountCreationRequest
import app.logdate.shared.model.CompleteAuthenticationRequest
import app.logdate.shared.model.PasskeyAssertionAuthenticatorResponse
import app.logdate.shared.model.PasskeyAssertionResponse
import app.logdate.shared.model.PasskeyAuthenticatorResponse
import app.logdate.shared.model.PasskeyCredentialResponse
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PasskeyApiClientTest {
    private fun createMockApiClient(mockEngine: MockEngine): PasskeyApiClient {
        val httpClient =
            HttpClient(mockEngine) {
                install(ContentNegotiation) {
                    json(
                        Json {
                            ignoreUnknownKeys = true
                            encodeDefaults = false
                        },
                    )
                }
            }

        val configRepository = MockConfigRepository()
        return PasskeyApiClient(httpClient, configRepository)
    }

    @Test
    fun `checkUsernameAvailability uses auth v1 path`() =
        runTest {
            val mockEngine =
                MockEngine { request ->
                    assertEquals("/api/v1/auth/signup/username/testuser/available", request.url.encodedPath)
                    respond(
                        content = """{"success": true, "data": {"username": "testuser", "available": true}}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }

            val apiClient = createMockApiClient(mockEngine)
            val result = apiClient.checkUsernameAvailability("testuser")

            assertTrue(result.isSuccess)
            assertEquals("testuser", result.getOrThrow().username)
        }

    @Test
    fun `beginAccountCreation uses auth v1 path`() =
        runTest {
            val mockEngine =
                MockEngine { request ->
                    assertEquals("/api/v1/auth/signup/passkey/begin", request.url.encodedPath)
                    respond(
                        content =
                            """
                            {
                              "success": true,
                                "data": {
                                  "sessionToken": "session123",
                                  "registrationOptions": {
                                    "challenge": "challenge123",
                                    "rpId": "logdate.app",
                                    "rpName": "LogDate",
                                    "user": {"id": "u", "name": "newuser", "displayName": "New User"},
                                    "timeout": 300000
                                  }
                                }
                            }
                            """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }

            val apiClient = createMockApiClient(mockEngine)
            val result = apiClient.beginAccountCreation(BeginAccountCreationRequest("newuser", "New User"))

            assertTrue(result.isSuccess)
            assertEquals("session123", result.getOrThrow().sessionToken)
        }

    @Test
    fun `completeAccountCreation maps auth response`() =
        runTest {
            val mockEngine =
                MockEngine { request ->
                    assertEquals("/api/v1/auth/signup/passkey/complete", request.url.encodedPath)
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
                                "tokens": {"accessToken": "access", "refreshToken": "refresh"}
                              }
                            }
                            """.trimIndent(),
                        status = HttpStatusCode.Created,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }

            val apiClient = createMockApiClient(mockEngine)
            val request =
                CompleteAccountCreationRequest(
                    sessionToken = "session123",
                    credential =
                        PasskeyCredentialResponse(
                            id = "cred-1",
                            rawId = "cred-1",
                            response = PasskeyAuthenticatorResponse("client", "attestation"),
                        ),
                )
            val result = apiClient.completeAccountCreation(request)

            assertTrue(result.isSuccess)
            assertEquals("newuser", result.getOrThrow().account.username)
        }

    @Test
    fun `beginAuthentication uses auth v1 path`() =
        runTest {
            val mockEngine =
                MockEngine { request ->
                    assertEquals("/api/v1/auth/signin/passkey/begin", request.url.encodedPath)
                    respond(
                        content =
                            """
                            {
                              "success": true,
                              "data": {
                                "challenge": "auth_challenge",
                                "rpId": "logdate.app",
                                "allowCredentials": [{"type":"public-key","id":"cred-1","transports":[]}],
                                "timeout": 300000,
                                "userVerification": "required"
                              }
                            }
                            """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }

            val apiClient = createMockApiClient(mockEngine)
            val result = apiClient.beginAuthentication(BeginAuthenticationRequest("testuser"))

            assertTrue(result.isSuccess)
            assertEquals("auth_challenge", result.getOrThrow().challenge)
        }

    @Test
    fun `completeAuthentication uses auth v1 path`() =
        runTest {
            val mockEngine =
                MockEngine { request ->
                    assertEquals("/api/v1/auth/signin/passkey/complete", request.url.encodedPath)
                    respond(
                        content =
                            """
                            {
                              "success": true,
                              "data": {
                                "account": {
                                  "id": "550e8400-e29b-41d4-a716-446655440001",
                                  "username": "testuser",
                                  "displayName": "Test User",
                                  "passkeyCredentialIds": ["cred-1"],
                                  "createdAt": "2026-03-05T00:00:00Z",
                                  "updatedAt": "2026-03-05T00:00:00Z"
                                },
                                "tokens": {"accessToken": "a2", "refreshToken": "r2"}
                              }
                            }
                            """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }

            val apiClient = createMockApiClient(mockEngine)
            val request =
                CompleteAuthenticationRequest(
                    challenge = "auth_challenge",
                    credential =
                        PasskeyAssertionResponse(
                            id = "cred-1",
                            rawId = "cred-1",
                            response = PasskeyAssertionAuthenticatorResponse("client", "auth", "sig", ""),
                        ),
                )
            val result = apiClient.completeAuthentication(request)

            assertTrue(result.isSuccess)
            assertEquals("testuser", result.getOrThrow().account.username)
        }

    @Test
    fun `refreshToken uses auth v1 path`() =
        runTest {
            val mockEngine =
                MockEngine { request ->
                    assertEquals("/api/v1/auth/token/refresh", request.url.encodedPath)
                    respond(
                        content = """{"success":true,"data":{"accessToken":"new-access"}}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }

            val apiClient = createMockApiClient(mockEngine)
            val result = apiClient.refreshToken("refresh")

            assertTrue(result.isSuccess)
            assertEquals("new-access", result.getOrThrow())
        }
}

private class MockConfigRepository : LogDateConfigRepository {
    override val backendUrl: StateFlow<String> = MutableStateFlow("http://localhost")
    override val apiVersion: StateFlow<String> = MutableStateFlow("v1")
    override val apiBaseUrl: Flow<String> = MutableStateFlow("http://localhost/api/v1")
    override val localServerAddress: StateFlow<String> = MutableStateFlow("localhost:8765")
    override val serverDescriptor: StateFlow<app.logdate.shared.model.ServerDescriptor?> = MutableStateFlow(null)

    override suspend fun updateBackendUrl(url: String) = Unit

    override suspend fun updateApiVersion(version: String) = Unit

    override suspend fun updateLocalServerAddress(address: String) = Unit

    override suspend fun updateServerDescriptor(descriptor: app.logdate.shared.model.ServerDescriptor?) = Unit

    override suspend fun resetToDefaults() = Unit

    override fun getCurrentBackendUrl(): String = "http://localhost"

    override fun getCurrentApiBaseUrl(): String = "http://localhost/api/v1"

    override fun getCurrentServerDescriptor(): app.logdate.shared.model.ServerDescriptor? = null
}
