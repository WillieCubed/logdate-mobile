package app.logdate.server.oauth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class OAuthNonceServiceTest {
    @Test
    fun `nonce stays stable until refreshed or expired`() {
        val clock = MutableClock(Instant.parse("2026-03-08T00:00:00Z"))
        val service = OAuthNonceService(clock = clock, ttl = 5.minutes)

        val initial = service.currentNonce()
        val repeated = service.currentNonce()
        val refreshed = service.refreshNonce()

        assertEquals(initial, repeated)
        assertNotEquals(initial, refreshed)

        clock.nowValue += 6.minutes
        val rotated = service.currentNonce()
        assertNotEquals(refreshed, rotated)
    }
}
