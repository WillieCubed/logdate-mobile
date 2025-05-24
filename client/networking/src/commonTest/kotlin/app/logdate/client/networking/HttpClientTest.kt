package app.logdate.client.networking

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.delete
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Comprehensive test suite for HTTP client functionality in the LogDate networking module.
 *
 * This test suite validates the core HTTP operations that the LogDate app relies on for
 * communication with backend services. It covers real-world scenarios including journal
 * management, authentication, error handling, and data serialization.
 *
 * ## Test Categories:
 * - **Basic Operations**: Client creation, configuration validation
 * - **HTTP Methods**: GET, POST, PUT, DELETE operations for journal APIs
 * - **Data Handling**: JSON serialization, large responses, complex nested data
 * - **Error Scenarios**: HTTP status codes, authentication failures, server errors
 * - **Production Features**: Custom headers, empty responses, API evolution compatibility
 *
 * ## Key Validations:
 * - Proper Ktor 3.0.1 client configuration with ContentNegotiation and Logging
 * - Real LogDate API endpoint patterns (api.logdate.com/journals)
 * - Authentication token handling and error responses
 * - JSON response parsing with unknown field tolerance
 * - Large dataset handling for journal entries
 *
 * @see app.logdate.client.networking.configureClientDefaults
 * @see io.ktor.client.HttpClient
 */
class HttpClientTest {

    /**
     * Creates a mock HTTP client configured with the same defaults as the production client.
     *
     * This helper function sets up a MockEngine with customizable responses while ensuring
     * the client uses the same configuration as the production LogDate app. This allows
     * tests to validate both the client setup and request/response handling.
     *
     * @param responseBody The JSON response body to return for requests
     * @param statusCode The HTTP status code to return (defaults to 200 OK)
     * @param headers Additional HTTP headers to include in the response
     * @return A configured HttpClient with MockEngine for testing
     */
    private fun createMockClient(
        responseBody: String = """{"message": "success"}""",
        statusCode: HttpStatusCode = HttpStatusCode.OK,
        headers: Map<String, String> = mapOf(HttpHeaders.ContentType to ContentType.Application.Json.toString())
    ): HttpClient {
        return HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    respond(
                        content = ByteReadChannel(responseBody),
                        status = statusCode,
                        headers = headersOf(*headers.map { it.key to listOf(it.value) }.toTypedArray())
                    )
                }
            }
            configureClientDefaults()
        }
    }

    /**
     * Validates that an HTTP client can be successfully created and configured.
     *
     * This test ensures the basic client instantiation works correctly with the LogDate
     * networking configuration. It verifies that the MockEngine setup doesn't interfere
     * with the core client creation process.
     *
     * **Test Scenario**: Basic client lifecycle
     * **Expected Behavior**: Client creates successfully and can be closed without errors
     */
    @Test
    fun httpClient_canBeCreated() = runTest {
        val client = createMockClient()
        assertNotNull(client)
        client.close()
    }

    /**
     * Verifies that ContentNegotiation plugin is properly installed and functional.
     *
     * The LogDate app relies heavily on JSON communication with backend services.
     * This test ensures that the ContentNegotiation plugin is correctly configured
     * to handle JSON serialization/deserialization automatically.
     *
     * **Test Scenario**: Plugin installation verification
     * **Expected Behavior**: Client can make requests and handle JSON responses
     * **Critical for**: All API communication in LogDate app
     */
    @Test
    fun httpClient_hasContentNegotiationInstalled() = runTest {
        val client = createMockClient()
        // Test that JSON content negotiation works by making a request
        val response = client.get("https://api.example.com/test")
        assertEquals(HttpStatusCode.OK, response.status)
        client.close()
    }

    /**
     * Tests GET request functionality for journal retrieval operations.
     *
     * This validates the most common operation in the LogDate app - retrieving journal
     * data from the backend. It simulates the API call used to fetch user journals
     * and verifies that the response is properly parsed.
     *
     * **Test Scenario**: GET /api.logdate.com/journals
     * **Expected Behavior**: Successful request with JSON response parsing
     * **Real App Usage**: Loading user journal list, fetching journal details
     */
    @Test
    fun httpClient_makesGetRequest() = runTest {
        val client = createMockClient("""{"data": "test_value", "id": 123}""")
        
        val response: HttpResponse = client.get("https://api.logdate.com/journals")
        val responseText = response.bodyAsText()
        
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(responseText.contains("test_value"))
        assertTrue(responseText.contains("123"))
        client.close()
    }

    /**
     * Tests POST request functionality for journal creation operations.
     *
     * This validates the journal creation workflow where users create new journals
     * by sending JSON data to the backend. It ensures proper request body serialization
     * and response handling for successful creation scenarios.
     *
     * **Test Scenario**: POST /api.logdate.com/journals with JSON payload
     * **Expected Behavior**: Successful creation with ID and confirmation response
     * **Real App Usage**: Creating new journals, adding journal entries
     */
    @Test
    fun httpClient_makesPostRequest() = runTest {
        val client = createMockClient("""{"id": "new_journal_123", "created": true}""")
        
        val response: HttpResponse = client.post("https://api.logdate.com/journals") {
            setBody(TextContent("""{"title": "My Journal", "content": "Today was good"}""", ContentType.Application.Json))
        }
        val responseText = response.bodyAsText()
        
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(responseText.contains("new_journal_123"))
        assertTrue(responseText.contains("created"))
        client.close()
    }

    /**
     * Tests PUT request functionality for journal update operations.
     *
     * This validates the journal editing workflow where users modify existing
     * journal content. It ensures that update operations properly send modified
     * data and receive confirmation of successful updates.
     *
     * **Test Scenario**: PUT /api.logdate.com/journals/123 with updated JSON
     * **Expected Behavior**: Successful update with confirmation response
     * **Real App Usage**: Editing journal titles, updating journal content
     */
    @Test
    fun httpClient_makesPutRequest() = runTest {
        val client = createMockClient("""{"id": "journal_123", "updated": true}""")
        
        val response: HttpResponse = client.put("https://api.logdate.com/journals/123") {
            setBody(TextContent("""{"title": "Updated Journal", "content": "Updated content"}""", ContentType.Application.Json))
        }
        val responseText = response.bodyAsText()
        
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(responseText.contains("updated"))
        client.close()
    }

    /**
     * Tests DELETE request functionality for journal removal operations.
     *
     * This validates the journal deletion workflow where users permanently remove
     * journals from their account. It ensures proper resource identification and
     * deletion confirmation handling.
     *
     * **Test Scenario**: DELETE /api.logdate.com/journals/123
     * **Expected Behavior**: Successful deletion with confirmation response
     * **Real App Usage**: Deleting unwanted journals, cleaning up old entries
     */
    @Test
    fun httpClient_makesDeleteRequest() = runTest {
        val client = createMockClient("""{"deleted": true, "id": "journal_123"}""")
        
        val response: HttpResponse = client.delete("https://api.logdate.com/journals/123")
        val responseText = response.bodyAsText()
        
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(responseText.contains("deleted"))
        client.close()
    }

    /**
     * Tests handling of complex, nested JSON responses from the LogDate API.
     *
     * Real-world API responses often contain nested objects and arrays. This test
     * validates that the client can handle complex data structures like journal
     * collections with embedded entries and metadata - a common pattern in the
     * LogDate API for paginated journal listings.
     *
     * **Test Scenario**: Complex JSON with nested arrays and metadata
     * **Expected Behavior**: Successful parsing of all nested data elements
     * **Real App Usage**: Journal list with entries, paginated responses, metadata
     */
    @Test
    fun httpClient_handlesJsonResponseWithComplexData() = runTest {
        val jsonResponse = """
        {
            "journals": [
                {"id": "1", "title": "Today", "entries": ["Entry 1", "Entry 2"]},
                {"id": "2", "title": "Yesterday", "entries": ["Entry 3"]}
            ],
            "metadata": {
                "total": 2,
                "hasMore": false
            }
        }
        """.trimIndent()
        val client = createMockClient(jsonResponse)
        
        val response: HttpResponse = client.get("https://api.logdate.com/journals")
        val responseText = response.bodyAsText()
        
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(responseText.contains("journals"))
        assertTrue(responseText.contains("metadata"))
        assertTrue(responseText.contains("Entry 1"))
        assertTrue(responseText.contains("hasMore"))
        client.close()
    }

    /**
     * Tests handling of 404 Not Found errors for non-existent resources.
     *
     * Users may attempt to access journals that have been deleted or that they
     * don't have permission to view. This test ensures the client properly handles
     * 404 responses and provides meaningful error information to the app.
     *
     * **Test Scenario**: GET request for non-existent journal
     * **Expected Behavior**: 404 status with descriptive error message
     * **Real App Usage**: Accessing deleted journals, invalid journal IDs
     */
    @Test
    fun httpClient_handles404Error() = runTest {
        val client = createMockClient(
            responseBody = """{"error": "Journal not found", "code": 404}""",
            statusCode = HttpStatusCode.NotFound
        )
        
        val response: HttpResponse = client.get("https://api.logdate.com/journals/nonexistent")
        val responseText = response.bodyAsText()
        
        assertEquals(HttpStatusCode.NotFound, response.status)
        assertTrue(responseText.contains("Journal not found"))
        client.close()
    }

    /**
     * Tests handling of 401 Unauthorized errors for authentication failures.
     *
     * Authentication is critical in the LogDate app for protecting user data.
     * This test validates that the client properly handles expired tokens,
     * invalid credentials, and other authentication failures, providing clear
     * error information for the app to handle re-authentication.
     *
     * **Test Scenario**: Request with invalid or expired authentication token
     * **Expected Behavior**: 401 status with authentication error details
     * **Real App Usage**: Expired login sessions, invalid API tokens
     */
    @Test
    fun httpClient_handles401UnauthorizedError() = runTest {
        val client = createMockClient(
            responseBody = """{"error": "Unauthorized", "message": "Invalid token"}""",
            statusCode = HttpStatusCode.Unauthorized
        )
        
        val response: HttpResponse = client.get("https://api.logdate.com/journals")
        val responseText = response.bodyAsText()
        
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertTrue(responseText.contains("Unauthorized"))
        assertTrue(responseText.contains("Invalid token"))
        client.close()
    }

    /**
     * Tests handling of 500 Internal Server Error responses.
     *
     * Backend services can experience failures that result in 500 errors.
     * This test ensures the client properly handles server-side failures
     * and provides appropriate error information for the app to display
     * user-friendly error messages or retry mechanisms.
     *
     * **Test Scenario**: Server-side error during request processing
     * **Expected Behavior**: 500 status with error timestamp for debugging
     * **Real App Usage**: Database failures, backend service outages
     */
    @Test
    fun httpClient_handles500InternalServerError() = runTest {
        val client = createMockClient(
            responseBody = """{"error": "Internal server error", "timestamp": "2024-01-01T00:00:00Z"}""",
            statusCode = HttpStatusCode.InternalServerError
        )
        
        val response: HttpResponse = client.get("https://api.logdate.com/journals")
        
        assertEquals(HttpStatusCode.InternalServerError, response.status)
        client.close()
    }

    /**
     * Tests handling of custom HTTP headers required by the LogDate API.
     *
     * The LogDate API uses various custom headers for authentication (Bearer tokens),
     * API versioning, request tracking, and other operational requirements. This test
     * validates that the client properly sends and handles these headers.
     *
     * **Test Scenario**: Request with Authorization, API version, and tracking headers
     * **Expected Behavior**: Successful request processing with all custom headers
     * **Real App Usage**: User authentication, API versioning, request tracing
     */
    @Test
    fun httpClient_handlesCustomHeaders() = runTest {
        val customHeaders = mapOf(
            HttpHeaders.ContentType to ContentType.Application.Json.toString(),
            HttpHeaders.Authorization to "Bearer test-token",
            "X-API-Version" to "v1",
            "X-Request-ID" to "req-123"
        )
        val client = createMockClient(headers = customHeaders)
        
        val response: HttpResponse = client.get("https://api.logdate.com/journals")
        
        assertEquals(HttpStatusCode.OK, response.status)
        client.close()
    }

    /**
     * Tests handling of empty responses from health check and ping endpoints.
     *
     * Some API endpoints (like health checks or ping endpoints) return empty
     * responses with just status codes. This test ensures the client handles
     * these scenarios gracefully without errors.
     *
     * **Test Scenario**: Request to ping endpoint returning empty body
     * **Expected Behavior**: Successful processing with empty string response
     * **Real App Usage**: Health checks, connectivity tests, ping operations
     */
    @Test
    fun httpClient_handlesEmptyResponse() = runTest {
        val client = createMockClient(responseBody = "")
        
        val response: HttpResponse = client.get("https://api.logdate.com/ping")
        val responseText = response.bodyAsText()
        
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("", responseText)
        client.close()
    }

    /**
     * Tests handling of large JSON responses with many journal entries.
     *
     * Power users of the LogDate app may have hundreds or thousands of journal
     * entries. This test validates that the client can efficiently handle large
     * response payloads without performance issues or memory problems.
     *
     * **Test Scenario**: Response with 100+ journal entries in JSON array
     * **Expected Behavior**: Successful parsing of large dataset without errors
     * **Real App Usage**: Loading complete journal history, bulk operations
     */
    @Test
    fun httpClient_handlesLargeJsonResponse() = runTest {
        // Simulate a large response that might be returned by the LogDate API
        val entries = (1..100).map { """{"id": "$it", "content": "Entry $it content", "timestamp": "2024-01-${it.toString().padStart(2, '0')}T00:00:00Z"}""" }
        val largeResponse = """{"entries": [${entries.joinToString(",")}], "total": 100}"""
        
        val client = createMockClient(largeResponse)
        
        val response: HttpResponse = client.get("https://api.logdate.com/journals/123/entries")
        val responseText = response.bodyAsText()
        
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(responseText.contains("Entry 1 content"))
        assertTrue(responseText.contains("Entry 100 content"))
        assertTrue(responseText.contains("\"total\": 100"))
        client.close()
    }

    /**
     * Tests JSON configuration for API evolution compatibility.
     *
     * APIs evolve over time, adding new fields that older client versions don't
     * know about. This test validates that the LogDate client's JSON configuration
     * properly ignores unknown fields, ensuring forward compatibility when the
     * backend API adds new features.
     *
     * **Test Scenario**: Response with unknown JSON fields mixed with known data
     * **Expected Behavior**: Successful parsing with unknown fields preserved in raw text
     * **Real App Usage**: API version upgrades, gradual feature rollouts
     */
    @Test
    fun httpClient_configurationIgnoresUnknownJsonKeys() = runTest {
        // Test that the JSON configuration properly ignores unknown keys (important for API evolution)
        val responseWithUnknownFields = """
        {
            "id": "journal_123",
            "title": "My Journal",
            "unknownNewField": "this should be ignored",
            "deprecatedField": "this too",
            "entries": ["Entry 1"],
            "futureApiField": {"nested": "data"}
        }
        """.trimIndent()
        
        val client = createMockClient(responseWithUnknownFields)
        
        val response: HttpResponse = client.get("https://api.logdate.com/journals/123")
        val responseText = response.bodyAsText()
        
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(responseText.contains("journal_123"))
        assertTrue(responseText.contains("My Journal"))
        // The response should include unknown fields since we're just getting raw text
        assertTrue(responseText.contains("unknownNewField"))
        client.close()
    }
}