@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package app.logdate.server.passkeys

import app.logdate.shared.model.PasskeyChallenge
import kotlin.time.Instant
import kotlin.uuid.Uuid

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
