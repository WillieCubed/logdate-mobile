package app.logdate.server.oauth

import java.security.MessageDigest
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * Remembers single-use values, such as DPoP proof IDs and PKCE code challenges, for as long as
 * they could still be accepted, so a second use is refused.
 *
 * A server with a database uses the PostgreSQL store, which every server instance shares. A replay
 * sent to a different instance from the first use is therefore still caught.
 */
fun interface OAuthReplayStore {
    /**
     * Records [key] for [ttl] and returns `true`. Returns `false` when [key] was already recorded
     * and has not expired, which means this is a replay.
     */
    fun firstUse(
        key: String,
        ttl: Duration,
    ): Boolean
}

/** The value a replay store keeps for [key]: its SHA-256, so stored rows never contain the value itself. */
internal fun replayKeyHash(key: String): String =
    MessageDigest
        .getInstance("SHA-256")
        .digest(key.toByteArray())
        .joinToString("") { "%02x".format(it) }

/**
 * A replay store for one process, used when the server runs without a database. It holds at most
 * [maxEntries] values and forgets the oldest first when full.
 */
class InMemoryOAuthReplayStore(
    private val clock: Clock = Clock.System,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
) : OAuthReplayStore {
    private val seen = LinkedHashMap<String, Instant>()

    @Synchronized
    override fun firstUse(
        key: String,
        ttl: Duration,
    ): Boolean {
        val now = clock.now()
        // Each caller records one kind of value with one lifetime, so insertion order is close to expiry order.
        val iterator = seen.entries.iterator()
        while (iterator.hasNext() && iterator.next().value <= now) iterator.remove()
        val hash = replayKeyHash(key)
        seen[hash]?.let { expiresAt -> if (expiresAt > now) return false }
        seen.remove(hash)
        if (seen.size >= maxEntries) {
            seen.remove(seen.keys.first())
        }
        seen[hash] = now + ttl
        return true
    }

    private companion object {
        const val DEFAULT_MAX_ENTRIES = 100_000
    }
}
