package app.logdate.server.identity

import kotlinx.serialization.Serializable
import studio.hypertext.atproto.crypto.Multikey
import java.math.BigInteger
import java.nio.ByteBuffer
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Generates and stores encrypted P-256 signing keys for AT Protocol identity documents.
 */
@OptIn(ExperimentalUuidApi::class, ExperimentalEncodingApi::class)
class SigningKeyService(
    private val repository: SigningKeyRepository,
    private val encryptionKeySeed: String = System.getenv("ATPROTO_SIGNING_KEY_KEK") ?: "logdate-atproto-dev-signing-key",
) {
    private val secureRandom = SecureRandom()

    fun generateKeyPair(): GeneratedSigningKey {
        val keyPairGenerator = KeyPairGenerator.getInstance("EC")
        keyPairGenerator.initialize(ECGenParameterSpec(P256_CURVE_NAME), secureRandom)
        val keyPair = keyPairGenerator.generateKeyPair()
        val compressedPublicKey = compressPublicKey(keyPair.public as ECPublicKey)

        return GeneratedSigningKey(
            algorithm = SIGNING_ALGORITHM,
            publicKeyMultibase = Multikey.encodeP256PublicKey(compressedPublicKey),
            privateKeyPkcs8 = keyPair.private.encoded,
        )
    }

    suspend fun ensureActiveKey(accountId: Uuid): StoredSigningKey = repository.findActiveByAccountId(accountId) ?: createKey(accountId)

    suspend fun rotateKey(accountId: Uuid): StoredSigningKey {
        repository.revokeActiveKeys(accountId)
        return createKey(accountId)
    }

    suspend fun exportActiveKey(
        accountId: Uuid,
        passphrase: String,
    ): ExportedSigningKey {
        require(passphrase.isNotBlank()) { "passphrase must not be blank" }

        val record = ensureActiveKey(accountId)
        val salt = ByteArray(EXPORT_SALT_SIZE).also(secureRandom::nextBytes)
        val iv = ByteArray(GCM_IV_SIZE).also(secureRandom::nextBytes)
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, exportAesKey(passphrase, salt), GCMParameterSpec(GCM_TAG_BITS, iv))
        val encryptedPrivateKey = cipher.doFinal(decryptPrivateKey(record).encoded)

        return ExportedSigningKey(
            algorithm = record.algorithm,
            publicKeyMultibase = record.publicKeyMultibase,
            publicKeyDidKey = didKeyFor(record.publicKeyMultibase),
            encryptedPrivateKey = Base64.encode(encryptedPrivateKey),
            salt = Base64.encode(salt),
            iv = Base64.encode(iv),
            iterations = EXPORT_KDF_ITERATIONS,
        )
    }

    fun decryptPrivateKey(record: StoredSigningKey): PrivateKey {
        val encoded = Base64.decode(record.privateKeyEncrypted)
        val buffer = ByteBuffer.wrap(encoded)
        val iv = ByteArray(GCM_IV_SIZE)
        buffer.get(iv)
        val cipherText = ByteArray(buffer.remaining())
        buffer.get(cipherText)

        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, aesKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        val privateKeyBytes = cipher.doFinal(cipherText)
        return KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(privateKeyBytes))
    }

    fun decryptExportedKey(
        exportedKey: ExportedSigningKey,
        passphrase: String,
    ): PrivateKey {
        require(passphrase.isNotBlank()) { "passphrase must not be blank" }
        require(exportedKey.kdf == PBKDF2_ALGORITHM) { "Unsupported export KDF: ${exportedKey.kdf}" }

        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            exportAesKey(passphrase, Base64.decode(exportedKey.salt), exportedKey.iterations),
            GCMParameterSpec(GCM_TAG_BITS, Base64.decode(exportedKey.iv)),
        )
        val privateKeyBytes = cipher.doFinal(Base64.decode(exportedKey.encryptedPrivateKey))
        return KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(privateKeyBytes))
    }

    private suspend fun createKey(accountId: Uuid): StoredSigningKey {
        val generated = generateKeyPair()
        val stored =
            StoredSigningKey(
                id = Uuid.random(),
                accountId = accountId,
                algorithm = generated.algorithm,
                publicKeyMultibase = generated.publicKeyMultibase,
                privateKeyEncrypted = encryptPrivateKey(generated.privateKeyPkcs8),
                createdAt = Clock.System.now(),
            )
        return repository.save(stored)
    }

    private fun encryptPrivateKey(privateKeyPkcs8: ByteArray): String {
        val iv = ByteArray(GCM_IV_SIZE).also(secureRandom::nextBytes)
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, aesKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        val cipherText = cipher.doFinal(privateKeyPkcs8)
        return Base64.encode(iv + cipherText)
    }

    private fun aesKey(): SecretKeySpec {
        val digest = MessageDigest.getInstance(SHA_256_ALGORITHM).digest(encryptionKeySeed.toByteArray())
        return SecretKeySpec(digest, AES_ALGORITHM)
    }

    private fun exportAesKey(
        passphrase: String,
        salt: ByteArray,
        iterations: Int = EXPORT_KDF_ITERATIONS,
    ): SecretKeySpec {
        val keyFactory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
        val keySpec = PBEKeySpec(passphrase.toCharArray(), salt, iterations, EXPORT_AES_KEY_BITS)
        return SecretKeySpec(keyFactory.generateSecret(keySpec).encoded, AES_ALGORITHM)
    }

    private fun compressPublicKey(publicKey: ECPublicKey): ByteArray {
        val x = publicKey.w.affineX.toFixedWidth(X_COORDINATE_BYTES)
        val prefix = if (publicKey.w.affineY.testBit(0)) COMPRESSED_ODD_PREFIX else COMPRESSED_EVEN_PREFIX
        return byteArrayOf(prefix.toByte()) + x
    }

    data class GeneratedSigningKey(
        val algorithm: String,
        val publicKeyMultibase: String,
        val privateKeyPkcs8: ByteArray,
    )

    @Serializable
    data class ExportedSigningKey(
        val algorithm: String,
        val publicKeyMultibase: String,
        val publicKeyDidKey: String,
        val encryptedPrivateKey: String,
        val salt: String,
        val iv: String,
        val kdf: String = PBKDF2_ALGORITHM,
        val iterations: Int = EXPORT_KDF_ITERATIONS,
    )

    private companion object {
        private const val SIGNING_ALGORITHM = "P-256"
        private const val P256_CURVE_NAME = "secp256r1"
        private const val AES_ALGORITHM = "AES"
        private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val SHA_256_ALGORITHM = "SHA-256"
        private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val GCM_IV_SIZE = 12
        private const val GCM_TAG_BITS = 128
        private const val EXPORT_SALT_SIZE = 16
        private const val EXPORT_KDF_ITERATIONS = 120_000
        private const val EXPORT_AES_KEY_BITS = 256
        private const val X_COORDINATE_BYTES = 32
        private const val COMPRESSED_EVEN_PREFIX = 0x02
        private const val COMPRESSED_ODD_PREFIX = 0x03
    }
}

private fun didKeyFor(publicKeyMultibase: String): String = "did:key:$publicKeyMultibase"

private fun BigInteger.toFixedWidth(width: Int): ByteArray {
    val encoded = toByteArray()
    return when {
        encoded.size == width -> encoded
        encoded.size < width -> ByteArray(width - encoded.size) + encoded
        else -> encoded.copyOfRange(encoded.size - width, encoded.size)
    }
}
