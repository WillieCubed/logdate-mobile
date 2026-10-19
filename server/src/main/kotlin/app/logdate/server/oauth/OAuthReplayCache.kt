package app.logdate.server.oauth

import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * Remembers single-use values, such as DPoP proof IDs and PKCE code challenges, for as long as
 * they could still be accepted, so a second use can be refused.
 *
 * The store lives in this process and holds at most [maxEntries] values; when it is full the
 * oldest value is forgotten first. Each value only needs remembering for its own short window.
 */
class OAuthReplayCache(
    private val clock: Clock = Clock.System,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
) {
    private val seen = LinkedHashMap<String, Instant>()

    /** Records [key] for [ttl] and returns `true`, or returns `false` when it was already recorded and has not expired. */
    @Synchronized
    fun firstUse(
        key: String,
        ttl: Duration,
    ): Boolean {
        val now = clock.now()
        // Each cache records one kind of value with one lifetime, so insertion order is expiry order.
        val iterator = seen.entries.iterator()
        while (iterator.hasNext() && iterator.next().value <= now) iterator.remove()
        seen[key]?.let { expiresAt -> if (expiresAt > now) return false }
        seen.remove(key)
        if (seen.size >= maxEntries) {
            seen.remove(seen.keys.first())
        }
        seen[key] = now + ttl
        return true
    }

    private companion object {
        const val DEFAULT_MAX_ENTRIES = 100_000
    }
}
