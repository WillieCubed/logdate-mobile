package app.logdate.server.ratelimit

import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock

/**
 * Per-key sliding-window rate limiter.
 *
 * Hands out request quotas that refill continuously over [policy.windowSeconds]. Each [allow]
 * call records the current timestamp under [key] (usually a user ID or an IP-hash), evicts
 * timestamps older than the window, and rejects when the remaining count exceeds
 * [policy.maxRequests].
 *
 * In-memory: good enough for a single-instance deploy. Multi-instance deployments should back
 * this with Redis — the [allow] signature is deliberately simple so a Redis-backed variant can
 * implement the same interface. For now, this is the shared base used by the sync endpoints and
 * (via its own private copy) the auth endpoints.
 */
class SlidingWindowRateLimiter {
    private val requestsByKey = ConcurrentHashMap<String, ArrayDeque<Long>>()

    fun allow(
        key: String,
        policy: RateLimitPolicy,
        nowEpochMillis: Long = Clock.System.now().toEpochMilliseconds(),
    ): Boolean {
        val windowStart = nowEpochMillis - (policy.windowSeconds * 1000L)
        val bucket = requestsByKey.computeIfAbsent(key) { ArrayDeque() }
        synchronized(bucket) {
            while (bucket.isNotEmpty() && bucket.first() < windowStart) {
                bucket.removeFirst()
            }
            if (bucket.size >= policy.maxRequests) {
                return false
            }
            bucket.addLast(nowEpochMillis)
            return true
        }
    }

    /**
     * Returns how many seconds a caller rejected by [allow] should wait before their oldest
     * tracked request falls out of the window and frees a slot. Used to populate the
     * `Retry-After` response header on 429s.
     */
    fun retryAfterSeconds(
        key: String,
        policy: RateLimitPolicy,
        nowEpochMillis: Long = Clock.System.now().toEpochMilliseconds(),
    ): Long {
        val bucket = requestsByKey[key] ?: return 0
        val oldest = synchronized(bucket) { bucket.firstOrNull() } ?: return 0
        val windowEnd = oldest + policy.windowSeconds * 1000L
        val waitMs = (windowEnd - nowEpochMillis).coerceAtLeast(0)
        return (waitMs + 999) / 1000
    }
}

data class RateLimitPolicy(
    val maxRequests: Int,
    val windowSeconds: Int,
)
