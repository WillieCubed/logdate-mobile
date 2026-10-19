package app.logdate.server.ratelimit

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FixedWindowRateLimiterTest {
    private val policy = RateLimitPolicy(maxRequests = 3, windowSeconds = 60)

    @Test
    fun `a key gets its budget once per window and learns when the window ends`() {
        val limiter = FixedWindowRateLimiter()
        repeat(3) { assertTrue(limiter.allow("a", policy, 1_000L + it)) }
        assertFalse(limiter.allow("a", policy, 2_000L))
        assertEquals(59, limiter.retryAfterSeconds("a", 2_000L))
        assertTrue(limiter.allow("b", policy, 2_000L), "each key has its own budget")
        assertTrue(limiter.allow("a", policy, 61_000L), "a new window starts once the last one ends")
        assertEquals(0, limiter.retryAfterSeconds("unknown", 2_000L))
    }

    @Test
    fun `concurrent requests never pass the budget`() =
        runBlocking {
            val limiter = FixedWindowRateLimiter()
            val wide = RateLimitPolicy(maxRequests = 500, windowSeconds = 60)
            val allowed = (1..2_000).map { async(Dispatchers.Default) { limiter.allow("shared", wide, 1_000L) } }.awaitAll()
            assertEquals(500, allowed.count { it })
        }

    @Test
    fun `ended windows are swept so idle keys don't stay in memory`() {
        val limiter = FixedWindowRateLimiter()
        repeat(5_000) { limiter.allow("idle-$it", policy, 1_000L) }
        assertEquals(5_000, limiter.trackedKeys())
        repeat(1_024) { limiter.allow("active", policy, 120_000L) }
        assertEquals(1, limiter.trackedKeys())
    }
}
