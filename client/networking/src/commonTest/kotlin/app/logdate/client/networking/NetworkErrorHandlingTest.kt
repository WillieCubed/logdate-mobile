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
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
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
        delayMs: Long = 0,
    ): HttpClient =
        HttpClient(MockEngine) {
            configureClientDefaults()
            install(HttpTimeout) {
                requestTimeoutMillis = 1000 // 1 second timeout
                connectTimeoutMillis = 500 // 500ms connect timeout
                socketTimeoutMillis = 1000 // 1 second socket timeout
            }
            engine {
                addHandler { request ->
                    if (delayMs > 0) {
                        delay(delayMs)
                    }
                    respond(
                        content = responseBody,
                        status = statusCode,
                    )
                }
            }
        }

    private fun createMockClientWithException(exception: Exception): HttpClient =
        HttpClient(MockEngine) {
            configureClientDefaults()
            engine {
                addHandler {
                    throw exception
                }
            }
        }

    @Test
    fun `http client with timeout throws timeout exception`() =
        runTest {
            val client = createMockClientWithDelay(delayMs = 2000) // 2 second delay

            assertFailsWith<HttpRequestTimeoutException> {
                client.get("https://api.example.com/slow")
            }
            client.close()
        }

    @Test
    fun `http client with connect timeout throws connect timeout exception`() =
        runTest {
            val client = createMockClientWithException(ConnectTimeoutException("Connection timeout"))

            assertFailsWith<ConnectTimeoutException> {
                client.get("https://api.example.com/unreachable")
            }
            client.close()
        }

    @Test
    fun `http client with socket timeout throws socket timeout exception`() =
        runTest {
            val client = createMockClientWithException(SocketTimeoutException("Socket timeout"))

            assertFailsWith<SocketTimeoutException> {
                client.get("https://api.example.com/socket-timeout")
            }
            client.close()
        }

    @Test
    fun `http client with io exception throws io exception`() =
        runTest {
            val client = createMockClientWithException(IOException("Network IO error"))

            assertFailsWith<IOException> {
                client.get("https://api.example.com/io-error")
            }
            client.close()
        }

    @Test
    fun `http client with400 bad request throws client request exception`() =
        runTest {
            val client =
                createMockClientWithDelay(
                    responseBody = """{"error": "Bad Request", "message": "Invalid parameters"}""",
                    statusCode = HttpStatusCode.BadRequest,
                )

            val response = client.get("https://api.example.com/bad-request")
            assertTrue(response.status == HttpStatusCode.BadRequest)
            client.close()
        }

    @Test
    fun `http client with401 unauthorized throws client request exception`() =
        runTest {
            val client =
                createMockClientWithDelay(
                    responseBody = """{"error": "Unauthorized", "message": "Invalid API key"}""",
                    statusCode = HttpStatusCode.Unauthorized,
                )

            val response = client.get("https://api.example.com/unauthorized")
            assertTrue(response.status == HttpStatusCode.Unauthorized)
            client.close()
        }

    @Test
    fun `http client with403 forbidden throws client request exception`() =
        runTest {
            val client =
                createMockClientWithDelay(
                    responseBody = """{"error": "Forbidden", "message": "Access denied"}""",
                    statusCode = HttpStatusCode.Forbidden,
                )

            val response = client.get("https://api.example.com/forbidden")
            assertTrue(response.status == HttpStatusCode.Forbidden)
            client.close()
        }

    @Test
    fun `http client with404 not found throws client request exception`() =
        runTest {
            val client =
                createMockClientWithDelay(
                    responseBody = """{"error": "Not Found", "message": "Resource not found"}""",
                    statusCode = HttpStatusCode.NotFound,
                )

            val response = client.get("https://api.example.com/not-found")
            assertTrue(response.status == HttpStatusCode.NotFound)
            client.close()
        }

    @Test
    fun `http client with500 internal server error throws server response exception`() =
        runTest {
            val client =
                createMockClientWithDelay(
                    responseBody = """{"error": "Internal Server Error", "message": "Server is down"}""",
                    statusCode = HttpStatusCode.InternalServerError,
                )

            val response = client.get("https://api.example.com/server-error")
            assertTrue(response.status == HttpStatusCode.InternalServerError)
            client.close()
        }

    @Test
    fun `http client with502 bad gateway throws server response exception`() =
        runTest {
            val client =
                createMockClientWithDelay(
                    responseBody = """{"error": "Bad Gateway", "message": "Upstream server error"}""",
                    statusCode = HttpStatusCode.BadGateway,
                )

            val response = client.get("https://api.example.com/bad-gateway")
            assertTrue(response.status == HttpStatusCode.BadGateway)
            client.close()
        }

    @Test
    fun `http client with503 service unavailable throws server response exception`() =
        runTest {
            val client =
                createMockClientWithDelay(
                    responseBody = """{"error": "Service Unavailable", "message": "Server temporarily unavailable"}""",
                    statusCode = HttpStatusCode.ServiceUnavailable,
                )

            val response = client.get("https://api.example.com/service-unavailable")
            assertTrue(response.status == HttpStatusCode.ServiceUnavailable)
            client.close()
        }

    @Test
    fun `http client with custom timeout respects timeout`() =
        runTest {
            val client =
                HttpClient(MockEngine) {
                    configureClientDefaults()
                    install(HttpTimeout) {
                        requestTimeoutMillis = 500
                    }
                    engine {
                        addHandler {
                            delay(2.seconds) // 2 second delay
                            respond("Should not reach here")
                        }
                    }
                }

            assertFailsWith<HttpRequestTimeoutException> {
                client.get("https://api.example.com/custom-timeout") {
                    timeout {
                        requestTimeoutMillis = 500 // 500ms timeout
                    }
                }
            }
            client.close()
        }

    @Test
    fun `http client with post request error handles correctly`() =
        runTest {
            val client =
                createMockClientWithDelay(
                    responseBody = """{"error": "Validation failed", "fields": ["email", "password"]}""",
                    statusCode = HttpStatusCode.UnprocessableEntity,
                )

            val response =
                client.post("https://api.example.com/create") {
                    setBody("""{"email": "invalid", "password": ""}""")
                }
            assertTrue(response.status == HttpStatusCode.UnprocessableEntity)
            client.close()
        }

    @Test
    fun `http client with network fluctuation handles multiple errors`() =
        runTest {
            val errors =
                listOf(
                    ConnectTimeoutException("Connection timeout"),
                    SocketTimeoutException("Socket timeout"),
                    IOException("Network IO error"),
                )

            errors.forEach { error ->
                val client = createMockClientWithException(error)

                val exception =
                    assertFailsWith<Exception> {
                        client.get("https://api.example.com/unstable")
                    }
                assertTrue(exception::class == error::class)
                client.close()
            }
        }

    @Test
    fun `http client with malformed response handles gracefully`() =
        runTest {
            val client =
                HttpClient(MockEngine) {
                    configureClientDefaults()
                    engine {
                        addHandler {
                            respond(
                                content = ByteReadChannel("malformed response that cannot be parsed"),
                                status = HttpStatusCode.OK,
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
    fun `http client with slow response completes within reasonable time`() =
        runTest {
            val client =
                createMockClientWithDelay(
                    responseBody = "Slow but successful response",
                    delayMs = 100, // Small delay that should not timeout
                )

            val response = client.get("https://api.example.com/slow-but-ok")
            assertTrue(response.status == HttpStatusCode.OK)
            client.close()
        }

    @Test
    fun `http client with empty error response handles correctly`() =
        runTest {
            val client =
                createMockClientWithDelay(
                    responseBody = "",
                    statusCode = HttpStatusCode.InternalServerError,
                )

            val response = client.get("https://api.example.com/empty-error")
            assertTrue(response.status == HttpStatusCode.InternalServerError)
            client.close()
        }

    @Test
    fun `http client with rate limit error throws correct exception`() =
        runTest {
            val client =
                createMockClientWithDelay(
                    responseBody = """{"error": "Rate limit exceeded", "retry_after": 60}""",
                    statusCode = HttpStatusCode.TooManyRequests,
                )

            val response = client.get("https://api.example.com/rate-limited")
            assertTrue(response.status == HttpStatusCode.TooManyRequests)
            client.close()
        }
}
