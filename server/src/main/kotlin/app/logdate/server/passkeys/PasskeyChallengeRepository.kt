@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package app.logdate.server.passkeys

import app.logdate.shared.model.PasskeyChallenge
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * The challenge type for passkey sign-in on the OAuth authorization page. Its challenges are bound
 * to one authorization request, so they are stored apart from app sign-in challenges and neither
 * kind can be spent as the other.
 */
const val OAUTH_AUTHORIZATION_CHALLENGE_TYPE = "oauth_authorize"

interface PasskeyChallengeRepository {
    suspend fun save(
        scope: String,
        challenge: PasskeyChallenge,
    )

    suspend fun consume(
        scope: String,
        challenge: String,
        expectedType: String,
        expectedUserId: Uuid?,
        now: Instant,
    ): PasskeyChallenge?
}
