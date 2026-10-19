package app.logdate.space

import dev.whyoleg.cryptography.BinarySize.Companion.bytes
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.HKDF
import dev.whyoleg.cryptography.algorithms.HMAC
import dev.whyoleg.cryptography.algorithms.SHA256

/**
 * The keys that seal one space's records. [contentKey] encrypts records, [indexKey] derives their
 * opaque keys, and [keyId] names the content key inside each envelope so a later rotation can
 * add keys without changing the format.
 */
public class SealingKeys internal constructor(
    internal val contentKey: ByteArray,
    internal val indexKey: ByteArray,
    public val keyId: String,
) {
    public companion object {
        private const val IDENTITY_KEY_SIZE = 32
        private const val DERIVED_KEY_SIZE = 32
        private const val KEY_ID_SIZE = 8
        private const val PERSONAL_SALT = "app.logdate.space.personal"
        private const val CONTENT_INFO = "sealed-record v1"
        private const val INDEX_INFO = "sealed-rkey v1"

        /**
         * Keys for the owner's personal space, derived from the account's 32-byte identity key
         * with HKDF-SHA256 (the identity key as input keying material, the space type as salt).
         */
        public suspend fun personal(identityKey: ByteArray): SealingKeys {
            require(identityKey.size == IDENTITY_KEY_SIZE) { "The identity key must be $IDENTITY_KEY_SIZE bytes" }
            val content = hkdf(identityKey, PERSONAL_SALT, CONTENT_INFO)
            val index = hkdf(identityKey, PERSONAL_SALT, INDEX_INFO)
            val keyId = Base32.encode(hmac(content, "keyId".encodeToByteArray()).copyOf(KEY_ID_SIZE))
            return SealingKeys(content, index, keyId)
        }

        internal suspend fun hmac(
            key: ByteArray,
            value: ByteArray,
        ): ByteArray =
            CryptographyProvider.Default
                .get(HMAC)
                .keyDecoder(SHA256)
                .decodeFromByteArray(HMAC.Key.Format.RAW, key)
                .signatureGenerator()
                .generateSignature(value)

        private suspend fun hkdf(
            ikm: ByteArray,
            salt: String,
            info: String,
        ): ByteArray =
            CryptographyProvider.Default
                .get(HKDF)
                .secretDerivation(SHA256, DERIVED_KEY_SIZE.bytes, salt.encodeToByteArray(), info.encodeToByteArray())
                .deriveSecretToByteArray(ikm)
    }
}
