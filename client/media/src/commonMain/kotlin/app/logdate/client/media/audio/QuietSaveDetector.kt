package app.logdate.client.media.audio

/** Requires a full quiet window on both the elapsed clock and the encoded audio timeline. */
class QuietSaveDetector {
    private var inputRoute: String? = null
    private var quietSince: Long? = null
    private var capturedQuietSince: Long? = null
    private var lastSample: Long? = null
    private var lastCapturedAudioMs: Long? = null
    private var noiseFloor = 0.0003f

    fun reset() {
        quietSince = null
        capturedQuietSince = null
        lastSample = null
        lastCapturedAudioMs = null
        inputRoute = null
        noiseFloor = 0.0003f
    }

    fun observe(
        level: Float?,
        elapsedMs: Long,
        inputRouteId: String? = null,
        capturedAudioMs: Long = elapsedMs,
    ): Boolean {
        if (inputRouteId != inputRoute) {
            reset()
            inputRoute = inputRouteId
        }
        val previous = lastSample
        val previousCaptured = lastCapturedAudioMs
        if (level == null || !level.isFinite() || level !in 0f..1f || capturedAudioMs < 0) {
            reset()
            return false
        }
        if (previous != null && (elapsedMs < previous || elapsedMs - previous > MAX_SAMPLE_GAP_MS)) reset()
        if (previousCaptured != null && capturedAudioMs < previousCaptured) reset()
        lastSample = elapsedMs
        lastCapturedAudioMs = capturedAudioMs
        // Only low-level input can establish the floor; speech cannot raise it indefinitely.
        if (level < MAX_ADAPTATION_LEVEL) noiseFloor = (noiseFloor * 0.98f + level * 0.02f).coerceAtMost(MAX_NOISE_FLOOR)
        val threshold = maxOf(MIN_SOUND_LEVEL, noiseFloor * 3f)
        if (level >= threshold) {
            quietSince = null
            capturedQuietSince = null
            return false
        }
        val start = quietSince ?: elapsedMs.also { quietSince = it }
        val capturedStart = capturedQuietSince ?: capturedAudioMs.also { capturedQuietSince = it }
        return elapsedMs - start >= QUIET_WINDOW_MS && capturedAudioMs - capturedStart >= QUIET_WINDOW_MS
    }

    companion object {
        const val QUIET_WINDOW_MS = 120_000L
        private const val MAX_SAMPLE_GAP_MS = 1_000L
        private const val MAX_ADAPTATION_LEVEL = 0.008f
        private const val MAX_NOISE_FLOOR = 0.0012f
        private const val MIN_SOUND_LEVEL = 0.0015f
    }
}
