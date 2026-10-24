package app.logdate.server.atproto.firehose

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * The requests the server sends to relays, checked against a relay that records what it receives.
 */
class RelayCrawlerTest {
    private class Relay(
        private val statusFor: (String) -> HttpStatusCode = { HttpStatusCode.OK },
        private val failFor: Set<String> = emptySet(),
    ) {
        val requests = CopyOnWriteArrayList<Triple<HttpMethod, String, String>>()

        val client =
            HttpClient(
                MockEngine { request ->
                    val host = request.url.host
                    requests += Triple(request.method, request.url.toString(), String(request.body.toByteArray()))
                    if (host in failFor) throw IOException("relay unreachable")
                    respond("{}", statusFor(host), headersOf(HttpHeaders.ContentType, "application/json"))
                },
            )
    }

    private class MovableClock(
        var now: Instant = Instant.fromEpochSeconds(1_800_000_000),
    ) : Clock {
        override fun now(): Instant = now
    }

    @Test
    fun `a relay is asked to crawl the server with a request carrying the server hostname`() =
        runBlocking<Unit> {
            val relay = Relay()
            val crawler = RelayCrawler(relay.client, listOf("relay.example.com"), "logdate.app")

            val outcomes = crawler.requestCrawl()

            assertEquals(mapOf("relay.example.com" to CrawlOutcome.ACCEPTED), outcomes)
            val (method, url, body) = relay.requests.single()
            assertEquals(HttpMethod.Post, method)
            assertEquals("https://relay.example.com/xrpc/com.atproto.sync.requestCrawl", url)
            assertEquals(
                "logdate.app",
                Json
                    .parseToJsonElement(body)
                    .jsonObject
                    .getValue("hostname")
                    .jsonPrimitive.content,
            )
        }

    @Test
    fun `every configured relay is asked and each answer is reported`() =
        runBlocking<Unit> {
            val relay =
                Relay(
                    statusFor = { host -> if (host == "refusing.example.com") HttpStatusCode.Forbidden else HttpStatusCode.OK },
                    failFor = setOf("down.example.com"),
                )
            val crawler = RelayCrawler(relay.client, listOf("good.example.com", "refusing.example.com", "down.example.com"), "logdate.app")

            val outcomes = crawler.requestCrawl()

            assertEquals(CrawlOutcome.ACCEPTED, outcomes.getValue("good.example.com"))
            assertEquals(CrawlOutcome.REFUSED, outcomes.getValue("refusing.example.com"))
            assertEquals(CrawlOutcome.UNREACHABLE, outcomes.getValue("down.example.com"))
        }

    @Test
    fun `with no relays configured nothing is sent`() =
        runBlocking<Unit> {
            val relay = Relay()
            val crawler = RelayCrawler(relay.client, emptyList(), "logdate.app")

            assertTrue(!crawler.configured)
            assertNull(crawler.requestIfDue())
            crawler.requestAtStartup()

            assertTrue(relay.requests.isEmpty())
        }

    @Test
    fun `with no public hostname nothing is sent`() =
        runBlocking<Unit> {
            val relay = Relay()
            val crawler = RelayCrawler(relay.client, listOf("relay.example.com"), serverHostname = null)

            assertNull(crawler.requestIfDue())
            assertTrue(crawler.requestCrawl().isEmpty())
            assertTrue(relay.requests.isEmpty())
        }

    @Test
    fun `rounds are spaced by the minimum interval`() =
        runBlocking<Unit> {
            val relay = Relay()
            val clock = MovableClock()
            val crawler =
                RelayCrawler(relay.client, listOf("relay.example.com"), "logdate.app", minimumInterval = 10.minutes, clock = clock)

            assertNotNull(crawler.requestIfDue())
            clock.now += 5.minutes
            assertNull(crawler.requestIfDue())
            clock.now += 6.minutes
            assertNotNull(crawler.requestIfDue())

            assertEquals(2, relay.requests.size)
        }

    @Test
    fun `the startup round counts toward the interval`() =
        runBlocking<Unit> {
            val relay = Relay()
            val clock = MovableClock()
            val crawler = RelayCrawler(relay.client, listOf("relay.example.com"), "logdate.app", minimumInterval = 1.hours, clock = clock)

            crawler.requestAtStartup()
            clock.now += 10.minutes

            assertNull(crawler.requestIfDue())
            assertEquals(1, relay.requests.size)
        }

    @Test
    fun `startup asks an unreachable relay again but not one that refused`() =
        runBlocking<Unit> {
            var attempts = 0
            val client =
                HttpClient(
                    MockEngine { request ->
                        if (request.url.host == "flaky.example.com") {
                            attempts += 1
                            if (attempts < 3) throw IOException("not yet")
                        }
                        respond("{}", if (request.url.host == "refusing.example.com") HttpStatusCode.Forbidden else HttpStatusCode.OK)
                    },
                )
            val crawler = RelayCrawler(client, listOf("flaky.example.com", "refusing.example.com"), "logdate.app")

            crawler.requestAtStartup(attempts = 5, firstWait = 1.milliseconds)

            assertEquals(3, attempts, "the unreachable relay is retried until it answers")
        }

    @Test
    fun `startup gives up on a relay that never answers`() =
        runBlocking<Unit> {
            var attempts = 0
            val client =
                HttpClient(
                    MockEngine { _ ->
                        attempts += 1
                        throw IOException("down")
                    },
                )
            val crawler = RelayCrawler(client, listOf("down.example.com"), "logdate.app")

            crawler.requestAtStartup(attempts = 3, firstWait = 1.milliseconds)

            assertEquals(3, attempts)
        }

    @Test
    fun `the hostname relays connect to comes from the public service endpoint`() {
        assertEquals("logdate.app", RelayCrawler.hostnameFor("https://logdate.app"))
        assertEquals("logdate.app", RelayCrawler.hostnameFor("https://LogDate.app/"))
        assertEquals("pds.example.com:8443", RelayCrawler.hostnameFor("https://pds.example.com:8443"))
        assertEquals("logdate.app", RelayCrawler.hostnameFor("https://logdate.app:443"))
        assertNull(RelayCrawler.hostnameFor("http://localhost:8080"))
        assertNull(RelayCrawler.hostnameFor("not a url"))
    }
}
