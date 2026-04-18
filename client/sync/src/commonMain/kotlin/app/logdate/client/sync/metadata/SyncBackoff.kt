package app.logdate.client.sync.metadata

import kotlin.random.Random

/**
 * Delay schedule used by the sync retry queue when an upload fails.
 *
 * The curve is exponential at first and then widens so a persistent outage doesn't spam the
 * server:
 *
 * | attempt | base delay |
 * |---------|------------|
 * | 1       | 1s         |
 * | 2       | 2s         |
 * | 3       | 4s         |
 * | 4       | 8s         |
 * | 5       | 16s        |
 * | 6       | 30s        |
 * | 7       | 5m         |
 * | 8       | 30m        |
 * | 9+      | 1h         |
 *
 * Each delay is multiplied by a uniform ±[jitterFraction] jitter so two devices that dropped
 * offline at the same moment don't return in lockstep and hammer the server together.
 *
 * [random] is injectable so tests can lock in the curve without flakiness.
 */
class SyncBackoff(
    private val jitterFraction: Double = DEFAULT_JITTER_FRACTION,
    private val random: Random = Random.Default,
) {
    fun nextDelayMs(retryCount: Int): Long {
        val base = baseDelayMs(retryCount)
        return applyJitter(base)
    }

    /** Deterministic base, without jitter — exposed for tests that only care about the ladder. */
    internal fun baseDelayMs(retryCount: Int): Long =
        when (retryCount.coerceAtLeast(1)) {
            1 -> 1_000L
            2 -> 2_000L
            3 -> 4_000L
            4 -> 8_000L
            5 -> 16_000L
            6 -> 30_000L
            7 -> 5 * 60_000L
            8 -> 30 * 60_000L
            else -> 60 * 60_000L
        }

    private fun applyJitter(baseMs: Long): Long {
        if (jitterFraction <= 0.0) return baseMs
        val spread = baseMs * jitterFraction
        val low = (baseMs - spread).coerceAtLeast(1.0)
        val high = baseMs + spread
        return (low + random.nextDouble() * (high - low)).toLong()
    }

    companion object {
        /** Fraction of each base delay used as symmetric jitter (±20%). */
        const val DEFAULT_JITTER_FRACTION: Double = 0.2
    }
}
