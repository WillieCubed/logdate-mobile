package app.logdate.feature.core

/** Owns one native challenge and its success while the host returns from system authentication. */
internal class AuthenticationChallenge {
    private var generation = 0L
    private var current: Long? = null
    private var successful = false
    private var hostPaused = false

    fun begin(): Long {
        generation += 1
        current = generation
        successful = false
        hostPaused = false
        return generation
    }

    fun isCurrent(id: Long): Boolean = current == id

    fun markHostPaused() {
        if (current != null) hostPaused = true
    }

    fun recordSuccess(
        id: Long,
        hostResumed: Boolean = true,
        deviceCredential: Boolean = false,
    ): Boolean {
        if (!isCurrent(id)) return false
        if ((!hostResumed || hostPaused) && !deviceCredential) {
            invalidate()
            return false
        }
        successful = true
        return true
    }

    fun consumeSuccess(): Boolean {
        if (!successful || current == null) return false
        invalidate()
        return true
    }

    fun invalidate() {
        current = null
        successful = false
    }
}
