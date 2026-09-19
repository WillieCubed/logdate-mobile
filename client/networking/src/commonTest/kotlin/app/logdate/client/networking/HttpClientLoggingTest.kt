package app.logdate.client.networking

import io.github.aakira.napier.Antilog
import io.github.aakira.napier.LogLevel
import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Request logging must never copy a request body into a log line. Media uploads carry whole
 * recordings and photos; logging one as text allocates twice its size in a single string and
 * ran the app out of memory mid-recording.
 */
class HttpClientLoggingTest {
    private val antilog = CapturingAntilog()

    @BeforeTest
    fun setUp() {
        Napier.base(antilog)
    }

    @AfterTest
    fun tearDown() {
        Napier.takeLogarithm(antilog)
    }

    @Test
    fun `a media upload is logged without its body`() =
        runTest {
            val payload = ByteArray(PAYLOAD_SIZE) { 'x'.code.toByte() }
            val marker = "recording-bytes-marker"

            client().post("https://api.logdate.test/media") {
                setBody(
                    MultiPartFormDataContent(
                        formData {
                            append("contentId", marker)
                            append(
                                key = "data",
                                value = payload,
                                headers = Headers.build { append(HttpHeaders.ContentType, "audio/mp4") },
                            )
                        },
                    ),
                )
            }

            val logged = antilog.messages.joinToString("\n")
            assertTrue(logged.contains("https://api.logdate.test/media"), "The request itself should still be logged")
            assertFalse(logged.contains(marker), "Request bodies must not be logged")
            assertTrue(logged.length < PAYLOAD_SIZE, "Logged ${logged.length} chars for a $PAYLOAD_SIZE-byte upload")
        }

    @Test
    fun `a JSON request body is not logged`() =
        runTest {
            client().post("https://api.logdate.test/drafts") {
                contentType(ContentType.Application.Json)
                setBody("""{"text":"private journal entry"}""")
            }

            assertFalse(antilog.messages.any { it.contains("private journal entry") }, "Request bodies must not be logged")
        }

    @Test
    fun `the bearer token is redacted from logged headers`() =
        runTest {
            client().post("https://api.logdate.test/media") {
                headers { append(HttpHeaders.Authorization, "Bearer secret-access-token") }
            }

            val logged = antilog.messages.joinToString("\n")
            assertTrue(logged.contains(HttpHeaders.Authorization), "The header name should still be logged")
            assertFalse(logged.contains("secret-access-token"), "Access tokens must not be logged")
        }

    private fun client(): HttpClient =
        HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    // A server reads the whole body before it answers. A handler that doesn't leaves
                    // anything waiting on the body -- a body logger included -- waiting forever, so a
                    // regression here would hang instead of failing the assertions.
                    request.body.toByteArray()
                    respond(
                        content = "{}",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }
            }
            configureClientDefaults()
        }

    private class CapturingAntilog : Antilog() {
        val messages = mutableListOf<String>()

        override fun performLog(
            priority: LogLevel,
            tag: String?,
            throwable: Throwable?,
            message: String?,
        ) {
            if (message != null) messages += message
        }
    }

    private companion object {
        const val PAYLOAD_SIZE = 256 * 1024
    }
}
