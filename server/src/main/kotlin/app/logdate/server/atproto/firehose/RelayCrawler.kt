package app.logdate.server.atproto.firehose

import app.logdate.server.logging.RepoEventNotices
import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.URI
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** How a request to one relay ended. */
enum class CrawlOutcome {
    /** The relay accepted the request. */
    ACCEPTED,

    /** The relay answered, but not with success. */
    REFUSED,

    /** The relay could not be reached. */
    UNREACHABLE,
}

/**
 * Asks relays to follow this server's event stream, with `com.atproto.sync.requestCrawl`.
 *
 * A relay learns about a server only when someone tells it, so the server tells each configured
 * relay when it starts and again when an account is announced. Relays limit these requests, and a
 * relay that already follows the server gains nothing from another, so [requestIfDue] sends at most
 * one round every [minimumInterval]. With no [relayHosts] it sends nothing.
 *
 * @param serverHostname the name relays should connect to, with a port only when it is not the default.
 */
class RelayCrawler(
    private val httpClient: HttpClient,
    private val relayHosts: List<String>,
    private val serverHostname: String?,
    private val minimumInterval: Duration = 10.minutes,
    private val requestTimeout: Duration = 15.seconds,
    private val clock: Clock = Clock.System,
) {
    private val lastRound = AtomicLong(NEVER)

    /** True when there is a relay to ask and a public name to give it. */
    val configured: Boolean get() = relayHosts.isNotEmpty() && serverHostname != null

    /** Asks every relay once and reports each one's answer. */
    suspend fun requestCrawl(): Map<String, CrawlOutcome> {
        val hostname = serverHostname ?: return emptyMap()
        return relayHosts.associateWith { relay -> requestCrawl(relay, hostname) }
    }

    /**
     * Asks every relay unless a round went out within [minimumInterval]. Returns the answers, or
     * `null` when the round was skipped.
     */
    suspend fun requestIfDue(): Map<String, CrawlOutcome>? {
        if (!configured) return null
        val now = clock.now().toEpochMilliseconds()
        val previous = lastRound.get()
        val due = previous == NEVER || now - previous >= minimumInterval.inWholeMilliseconds
        if (!due || !lastRound.compareAndSet(previous, now)) return null
        return requestCrawl()
    }

    /**
     * Asks the relays when the server starts, and asks again after a growing wait for the ones that
     * could not be reached, up to [attempts] rounds. A relay that refuses is not asked again: it
     * answered, and asking more often will not change that.
     */
    suspend fun requestAtStartup(
        attempts: Int = STARTUP_ATTEMPTS,
        firstWait: Duration = 10.seconds,
    ) {
        if (!configured) return
        lastRound.set(clock.now().toEpochMilliseconds())
        var pending = relayHosts
        var wait = firstWait
        repeat(attempts) { attempt ->
            val hostname = checkNotNull(serverHostname)
            pending = pending.filter { relay -> requestCrawl(relay, hostname) == CrawlOutcome.UNREACHABLE }
            if (pending.isEmpty()) return
            if (attempt < attempts - 1) {
                delay(wait)
                wait *= 2
            }
        }
    }

    private suspend fun requestCrawl(
        relay: String,
        hostname: String,
    ): CrawlOutcome =
        try {
            val status =
                withTimeout(requestTimeout) {
                    httpClient
                        .post("https://$relay/xrpc/com.atproto.sync.requestCrawl") {
                            contentType(ContentType.Application.Json)
                            setBody(buildJsonObject { put("hostname", hostname) }.toString())
                        }.status
                }
            if (status.isSuccess()) {
                Napier.i(RepoEventNotices.CRAWL_REQUESTED)
                CrawlOutcome.ACCEPTED
            } else {
                Napier.w(RepoEventNotices.CRAWL_REFUSED)
                CrawlOutcome.REFUSED
            }
        } catch (timeout: TimeoutCancellationException) {
            Napier.e(RepoEventNotices.CRAWL_FAILED, timeout)
            CrawlOutcome.UNREACHABLE
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Napier.e(RepoEventNotices.CRAWL_FAILED, failure)
            CrawlOutcome.UNREACHABLE
        }

    companion object {
        private const val NEVER = Long.MIN_VALUE
        private const val STARTUP_ATTEMPTS = 5

        /**
         * The name relays connect to for a server at [serviceEndpoint], or `null` when the endpoint
         * is not an https address a relay on the internet could reach.
         */
        fun hostnameFor(serviceEndpoint: String): String? {
            val uri = runCatching { URI(serviceEndpoint.trim()) }.getOrNull() ?: return null
            if (uri.scheme != "https") return null
            val host = uri.host?.lowercase() ?: return null
            return if (uri.port == -1 || uri.port == HTTPS_PORT) host else "$host:${uri.port}"
        }

        private const val HTTPS_PORT = 443
    }
}
