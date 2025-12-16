package app.logdate.client.networking

import app.logdate.shared.config.LogDateConfigRepository
import app.logdate.shared.model.*
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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class PasskeyApiClientTest {

    private fun createMockApiClient(mockEngine: MockEngine): PasskeyApiClient {
        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    encodeDefaults = false
                })
            }
        }
        
        val configRepository = MockConfigRepository()
        
        return PasskeyApiClient(httpClient, configRepository)
    }

    @Test
    fun `checkUsernameAvailability returns success when username is available`() = runTest {
        val mockEngine = MockEngine { request ->
            assertEquals("/accounts/username/testuser/available", request.url.encodedPath)
            respond(
                content = """{"success": true, "data": {"username": "testuser", "available": true}}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        
        val apiClient = createMockApiClient(mockEngine)
        val result = apiClient.checkUsernameAvailability("testuser")
        
        assertTrue(result.isSuccess)
        val data = result.getOrThrow()
        assertTrue(data.available)
        assertEquals("testuser", data.username)
    }

    @Test
    fun `checkUsernameAvailability returns success when username is taken`() = runTest {
        val mockEngine = MockEngine { request ->
            assertEquals("/accounts/username/existinguser/available", request.url.encodedPath)
            respond(
                content = """{"success": true, "data": {"username": "existinguser", "available": false}}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        
        val apiClient = createMockApiClient(mockEngine)
        val result = apiClient.checkUsernameAvailability("existinguser")
        
        assertTrue(result.isSuccess)
        val data = result.getOrThrow()
        assertTrue(!data.available)
        assertEquals("existinguser", data.username)
    }

    @Test
    fun `checkUsernameAvailability handles server error`() = runTest {
        val mockEngine = MockEngine { request ->
            respond(
                content = """{"error": {"code": "INTERNAL_ERROR", "message": "Database unavailable"}}""",
                status = HttpStatusCode.InternalServerError,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        
        val apiClient = createMockApiClient(mockEngine)
        val result = apiClient.checkUsernameAvailability("testuser")
        
        assertTrue(result.isFailure)
    }

    @Test
    fun `beginAccountCreation returns success with registration options`() = runTest {
        val testAccount = LogDateAccount(
            id = Uuid.random(),
            username = "newuser",
            displayName = "New User",
            bio = "Test bio",
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now()
        )
        
        val mockEngine = MockEngine { request ->
            assertEquals("/accounts/create/begin", request.url.encodedPath)
            assertEquals("POST", request.method.value)
            respond(
                content = """
                {
                    "success": true,
                    "data": {
                        "sessionToken": "session123",
                        "registrationOptions": {
                            "challenge": "challenge123",
                            "user": {
                                "id": "user123",
                                "name": "newuser",
                                "displayName": "New User"
                            },
                            "timeout": 300000
                        }
                    }
                }
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        
        val apiClient = createMockApiClient(mockEngine)
        val request = BeginAccountCreationRequest(
            username = "newuser",
            displayName = "New User",
            bio = "Test bio"
        )
        val result = apiClient.beginAccountCreation(request)
        
        assertTrue(result.isSuccess)
        val data = result.getOrThrow()
        assertEquals("session123", data.sessionToken)
        assertEquals("challenge123", data.registrationOptions.challenge)
        assertEquals("newuser", data.registrationOptions.user.name)
    }

    @Test
    fun `completeAccountCreation returns success with account and tokens`() = runTest {
        val testAccount = LogDateAccount(
            id = Uuid.random(),
            username = "newuser",
            displayName = "New User",
            bio = "Test bio",
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now()
        )
        
        val mockEngine = MockEngine { request ->
            assertEquals("/accounts/create/complete", request.url.encodedPath)
            assertEquals("POST", request.method.value)
            respond(
                content = """
                {
                    "success": true,
                    "data": {
                        "account": {
                            "id": "${testAccount.id}",
                            "username": "newuser",
                            "displayName": "New User",
                            "bio": "Test bio",
                            "createdAt": "${testAccount.createdAt}",
                            "updatedAt": "${testAccount.updatedAt}"
                        },
                        "tokens": {
                            "accessToken": "access_token_123",
                            "refreshToken": "refresh_token_123"
                        }
                    }
                }
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        
        val apiClient = createMockApiClient(mockEngine)
        val credential = PasskeyCredentialResponse(
            id = "credential123",
            rawId = "credential123",
            response = PasskeyAuthenticatorResponse(
                clientDataJSON = "clientData",
                attestationObject = "attestation"
            )
        )
        val request = CompleteAccountCreationRequest(
            sessionToken = "session123",
            credential = credential
        )
        val result = apiClient.completeAccountCreation(request)
        
        assertTrue(result.isSuccess)
        val data = result.getOrThrow()
        assertEquals("newuser", data.account.username)
        assertEquals("access_token_123", data.tokens.accessToken)
        assertEquals("refresh_token_123", data.tokens.refreshToken)
    }

    @Test
    fun `beginAuthentication returns success with authentication options`() = runTest {
        val mockEngine = MockEngine { request ->
            assertEquals("/accounts/authenticate/begin", request.url.encodedPath)
            assertEquals("POST", request.method.value)
            respond(
                content = """
                {
                    "success": true,
                    "data": {
                        "challenge": "auth_challenge_123",
                        "rpId": "logdate.app",
                        "allowCredentials": [
                            {
                                "type": "public-key",
                                "id": "credential123",
                                "transports": ["internal"]
                            }
                        ],
                        "timeout": 300000,
                        "userVerification": "preferred"
                    }
                }
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        
        val apiClient = createMockApiClient(mockEngine)
        val request = BeginAuthenticationRequest(username = "testuser")
        val result = apiClient.beginAuthentication(request)
        
        assertTrue(result.isSuccess)
        val data = result.getOrThrow()
        assertEquals("auth_challenge_123", data.challenge)
        assertEquals("logdate.app", data.rpId)
        assertEquals(1, data.allowCredentials.size)
        assertEquals("credential123", data.allowCredentials[0].id)
    }

    @Test
    fun `completeAuthentication returns success with account and tokens`() = runTest {
        val testAccount = LogDateAccount(
            id = Uuid.random(),
            username = "testuser",
            displayName = "Test User",
            bio = "Test bio",
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now()
        )
        
        val mockEngine = MockEngine { request ->
            assertEquals("/accounts/authenticate/complete", request.url.encodedPath)
            assertEquals("POST", request.method.value)
            respond(
                content = """
                {
                    "success": true,
                    "data": {
                        "account": {
                            "id": "${testAccount.id}",
                            "username": "testuser",
                            "displayName": "Test User",
                            "bio": "Test bio",
                            "createdAt": "${testAccount.createdAt}",
                            "updatedAt": "${testAccount.updatedAt}"
                        },
                        "tokens": {
                            "accessToken": "new_access_token",
                            "refreshToken": "new_refresh_token"
                        }
                    }
                }
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        
        val apiClient = createMockApiClient(mockEngine)
        val assertion = PasskeyAssertionResponse(
            id = "credential123",
            rawId = "credential123",
            response = PasskeyAssertionAuthenticatorResponse(
                clientDataJSON = "clientData",
                authenticatorData = "authData",
                signature = "signature",
                userHandle = "userHandle"
            )
        )
        val request = CompleteAuthenticationRequest(
            credential = assertion,
            challenge = "challenge123"
        )
        val result = apiClient.completeAuthentication(request)
        
        assertTrue(result.isSuccess)
        val data = result.getOrThrow()
        assertEquals("testuser", data.account.username)
        assertEquals("new_access_token", data.tokens.accessToken)
    }

    @Test
    fun `refreshToken succeeds with valid refresh token`() = runTest {
        val mockEngine = MockEngine { request ->
            assertEquals("/accounts/refresh", request.url.encodedPath)
            assertEquals("POST", request.method.value)
            respond(
                content = """{
                    "success": true,
                    "data": {
                        "accessToken": "new_access_token_456"
                    }
                }""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        
        val apiClient = createMockApiClient(mockEngine)
        val result = apiClient.refreshToken("refresh_token_123")
        
        assertTrue(result.isSuccess)
        assertEquals("new_access_token_456", result.getOrThrow())
    }

    @Test
    fun `refreshToken fails with invalid refresh token`() = runTest {
        val mockEngine = MockEngine { request ->
            respond(
                content = """{"error": {"code": "INVALID_REFRESH_TOKEN", "message": "Refresh token expired"}}""",
                status = HttpStatusCode.Unauthorized,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        
        val apiClient = createMockApiClient(mockEngine)
        val result = apiClient.refreshToken("invalid_token")
        
        assertTrue(result.isFailure)
    }

    @Test
    fun `getAccountInfo succeeds with valid access token`() = runTest {
        val testAccount = LogDateAccount(
            id = Uuid.random(),
            username = "testuser",
            displayName = "Test User",
            bio = "Test bio",
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now()
        )
        
        val mockEngine = MockEngine { request ->
            assertEquals("/accounts/me", request.url.encodedPath)
            assertEquals("GET", request.method.value)
            assertEquals("Bearer access_token_123", request.headers["Authorization"])
            respond(
                content = """
                {
                    "id": "${testAccount.id}",
                    "username": "testuser",
                    "displayName": "Test User",
                    "bio": "Test bio",
                    "createdAt": "${testAccount.createdAt}",
                    "updatedAt": "${testAccount.updatedAt}"
                }
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        
        val apiClient = createMockApiClient(mockEngine)
        val result = apiClient.getAccountInfo("access_token_123")
        
        assertTrue(result.isSuccess)
        val account = result.getOrThrow()
        assertEquals("testuser", account.username)
        assertEquals("Test User", account.displayName)
    }

    @Test
    fun `getAccountInfo fails with invalid access token`() = runTest {
        val mockEngine = MockEngine { request ->
            respond(
                content = """{"error": {"code": "UNAUTHORIZED", "message": "Invalid access token"}}""",
                status = HttpStatusCode.Unauthorized,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        
        val apiClient = createMockApiClient(mockEngine)
        val result = apiClient.getAccountInfo("invalid_token")
        
        assertTrue(result.isFailure)
    }

    @Test
    fun `deletePasskey succeeds with valid token and credential ID`() = runTest {
        val mockEngine = MockEngine { request ->
            assertEquals("/passkeys/cred123", request.url.encodedPath)
            assertEquals("DELETE", request.method.value)
            assertEquals("Bearer access123", request.headers["Authorization"])
            respond(
                content = """{"success": true}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        
        val apiClient = createMockApiClient(mockEngine)
        val result = apiClient.deletePasskey("access123", "cred123")
        
        assertTrue(result.isSuccess)
    }

    @Test
    fun `deletePasskey fails with invalid credentials`() = runTest {
        val mockEngine = MockEngine { request ->
            respond(
                content = """{"error": {"code": "NOT_FOUND", "message": "Passkey not found"}}""",
                status = HttpStatusCode.NotFound,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        
        val apiClient = createMockApiClient(mockEngine)
        val result = apiClient.deletePasskey("invalid_token", "invalid_cred")
        
        assertTrue(result.isFailure)
    }

    private class MockConfigRepository : LogDateConfigRepository {
        override val backendUrl: StateFlow<String> = MutableStateFlow("https://api.test.logdate.app").asStateFlow()
        override val apiVersion: StateFlow<String> = MutableStateFlow("v1").asStateFlow()
        override val apiBaseUrl: Flow<String> = MutableStateFlow("https://api.test.logdate.app/api/v1").asStateFlow()
        
        override suspend fun updateBackendUrl(url: String) {}
        override suspend fun updateApiVersion(version: String) {}
        override suspend fun resetToDefaults() {}
        
        override fun getCurrentBackendUrl(): String = "https://api.test.logdate.app"
        override fun getCurrentApiBaseUrl(): String = "https://api.test.logdate.app/api/v1"
    }
}