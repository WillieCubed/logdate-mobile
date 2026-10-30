package app.logdate.client.networking

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeoutCapability
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.content.TextContent
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ClientDefaultsTest {
    @Test
    fun `a connection that cannot open or stalls fails instead of waiting forever`() =
        runTest {
            var timeouts: HttpTimeoutConfig? = null
            val client = client { request -> timeouts = request.getCapabilityOrNull(HttpTimeoutCapability) }

            client.post("https://sync.example/api") { setBody("") }

            val applied = assertNotNull(timeouts)
            assertNotNull(applied.connectTimeoutMillis)
            assertNotNull(applied.socketTimeoutMillis)
            assertNull(applied.requestTimeoutMillis, "Long uploads must not be cut off by an overall request limit")
        }

    @Test
    fun `request bodies are sent as compact JSON`() =
        runTest {
            var sent = ""
            val client = client { request -> sent = (request.body as TextContent).text }

            client.post("https://sync.example/api") {
                contentType(ContentType.Application.Json)
                setBody(Probe(name = "entry", count = 2))
            }

            assertEquals("""{"name":"entry","count":2}""", sent)
        }

    @Serializable
    private data class Probe(
        val name: String,
        val count: Int,
    )

    private fun client(onRequest: (io.ktor.client.request.HttpRequestData) -> Unit): HttpClient =
        HttpClient(
            MockEngine { request ->
                onRequest(request)
                respond("{}", headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
            },
        ) {
            configureClientDefaults()
        }
}
