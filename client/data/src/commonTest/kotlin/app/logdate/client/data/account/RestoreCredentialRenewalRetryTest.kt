package app.logdate.client.data.account

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RestoreCredentialRenewalRetryTest {
    @Test
    fun `authorization read outages during retry retain pending enrollment`() =
        runTest {
            for (failedRead in 1..4) {
                var reads = 0
                var pending = true
                var attempts = 0
                retryRestoreCredentialRenewal(
                    isPending = { pending },
                    isCurrent = {
                        if (++reads == failedRead) error("Authorization temporarily unavailable")
                        true
                    },
                    renew = {
                        if (++attempts == 1) error("Provider temporarily unavailable")
                        pending = false
                    },
                    pause = {},
                )
                assertEquals(false, pending)
            }
        }

    @Test
    fun `an outage repairs itself without another session emission`() =
        runTest {
            var pending = true
            var attempts = 0
            val waits = mutableListOf<Long>()
            retryRestoreCredentialRenewal({ pending }, { true }, {
                attempts++
                if (attempts < 3) error("provider temporarily unavailable")
                pending = false
            }, { waits += it })
            assertEquals(3, attempts)
            assertEquals(listOf(30_000L, 60_000L), waits)
        }

    @Test
    fun `a changed installation does not consume its pending credential`() =
        runTest {
            retryRestoreCredentialRenewal({ true }, { false }, { error("unexpected attempt") }, { error("unexpected wait") })
        }

    @Test
    fun `sign out during an attempt stops retrying`() =
        runTest {
            var current = true
            var attempts = 0
            retryRestoreCredentialRenewal({ true }, { current }, {
                attempts++
                current = false
                error("signed out")
            }, { error("stale recovery waited") })
            assertEquals(1, attempts)
        }

    @Test
    fun `cancellation propagates without another attempt`() =
        runTest {
            var attempts = 0
            assertFailsWith<CancellationException> {
                retryRestoreCredentialRenewal({ true }, { true }, {
                    attempts++
                    throw CancellationException("stopped")
                }, { error("cancelled recovery waited") })
            }
            assertEquals(1, attempts)
        }

    @Test
    fun `retry intervals are capped while the durable marker stays pending`() =
        runTest {
            var attempts = 0
            val waits = mutableListOf<Long>()
            retryRestoreCredentialRenewal({ attempts < 10 }, { true }, {
                attempts++
                if (attempts < 10) error("offline")
            }, { waits += it })
            assertEquals(listOf(30_000L, 60_000L, 120_000L, 240_000L, 480_000L, 900_000L, 900_000L, 900_000L, 900_000L), waits)
        }

    @Test
    fun `temporary secure storage failure does not strand renewal`() =
        runTest {
            var reads = 0
            var pending = true
            retryRestoreCredentialRenewal({
                reads++
                if (reads == 1) error("vault temporarily unavailable")
                pending
            }, { true }, { pending = false }, {})
            assertEquals(false, pending)
        }

    @Test
    fun `refreshing an expired session cannot retry with its old credentials`() =
        runTest {
            var current = true
            var attempts = 0
            retryRestoreCredentialRenewal({ true }, { current }, {
                attempts++
                error("expired session")
            }, { error("old session waited") }, { current = false })
            assertEquals(1, attempts)
        }

    @Test
    fun `cancellation during backoff stops recovery immediately`() =
        runTest {
            var attempts = 0
            assertFailsWith<CancellationException> {
                retryRestoreCredentialRenewal({ true }, { true }, {
                    attempts++
                    error("offline")
                }, { throw CancellationException("closed") })
            }
            assertEquals(1, attempts)
        }
}
