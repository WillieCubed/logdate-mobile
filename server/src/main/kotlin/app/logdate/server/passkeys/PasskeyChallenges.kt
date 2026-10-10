@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package app.logdate.server.passkeys

import app.logdate.shared.model.PasskeyChallenge
import kotlinx.coroutines.runBlocking
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class PasskeyChallenges(
    private val scope: String,
    private val local: ConcurrentHashMap<String, PasskeyChallenge>,
    private val repository: PasskeyChallengeRepository?,
) {
    private val random = SecureRandom()

    fun issue(
        userId: Uuid,
        type: String,
    ): String {
        val bytes = ByteArray(32).also(random::nextBytes)
        val challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        val now = Clock.System.now()
        val data = PasskeyChallenge(challenge, userId, type, (now + 5.minutes).toString())
        if (repository != null) {
            runBlocking { repository.save(scope, data) }
        } else {
            local.entries.removeIf { Instant.parse(it.value.expiresAt) <= now }
            local[challenge] = data
        }
        return challenge
    }

    fun consume(
        challenge: String,
        expectedType: String,
        expectedUserId: Uuid?,
    ): PasskeyChallenge? {
        val now = Clock.System.now()
        if (repository != null) {
            return runBlocking { repository.consume(scope, challenge, expectedType, expectedUserId, now) }
        }
        var consumed: PasskeyChallenge? = null
        local.computeIfPresent(challenge) { _, data ->
            val expiry = runCatching { Instant.parse(data.expiresAt) }.getOrNull()
            if (!data.isUsed &&
                data.type == expectedType &&
                (expectedUserId == null || data.userId == expectedUserId) &&
                expiry != null &&
                expiry > now
            ) {
                consumed = data
                data.copy(isUsed = true)
            } else {
                data
            }
        }
        return consumed
    }
}
