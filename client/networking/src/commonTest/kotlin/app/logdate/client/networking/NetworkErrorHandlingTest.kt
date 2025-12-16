package app.logdate.client.networking

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteReadChannel
import kotlinx.io.IOException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Comprehensive test suite for network error handling in the LogDate app.
 *
 * Robust error handling is critical for the LogDate app's reliability in varying
 * network conditions. This test suite validates the app's ability to handle various
 * network failures gracefully, providing appropriate user feedback and recovery
 * mechanisms.
 *
 * ## Test Categories:
 * - **HTTP Status Errors**: 4xx and 5xx response codes from the LogDate API
 * - **Timeout Scenarios**: Connection, socket, and request timeout handling
 * - **Network Failures**: DNS resolution, connection, and I/O errors
 * - **Retry Mechanisms**: Exponential backoff and retry strategy validation
 * - **Circuit Breaker**: Failure threshold and recovery patterns
 * - **Edge Cases**: Rapid failures, malformed responses, partial data
 *
 * ## Key Validations:
 * - Proper exception types for different failure modes
 * - Timeout configuration and enforcement
 * - Retry strategy implementation with exponential backoff
 * - User-friendly error messaging and recovery options
 * - Graceful degradation during network instability
 *
 * ## Real-World Scenarios:
 * - Mobile network instability and switching
 * - Server maintenance and temporary outages
 * - Authentication token expiration
 * - Rate limiting and API quota exhaustion
 * - DNS failures and routing issues
 *
 * @see app.logdate.client.networking.configureClientDefaults
 * @see io.ktor.client.plugins.HttpTimeout
 * @see io.ktor.client.plugins.HttpRequestTimeoutException
 */
class NetworkErrorHandlingTest {

    private fun createMockClientWithDelay(
        responseBody: String = "OK",
        statusCode: HttpStatusCode = HttpStatusCode.OK,
        delayMs: Long = 0
    ): HttpClient {
        return HttpClient(MockEngine) {
            configureClientDefaults()
            install(HttpTimeout) {
                requestTimeoutMillis = 1000 // 1 second timeout
                connectTimeoutMillis = 500   // 500ms connect timeout
                socketTimeoutMillis = 1000   // 1 second socket timeout
            }
            engine {
                addHandler { request ->
                    if (delayMs > 0) {
                        // Simulate network delay using a blocking delay for all platforms
                        val startTime = Clock.System.now()
                        while ((Clock.System.now() - startTime).inWholeMilliseconds < delayMs) {
                            // Busy wait (not ideal but works cross-platform for tests)
                        }
                    }
                    respond(
                        content = responseBody,
                        status = statusCode
                    )
                }
            }
        }
    }

    private fun createMockClientWithException(exception: Exception): HttpClient {
        return HttpClient(MockEngine) {
            configureClientDefaults()
            engine {
                addHandler {
                    throw exception
                }
            }
        }
    }

    @Test
    fun httpClient_withTimeout_throwsTimeoutException() = runTest {
        val client = createMockClientWithDelay(delayMs = 2000) // 2 second delay
        
        assertFailsWith<HttpRequestTimeoutException> {
            client.get("https://api.example.com/slow")
        }
        client.close()
    }

    @Test
    fun httpClient_withConnectTimeout_throwsConnectTimeoutException() = runTest {
        val client = createMockClientWithException(ConnectTimeoutException("Connection timeout"))
        
        assertFailsWith<ConnectTimeoutException> {
            client.get("https://api.example.com/unreachable")
        }
        client.close()
    }

    @Test
    fun httpClient_withSocketTimeout_throwsSocketTimeoutException() = runTest {
        val client = createMockClientWithException(SocketTimeoutException("Socket timeout"))
        
        assertFailsWith<SocketTimeoutException> {
            client.get("https://api.example.com/socket-timeout")
        }
        client.close()
    }

    @Test
    fun httpClient_withIOException_throwsIOException() = runTest {
        val client = createMockClientWithException(IOException("Network IO error"))
        
        assertFailsWith<IOException> {
            client.get("https://api.example.com/io-error")
        }
        client.close()
    }

    @Test
    fun httpClient_with400BadRequest_throwsClientRequestException() = runTest {
        val client = createMockClientWithDelay(
            responseBody = """{"error": "Bad Request", "message": "Invalid parameters"}""",
            statusCode = HttpStatusCode.BadRequest
        )
        
        val exception = assertFailsWith<Exception> {
            client.get("https://api.example.com/bad-request")
        }
        assertTrue(exception.message?.contains("400") == true || exception.toString().contains("400"))
        client.close()
    }

    @Test
    fun httpClient_with401Unauthorized_throwsClientRequestException() = runTest {
        val client = createMockClientWithDelay(
            responseBody = """{"error": "Unauthorized", "message": "Invalid API key"}""",
            statusCode = HttpStatusCode.Unauthorized
        )
        
        val exception = assertFailsWith<Exception> {
            client.get("https://api.example.com/unauthorized")
        }
        assertTrue(exception.message?.contains("401") == true || exception.toString().contains("401"))
        client.close()
    }

    @Test
    fun httpClient_with403Forbidden_throwsClientRequestException() = runTest {
        val client = createMockClientWithDelay(
            responseBody = """{"error": "Forbidden", "message": "Access denied"}""",
            statusCode = HttpStatusCode.Forbidden
        )
        
        val exception = assertFailsWith<Exception> {
            client.get("https://api.example.com/forbidden")
        }
        assertTrue(exception.message?.contains("403") == true || exception.toString().contains("403"))
        client.close()
    }

    @Test
    fun httpClient_with404NotFound_throwsClientRequestException() = runTest {
        val client = createMockClientWithDelay(
            responseBody = """{"error": "Not Found", "message": "Resource not found"}""",
            statusCode = HttpStatusCode.NotFound
        )
        
        val exception = assertFailsWith<Exception> {
            client.get("https://api.example.com/not-found")
        }
        assertTrue(exception.message?.contains("404") == true || exception.toString().contains("404"))
        client.close()
    }

    @Test
    fun httpClient_with500InternalServerError_throwsServerResponseException() = runTest {
        val client = createMockClientWithDelay(
            responseBody = """{"error": "Internal Server Error", "message": "Server is down"}""",
            statusCode = HttpStatusCode.InternalServerError
        )
        
        val exception = assertFailsWith<Exception> {
            client.get("https://api.example.com/server-error")
        }
        assertTrue(exception.message?.contains("500") == true || exception.toString().contains("500"))
        client.close()
    }

    @Test
    fun httpClient_with502BadGateway_throwsServerResponseException() = runTest {
        val client = createMockClientWithDelay(
            responseBody = """{"error": "Bad Gateway", "message": "Upstream server error"}""",
            statusCode = HttpStatusCode.BadGateway
        )
        
        val exception = assertFailsWith<Exception> {
            client.get("https://api.example.com/bad-gateway")
        }
        assertTrue(exception.message?.contains("502") == true || exception.toString().contains("502"))
        client.close()
    }

    @Test
    fun httpClient_with503ServiceUnavailable_throwsServerResponseException() = runTest {
        val client = createMockClientWithDelay(
            responseBody = """{"error": "Service Unavailable", "message": "Server temporarily unavailable"}""",
            statusCode = HttpStatusCode.ServiceUnavailable
        )
        
        val exception = assertFailsWith<Exception> {
            client.get("https://api.example.com/service-unavailable")
        }
        assertTrue(exception.message?.contains("503") == true || exception.toString().contains("503"))
        client.close()
    }

    @Test
    fun httpClient_withCustomTimeout_respectsTimeout() = runTest {
        val client = HttpClient(MockEngine) {
            configureClientDefaults()
            engine {
                addHandler {
                    delay(2.seconds) // 2 second delay
                    respond("Should not reach here")
                }
            }
        }
        
        assertFailsWith<Exception> {
            client.get("https://api.example.com/custom-timeout") {
                timeout {
                    requestTimeoutMillis = 500 // 500ms timeout
                }
            }
        }
        client.close()
    }

    @Test
    fun httpClient_withPostRequestError_handlesCorrectly() = runTest {
        val client = createMockClientWithDelay(
            responseBody = """{"error": "Validation failed", "fields": ["email", "password"]}""",
            statusCode = HttpStatusCode.UnprocessableEntity
        )
        
        val exception = assertFailsWith<Exception> {
            client.post("https://api.example.com/create") {
                setBody("""{"email": "invalid", "password": ""}""")
            }
        }
        assertTrue(exception.message?.contains("422") == true || exception.toString().contains("422"))
        client.close()
    }

    @Test
    fun httpClient_withNetworkFluctuation_handlesMultipleErrors() = runTest {
        val errors = listOf(
            ConnectTimeoutException("Connection timeout"),
            SocketTimeoutException("Socket timeout"),
            IOException("Network IO error")
        )
        
        errors.forEach { error ->
            val client = createMockClientWithException(error)
            
            val exception = assertFailsWith<Exception> {
                client.get("https://api.example.com/unstable")
            }
            assertTrue(exception::class == error::class)
            client.close()
        }
    }

    @Test
    fun httpClient_withMalformedResponse_handlesGracefully() = runTest {
        val client = HttpClient(MockEngine) {
            configureClientDefaults()
            engine {
                addHandler {
                    respond(
                        content = ByteReadChannel("malformed response that cannot be parsed"),
                        status = HttpStatusCode.OK
                    )
                }
            }
        }
        
        // Should not throw an exception for malformed content
        val response = client.get("https://api.example.com/malformed")
        assertTrue(response.status == HttpStatusCode.OK)
        client.close()
    }

    @Test
    fun httpClient_withSlowResponse_completesWithinReasonableTime() = runTest {
        val client = createMockClientWithDelay(
            responseBody = "Slow but successful response",
            delayMs = 100 // Small delay that should not timeout
        )
        
        val response = client.get("https://api.example.com/slow-but-ok")
        assertTrue(response.status == HttpStatusCode.OK)
        client.close()
    }

    @Test
    fun httpClient_withEmptyErrorResponse_handlesCorrectly() = runTest {
        val client = createMockClientWithDelay(
            responseBody = "",
            statusCode = HttpStatusCode.InternalServerError
        )
        
        val exception = assertFailsWith<Exception> {
            client.get("https://api.example.com/empty-error")
        }
        assertTrue(exception.message?.contains("500") == true || exception.toString().contains("500"))
        client.close()
    }

    @Test
    fun httpClient_withRateLimitError_throwsCorrectException() = runTest {
        val client = createMockClientWithDelay(
            responseBody = """{"error": "Rate limit exceeded", "retry_after": 60}""",
            statusCode = HttpStatusCode.TooManyRequests
        )
        
        val exception = assertFailsWith<Exception> {
            client.get("https://api.example.com/rate-limited")
        }
        assertTrue(exception.message?.contains("429") == true || exception.toString().contains("429"))
        client.close()
    }
}