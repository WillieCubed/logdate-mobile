package app.logdate.client.sync.cloud

import app.logdate.shared.model.AccountTokens
import app.logdate.shared.model.BeginAccountCreationData
import app.logdate.shared.model.BeginAccountCreationRequest
import app.logdate.shared.model.BeginAccountCreationResponse
import app.logdate.shared.model.CompleteAccountCreationData
import app.logdate.shared.model.CompleteAccountCreationRequest
import app.logdate.shared.model.CompleteAccountCreationResponse
import app.logdate.shared.model.LogDateAccount
import app.logdate.shared.model.PasskeyAuthenticatorResponse
import app.logdate.shared.model.PasskeyCredentialResponse
import app.logdate.shared.model.PasskeyRegistrationOptions
import app.logdate.shared.model.PasskeyUser
import app.logdate.shared.model.RefreshTokenData
import app.logdate.shared.model.RefreshTokenResponse
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
import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for [LogDateCloudApiClient].
 */
class BasicCloudApiClientTest {
    private val baseUrl = "https://api.logdate.example.com/v1"
    private val json = Json { 
        ignoreUnknownKeys = true 
        prettyPrint = false
    }

    @Test
    fun testUsernameAvailabilityEndpoint() = runTest {
        // Given
        val username = "testuser"
        val mockEngine = MockEngine { request ->
            // Verify the request URL is correct - now uses RESTful endpoint
            assertEquals("$baseUrl/accounts/username/$username", request.url.toString(), "Request URL should target the username availability endpoint")
            
            // Return 404 Not Found to indicate username is available
            respond(
                content = "",
                status = HttpStatusCode.NotFound,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        
        val client = createApiClient(mockEngine)
        
        // When
        val result = client.checkUsernameAvailability(username)
        
        // Then
        assertTrue(result.isSuccess, "Username availability check should succeed")
        val response = result.getOrNull()
        assertTrue(response != null, "Response object should not be null")
        assertEquals(username, response.username, "Username in response should match requested username")
        assertTrue(response.available == true, "Username should be marked as available")
    }

    @Test
    fun testUsernameAvailabilityFailure() = runTest {
        // Given - a server error
        val mockEngine = MockEngine { request ->
            respond(
                content = """{"error":{"code":"SERVER_ERROR","message":"Internal server error"}}""",
                status = HttpStatusCode.InternalServerError,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        
        val client = createApiClient(mockEngine)
        
        // When
        val result = client.checkUsernameAvailability("testuser")
        
        // Then
        assertFalse(result.isSuccess, "Result should be failure for server error")
        val exception = result.exceptionOrNull()
        assertTrue(exception is CloudApiException, "Exception should be CloudApiException")
        assertEquals("SERVER_ERROR", (exception as CloudApiException).errorCode, "Error code should match server response")
    }

    @Test
    fun testUsernameAlreadyTakenCheck() = runTest {
        // Given - username already exists
        val username = "existinguser"
        val mockEngine = MockEngine { request ->
            // Verify the request URL is correct
            assertEquals("$baseUrl/accounts/username/$username", request.url.toString(), "Request URL should target the username availability endpoint")
            
            // Return 200 OK to indicate username already exists
            respond(
                content = "",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        
        val client = createApiClient(mockEngine)
        
        // When
        val result = client.checkUsernameAvailability(username)
        
        // Then
        assertTrue(result.isSuccess, "Username availability check should succeed even when username exists")
        val response = result.getOrNull()
        assertTrue(response != null, "Response object should not be null")
        assertEquals(username, response.username, "Username in response should match requested username")
        assertFalse(response.available, "Username should be marked as unavailable")
    }
    
    @Test
    fun testRefreshTokenEndpoint() = runTest {
        // Given
        val refreshToken = "test-refresh-token"
        val newAccessToken = "new-access-token"
        
        val mockEngine = MockEngine { request ->
            // Verify it's a POST request to the right endpoint
            assertEquals("$baseUrl/accounts/refresh", request.url.toString(), "Request URL should target the token refresh endpoint")
            
            // Create a successful response
            val responseData = RefreshTokenResponse(
                success = true,
                data = RefreshTokenData(accessToken = newAccessToken)
            )
            
            respond(
                content = json.encodeToString(responseData),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        
        val client = createApiClient(mockEngine)
        
        // When
        val result = client.refreshAccessToken(refreshToken)
        
        // Then
        assertTrue(result.isSuccess, "Token refresh should succeed")
        assertEquals(newAccessToken, result.getOrNull(), "Refreshed access token should match expected token")
    }
    
    @Test
    fun testBeginAccountCreation() = runTest {
        // Given
        val request = BeginAccountCreationRequest(
            username = "newuser",
            displayName = "New User",
            bio = "Testing the API"
        )
        
        val sessionToken = "test-session-token"
        val challenge = "test-challenge-bytes"
        
        val mockEngine = MockEngine { req ->
            // Verify the request URL is correct
            assertEquals("$baseUrl/accounts/create/begin", req.url.toString(), "Request URL should target the begin account creation endpoint")
            
            // Create a successful response
            val responseData = ApiResponse(
                success = true,
                data = BeginAccountCreationResponse(
                    success = true,
                    data = BeginAccountCreationData(
                        sessionToken = sessionToken,
                        registrationOptions = PasskeyRegistrationOptions(
                            challenge = challenge,
                            user = PasskeyUser(
                                id = "user-id-123",
                                name = request.username,
                                displayName = request.displayName
                            ),
                            excludeCredentials = emptyList(),
                            timeout = 60000
                        )
                    )
                )
            )
            
            respond(
                content = json.encodeToString(responseData),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        
        val client = createApiClient(mockEngine)
        
        // When
        val result = client.beginAccountCreation(request)
        
        // Then
        assertTrue(result.isSuccess, "Account creation initiation should succeed")
        val response = result.getOrNull()
        assertTrue(response != null, "Response object should not be null")
        assertEquals(sessionToken, response?.data?.sessionToken, "Session token should match expected value")
        assertEquals(challenge, response?.data?.registrationOptions?.challenge, "Challenge should match expected value")
    }
    
    @Test
    fun testCompleteAccountCreation() = runTest {
        // Given
        val sessionToken = "test-session-token"
        val credential = PasskeyCredentialResponse(
            id = "credential-id-123",
            rawId = "raw-id-bytes",
            response = PasskeyAuthenticatorResponse(
                clientDataJSON = "client-data-json",
                attestationObject = "attestation-object-bytes"
            ),
            type = "public-key"
        )
        
        val request = CompleteAccountCreationRequest(
            sessionToken = sessionToken,
            credential = credential
        )
        
        val mockEngine = MockEngine { req ->
            // Verify the request URL is correct
            assertEquals("$baseUrl/accounts/create/complete", req.url.toString(), "Request URL should target the complete account creation endpoint")
            
            // Create a successful response
            val responseData = ApiResponse(
                success = true,
                data = CompleteAccountCreationResponse(
                    success = true,
                    data = CompleteAccountCreationData(
                        account = LogDateAccount(
                            username = "newuser",
                            displayName = "New User",
                            bio = "Testing the API",
                            passkeyCredentialIds = listOf("credential-id-123"),
                            createdAt = Clock.System.now(),
                            updatedAt = Clock.System.now()
                        ),
                        tokens = AccountTokens(
                            accessToken = "new-access-token",
                            refreshToken = "new-refresh-token"
                        )
                    )
                )
            )
            
            respond(
                content = json.encodeToString(responseData),
                status = HttpStatusCode.Created,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        
        val client = createApiClient(mockEngine)
        
        // When
        val result = client.completeAccountCreation(request)
        
        // Then
        assertTrue(result.isSuccess, "Account creation completion should succeed")
        val response = result.getOrNull()
        assertTrue(response != null, "Response object should not be null")
        assertEquals("newuser", response?.data?.account?.username, "Username should match expected value")
        assertEquals("new-access-token", response?.data?.tokens?.accessToken, "Access token should match expected value")
        assertEquals("new-refresh-token", response?.data?.tokens?.refreshToken, "Refresh token should match expected value")
    }
    
    @Test
    fun testGetAccountInfo() = runTest {
        // Given
        val accessToken = "test-access-token"
        
        val mockEngine = MockEngine { request ->
            // Verify the request URL is correct
            assertEquals("$baseUrl/accounts/me", request.url.toString(), "Request URL should target the account info endpoint")
            
            // Verify authorization header is included
            assertEquals("Bearer $accessToken", request.headers["Authorization"] ?: "")
            // Also check that Authorization header exists
            assertTrue(request.headers.contains("Authorization"), "Authorization header should exist")
            
            // No debugging printlns - using descriptive assertion messages instead
            
            // Create a successful response with explicit data
            val responseData = ApiResponse(
                success = true,
                data = AccountInfoResponse(
                    id = "user-123",
                    username = "testuser",
                    displayName = "Test User",
                    bio = "User profile for testing",
                    passkeyCredentialIds = listOf("credential-123"),
                    createdAt = "2025-01-01T00:00:00Z",
                    updatedAt = "2025-01-02T00:00:00Z"
                )
            )
            
            respond(
                content = json.encodeToString(responseData),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        
        val client = createApiClient(mockEngine)
        
        // When
        val result = client.getAccountInfo(accessToken)
        
        // Then
        assertTrue(result.isSuccess, "API call should succeed, but got: ${result.exceptionOrNull()}")
        val response = result.getOrNull()
        assertTrue(response != null, "Response should not be null")
        assertEquals("user-123", response?.id, "User ID should match")
        assertEquals("testuser", response?.username, "Username should match")
        assertEquals("Test User", response?.displayName, "Display name should match")
    }
    
    private fun createApiClient(mockEngine: MockEngine): LogDateCloudApiClient {
        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    prettyPrint = false
                })
            }
        }
        
        return LogDateCloudApiClient(baseUrl, httpClient)
    }
}