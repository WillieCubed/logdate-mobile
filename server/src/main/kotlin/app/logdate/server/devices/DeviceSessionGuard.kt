package app.logdate.server.devices

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Answers whether a linked device session may still use its access tokens.
 *
 * Every request from a linked device asks, so answers are cached for [cacheTtl]. Revoking through
 * [DeviceLinkService] clears the cached answer on this server at once; another server instance notices
 * within [cacheTtl]. Refreshing a token always reads the store. Without a [repository] no session is
 * active, so a server or test fixture without device sessions refuses every linked device token.
 */
class DeviceSessionGuard(
    private val repository: DeviceSessionRepository?,
    private val cacheTtl: Duration = 30.seconds,
    private val clock: Clock = Clock.System,
) {
    private data class Answer(
        val accountId: UUID?,
        val active: Boolean,
        val checkedAt: Instant,
    )

    private val answers = ConcurrentHashMap<String, Answer>()

    suspend fun isActive(
        accountId: String,
        sessionId: String,
    ): Boolean {
        val store = repository ?: return false
        val now = clock.now()
        val cached = answers[sessionId]?.takeIf { now - it.checkedAt < cacheTtl }
        val answer = cached ?: lookUp(store, sessionId, now).also { if (cacheTtl.isPositive()) answers[sessionId] = it }
        return answer.active && answer.accountId?.toString() == accountId
    }

    fun invalidate(sessionId: String) {
        answers.remove(sessionId)
    }

    private suspend fun lookUp(
        store: DeviceSessionRepository,
        sessionId: String,
        now: Instant,
    ): Answer {
        val id = runCatching { UUID.fromString(sessionId) }.getOrNull() ?: return Answer(null, false, now)
        val session = store.find(id)
        return Answer(session?.accountId, session?.isActive(now.toEpochMilliseconds()) == true, now)
    }

    companion object {
        /** For servers and fixtures without device sessions: every linked device token is refused. */
        val Disabled: DeviceSessionGuard = DeviceSessionGuard(repository = null)
    }
}
