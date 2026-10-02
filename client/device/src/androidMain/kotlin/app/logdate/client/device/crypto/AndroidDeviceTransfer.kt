package app.logdate.client.device.crypto

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.uuid.Uuid

/** Seals an existing identity key to the ephemeral public key shown by a signed-in Mac. */
class AndroidDeviceTransfer(
    privateKey: ByteArray = ByteArray(32).also(SecureRandom()::nextBytes),
) {
    @Serializable
    private data class Payload(
        val identityKey: String,
        val legacyMediaKey: String?,
        val session: DeviceTransferSession? = null,
    )

    @Serializable
    private data class Envelope(
        val v: Int,
        val pk: String,
        val iv: String,
        val ct: String,
    )

    private val privateKey = X25519PrivateKeyParameters(privateKey, 0)
    private val json = Json { explicitNulls = false }

    fun seal(
        recipientPublicKey: String,
        identityKey: ByteArray,
        legacyMediaKey: ByteArray?,
        accountId: String,
        requestId: Uuid,
        confirmationCode: String,
        session: DeviceTransferSession? = null,
        nonce: ByteArray = ByteArray(12).also(SecureRandom()::nextBytes),
    ): String {
        require(identityKey.size == 32 && (legacyMediaKey == null || legacyMediaKey.size == 32))
        require(nonce.size == 12)
        val recipientBytes = decode(recipientPublicKey)
        require(recipientBytes.size == 32)
        val recipient = X25519PublicKeyParameters(recipientBytes, 0)
        val sharedSecret = ByteArray(32)
        privateKey.generateSecret(recipient, sharedSecret, 0)
        val salt = requestId.toString().lowercase().encodeToByteArray()
        val prk = hmac(salt, sharedSecret)
        val key = hmac(prk, "logdate-device-enrollment-v1".encodeToByteArray() + byteArrayOf(1))
        val associatedData =
            "logdate-device-enrollment-v1|${accountId.lowercase()}|${requestId.toString().lowercase()}|$confirmationCode"
                .encodeToByteArray()
        val payload = json.encodeToString(Payload(encode(identityKey), legacyMediaKey?.let(::encode), session)).encodeToByteArray()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        cipher.updateAAD(associatedData)
        val ciphertext = cipher.doFinal(payload)
        return json.encodeToString(
            Envelope(
                v = 1,
                pk = encode(privateKey.generatePublicKey().encoded),
                iv = encode(nonce),
                ct = encode(ciphertext),
            ),
        )
    }

    private fun hmac(
        key: ByteArray,
        value: ByteArray,
    ): ByteArray =
        Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(key, "HmacSHA256"))
            doFinal(value)
        }

    private fun encode(value: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(value)

    private fun decode(value: String): ByteArray = Base64.getUrlDecoder().decode(value)
}

/** Seals each package with a fresh [AndroidDeviceTransfer] so no two approvals share a sender key. */
class AndroidDeviceTransferSealer : DeviceTransferSealer {
    override suspend fun seal(contents: DeviceTransferContents): String =
        AndroidDeviceTransfer().seal(
            recipientPublicKey = contents.recipientPublicKey,
            identityKey = contents.identityKey,
            legacyMediaKey = contents.legacyMediaKey,
            accountId = contents.accountId,
            requestId = contents.requestId,
            confirmationCode = contents.confirmationCode,
            session = contents.session,
        )
}
