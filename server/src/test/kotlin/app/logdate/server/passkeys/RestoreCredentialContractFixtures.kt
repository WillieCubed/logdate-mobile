@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package app.logdate.server.passkeys

import app.logdate.shared.model.PasskeyChallenge
import java.util.Base64
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

internal fun encodeBase64Url(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

internal fun challenge(
    userId: Uuid,
    type: String,
    isUsed: Boolean = false,
): PasskeyChallenge =
    PasskeyChallenge(
        challenge = "c",
        userId = userId,
        type = type,
        expiresAt = (Clock.System.now() + 5.minutes).toString(),
        isUsed = isUsed,
    )

@Suppress("UNCHECKED_CAST")
internal fun putChallenge(
    service: RestoreCredentialService,
    challenge: String,
    challengeData: PasskeyChallenge,
) {
    val field = service::class.java.getDeclaredField("challenges")
    field.isAccessible = true
    val map = field.get(service) as MutableMap<String, PasskeyChallenge>
    map[challenge] = challengeData.copy(challenge = challenge)
}

internal fun setPrivateField(
    target: Any,
    fieldName: String,
    value: Any,
) {
    val field = target::class.java.getDeclaredField(fieldName)
    field.isAccessible = true
    field.set(target, value)
}
