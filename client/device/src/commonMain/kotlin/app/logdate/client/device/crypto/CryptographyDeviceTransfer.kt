package app.logdate.client.device.crypto

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.DelicateCryptographyApi
import dev.whyoleg.cryptography.algorithms.AES
import dev.whyoleg.cryptography.algorithms.HMAC
import dev.whyoleg.cryptography.algorithms.SHA256
import dev.whyoleg.cryptography.algorithms.XDH
import dev.whyoleg.cryptography.random.CryptographyRandom
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64

/**
 * [DeviceTransferSealer] built on the platform's own cryptography provider (CryptoKit on iOS, the
 * JDK elsewhere). It produces the same envelope as `AndroidDeviceTransfer`, byte for byte.
 *
 * [privateKey] and [nonce] exist so tests can reproduce the Swift fixture; production callers leave
 * them unset so every seal uses a fresh X25519 key and a random nonce.
 */
class CryptographyDeviceTransfer(
    private val privateKey: ByteArray? = null,
    private val nonce: () -> ByteArray = { CryptographyRandom.nextBytes(NONCE_SIZE) },
    private val provider: CryptographyProvider = CryptographyProvider.Default,
) : DeviceTransferSealer {
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

    private val json = Json { explicitNulls = false }

    @OptIn(DelicateCryptographyApi::class)
    override suspend fun seal(contents: DeviceTransferContents): String {
        require(contents.identityKey.size == KEY_SIZE)
        require(contents.legacyMediaKey == null || contents.legacyMediaKey.size == KEY_SIZE)
        val iv = nonce()
        require(iv.size == NONCE_SIZE)
        val recipientBytes = decode(contents.recipientPublicKey)
        require(recipientBytes.size == KEY_SIZE)

        val xdh = provider.get(XDH)
        val senderKey =
            privateKey
                ?.let { xdh.privateKeyDecoder(XDH.Curve.X25519).decodeFromByteArray(XDH.PrivateKey.Format.RAW, it) }
                ?: xdh.keyPairGenerator(XDH.Curve.X25519).generateKey().privateKey
        val recipient = xdh.publicKeyDecoder(XDH.Curve.X25519).decodeFromByteArray(XDH.PublicKey.Format.RAW, recipientBytes)
        val sharedSecret = senderKey.sharedSecretGenerator().generateSharedSecretToByteArray(recipient)

        val requestId = contents.requestId.toString().lowercase()
        val prk = hmac(requestId.encodeToByteArray(), sharedSecret)
        val key = hmac(prk, INFO.encodeToByteArray() + byteArrayOf(1))
        val associatedData =
            "$INFO|${contents.accountId.lowercase()}|$requestId|${contents.confirmationCode}".encodeToByteArray()
        val payload =
            json
                .encodeToString(
                    Payload(encode(contents.identityKey), contents.legacyMediaKey?.let(::encode), contents.session),
                ).encodeToByteArray()
        val ciphertext =
            provider
                .get(AES.GCM)
                .keyDecoder()
                .decodeFromByteArray(AES.Key.Format.RAW, key)
                .cipher()
                .encryptWithIv(iv = iv, plaintext = payload, associatedData = associatedData)
        val senderPublicKey = senderKey.getPublicKey().encodeToByteArray(XDH.PublicKey.Format.RAW)
        return json.encodeToString(Envelope(v = 1, pk = encode(senderPublicKey), iv = encode(iv), ct = encode(ciphertext)))
    }

    private suspend fun hmac(
        key: ByteArray,
        value: ByteArray,
    ): ByteArray =
        provider
            .get(HMAC)
            .keyDecoder(SHA256)
            .decodeFromByteArray(HMAC.Key.Format.RAW, key)
            .signatureGenerator()
            .generateSignature(value)

    private fun encode(value: ByteArray): String = base64.encode(value)

    private fun decode(value: String): ByteArray = base64Decoder.decode(value)

    private companion object {
        const val KEY_SIZE = 32
        const val NONCE_SIZE = 12
        const val INFO = "logdate-device-enrollment-v1"
        val base64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
        val base64Decoder = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)
    }
}
