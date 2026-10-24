package app.logdate.server.atproto.firehose

import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds

/**
 * Whether the server announces public repositories to the rest of the AT Protocol network, and to
 * which relays.
 *
 * Turning [enabled] on does three things: the server serves `com.atproto.sync.subscribeRepos`,
 * anyone can read a public repository through the standard repository methods, and every change to
 * a public repository is written to the event log that stream replays. [relayHosts] are the
 * relays the server asks to follow it; with none, a relay has to be pointed at the server by hand.
 *
 * Nothing here reaches a personal or shared space: [PublicRepoPolicy] decides which collections are
 * public, and that list is fixed in code, not configuration.
 */
data class AtprotoFirehoseConfig(
    val enabled: Boolean = false,
    val relayHosts: List<String> = emptyList(),
    /** How long the event log keeps an event, which is how far back a relay can resume from. */
    val retention: Duration = DEFAULT_RETENTION,
    /** How many relays and apps may follow the stream at once. */
    val maxSubscribers: Int = DEFAULT_MAX_SUBSCRIBERS,
    /** How often a follower of the stream looks for events another server instance wrote. */
    val pollInterval: Duration = DEFAULT_POLL_INTERVAL,
) {
    init {
        require(maxSubscribers > 0) { "maxSubscribers must be positive" }
        relayHosts.forEach { host -> require(isRelayHostname(host)) { "ATPROTO_RELAY_HOSTS entry is not a hostname: $host" } }
    }

    companion object {
        const val ENABLED_VARIABLE: String = "ATPROTO_FIREHOSE_ENABLED"
        const val RELAY_HOSTS_VARIABLE: String = "ATPROTO_RELAY_HOSTS"

        val DEFAULT_RETENTION: Duration = 7.days
        const val DEFAULT_MAX_SUBSCRIBERS: Int = 64
        val DEFAULT_POLL_INTERVAL: Duration = 500.milliseconds

        private val hostname = Regex("^(?=.{1,253}$)[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)+$")

        /** True for a lowercase DNS name with at least two labels, which is what a relay answers on. */
        fun isRelayHostname(value: String): Boolean = hostname.matches(value)

        /**
         * Reads the configuration from the environment. Only the exact value `true` turns the
         * stream on, and relay hosts are a comma-separated list of hostnames without a scheme or
         * path. A host that is not a hostname stops the server at startup, because a typo there
         * would silently leave the server unannounced.
         */
        fun fromEnvironment(readEnv: (String) -> String? = System::getenv): AtprotoFirehoseConfig {
            val enabled = readEnv(ENABLED_VARIABLE)?.trim()?.equals("true", ignoreCase = true) == true
            val hosts =
                readEnv(RELAY_HOSTS_VARIABLE)
                    .orEmpty()
                    .split(',')
                    .map { it.trim().lowercase() }
                    .filter(String::isNotEmpty)
                    .distinct()
            return AtprotoFirehoseConfig(enabled = enabled, relayHosts = if (enabled) hosts else emptyList())
        }
    }
}
