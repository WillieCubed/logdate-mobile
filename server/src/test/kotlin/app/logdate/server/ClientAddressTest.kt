package app.logdate.server

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.server.application.call
import io.ktor.server.plugins.origin
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * The client address comes from the proxy in front of the server, never from entries the client
 * wrote into `X-Forwarded-For` itself.
 */
class ClientAddressTest {
    @Test
    fun `the last entry is the client when one trusted proxy forwards the request`() {
        assertEquals("203.0.113.5", forwardedClientAddress(listOf("198.51.100.77, 203.0.113.5"), trustedProxyHops = 0))
    }

    @Test
    fun `separate header lines are read as one list in the order they arrived`() {
        val lines = listOf("198.51.100.77", "203.0.113.5, 10.0.0.9")

        assertEquals("10.0.0.9", forwardedClientAddress(lines, trustedProxyHops = 0))
        assertEquals("203.0.113.5", forwardedClientAddress(lines, trustedProxyHops = 1))
    }

    @Test
    fun `an extra trusted hop skips the address its proxy appended`() {
        assertEquals("203.0.113.5", forwardedClientAddress(listOf("198.51.100.77, 203.0.113.5, 10.0.0.9"), trustedProxyHops = 1))
    }

    @Test
    fun `there is no forwarded address when the header has too few entries`() {
        assertNull(forwardedClientAddress(emptyList(), trustedProxyHops = 0))
        assertNull(forwardedClientAddress(listOf("203.0.113.5"), trustedProxyHops = 1))
    }

    @Test
    fun `TRUSTED_PROXY_HOPS accepts unset, blank and whole numbers`() {
        assertEquals(0, parseTrustedProxyHops(null))
        assertEquals(0, parseTrustedProxyHops("  "))
        assertEquals(1, parseTrustedProxyHops(" 1 "))
    }

    @Test
    fun `TRUSTED_PROXY_HOPS stops the server when it is not a whole number or is negative`() {
        listOf("one", "1.5", "-1", "1,2").forEach { raw ->
            assertFailsWith<IllegalArgumentException>("value: $raw") { parseTrustedProxyHops(raw) }
        }
    }

    @Test
    fun `with one extra trusted proxy a call is attributed to the address that proxy recorded`() =
        testApplication {
            application {
                installForwardedClientAddress(trustedProxyHops = 1)
                routing { get("/who") { call.respondText(call.clientAddress()) } }
            }

            val response =
                client.get("/who") {
                    header(HttpHeaders.XForwardedFor, "198.51.100.77, 203.0.113.5")
                    header(HttpHeaders.XForwardedFor, "10.0.0.9")
                }

            assertEquals("203.0.113.5", response.bodyAsText())
        }

    @Test
    fun `a client-sent forwarded host does not change the scheme or host the server sees`() =
        testApplication {
            application {
                installForwardedClientAddress()
                routing {
                    get("/where") {
                        call.respondText("${call.request.origin.scheme}://${call.request.origin.serverHost}")
                    }
                }
            }

            val response = client.get("/where") { header("X-Forwarded-Host", "evil.example.com") }

            assertEquals(false, "evil.example.com" in response.bodyAsText())
        }
}
