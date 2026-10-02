package app.logdate.client.device.crypto

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/** Credentials the new device signs in with after it opens the transfer package. */
@Serializable
data class DeviceTransferSession(
    val accountDisplayName: String,
    val accessToken: String,
    val refreshToken: String,
) {
    override fun toString(): String = "DeviceTransferSession(accountDisplayName=$accountDisplayName, tokens=<redacted>)"
}

/** Everything a device transfer package carries, plus the values its encryption is bound to. */
class DeviceTransferContents(
    val recipientPublicKey: String,
    val identityKey: ByteArray,
    val legacyMediaKey: ByteArray?,
    val accountId: String,
    val requestId: Uuid,
    val confirmationCode: String,
    val session: DeviceTransferSession? = null,
)

/**
 * Encrypts account keys to the one-time public key a new device showed in its connection code.
 *
 * Every implementation produces the same `logdate-device-enrollment-v1` envelope: X25519 with a
 * fresh sender key, HKDF-SHA256 salted with the request ID, and AES-256-GCM bound to the account,
 * request, and confirmation code. The native Swift client opens it.
 */
fun interface DeviceTransferSealer {
    suspend fun seal(contents: DeviceTransferContents): String
}
