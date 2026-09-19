package app.logdate.client.networking

import io.github.aakira.napier.Antilog
import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import io.github.aakira.napier.LogLevel as NapierLogLevel

/**
 * What the shared client configuration is allowed to write to the log.
 *
 * Logging a request body means reading the whole thing into one string first. For a photo or a
 * recording that is tens of megabytes of text, and on Android it ran the app out of memory in the
 * middle of every backup that reached a large file -- the app crashed, the backup restarted, and it
 * reached the same file again. Bodies stay out of the log, and so do access tokens.
 */
class HttpClientLoggingTest {
    private class CapturingAntilog : Antilog() {
        val messages = mutableListOf<String>()

        override fun performLog(
            priority: NapierLogLevel,
            tag: String?,
            throwable: Throwable?,
            message: String?,
        ) {
            message?.let(messages::add)
        }
    }

    private val antilog = CapturingAntilog()

    @BeforeTest
    fun installAntilog() {
        Napier.base(antilog)
    }

    @AfterTest
    fun removeAntilog() {
        Napier.takeLogarithm(antilog)
    }

    private fun client(): HttpClient =
        HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    // A server reads the whole body before it answers; a handler that doesn't would
                    // leave anything waiting on the body -- a body logger included -- waiting forever.
                    request.body.toByteArray()
                    respond(
                        content = """{"mediaId": "m1"}""",
                        status = HttpStatusCode.Created,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }
            }
            configureClientDefaults()
        }

    @Test
    fun `a media upload is logged without its body`() =
        runTest {
            val payload = BODY_MARKER.repeat(PAYLOAD_REPEATS).encodeToByteArray()

            client().use { client ->
                val response =
                    client.post("https://cloud.example/media") {
                        setBody(
                            MultiPartFormDataContent(
                                formData {
                                    append("contentId", "c1")
                                    append(
                                        key = "data",
                                        value = payload,
                                        headers =
                                            Headers.build {
                                                append(HttpHeaders.ContentDisposition, "filename=\"recording.m4a\"")
                                                append(HttpHeaders.ContentType, "audio/mp4")
                                            },
                                    )
                                },
                            ),
                        )
                    }
                assertEquals(HttpStatusCode.Created, response.status)
                response.bodyAsText()
            }

            assertTrue(antilog.messages.isNotEmpty(), "The request should still be logged")
            assertFalse(
                antilog.messages.any { it.contains(BODY_MARKER) },
                "The request body was written to the log",
            )
            val loggedChars = antilog.messages.sumOf { it.length }
            assertTrue(loggedChars < payload.size / 10, "Logged $loggedChars characters for one request")
        }

    @Test
    fun `an access token is never written to the log`() =
        runTest {
            client().use { client ->
                client
                    .post("https://cloud.example/contents") {
                        header(HttpHeaders.Authorization, "Bearer $ACCESS_TOKEN")
                    }.bodyAsText()
            }

            assertTrue(antilog.messages.isNotEmpty(), "The request should still be logged")
            assertFalse(
                antilog.messages.any { it.contains(ACCESS_TOKEN) },
                "The access token was written to the log",
            )
        }

    private companion object {
        const val BODY_MARKER = "recording-bytes-"
        const val PAYLOAD_REPEATS = 64 * 1024
        const val ACCESS_TOKEN = "eyJ-test-access-token"
    }
}
