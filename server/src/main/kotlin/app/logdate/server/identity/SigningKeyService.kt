package app.logdate.server.identity

import kotlinx.serialization.Serializable
import studio.hypertext.atproto.crypto.EcCurve
import studio.hypertext.atproto.crypto.EcKeySupport
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.SecureRandom
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
 * Generates and stores encrypted hosted signing keys for AT Protocol identity documents.
 */
@OptIn(ExperimentalUuidApi::class, ExperimentalEncodingApi::class)
class SigningKeyService(
    private val repository: SigningKeyRepository,
    private val encryptionKeySeed: String = System.getenv("ATPROTO_SIGNING_KEY_KEK") ?: "logdate-atproto-dev-signing-key",
) {
    private val secureRandom = SecureRandom()

    fun generateKeyPair(curve: EcCurve = DEFAULT_SIGNING_CURVE): GeneratedSigningKey {
        val keyPair = EcKeySupport.generateKeyPair(curve = curve, secureRandom = secureRandom)
        return GeneratedSigningKey(
            algorithm = curve.signingKeyAlgorithm,
            publicKeyMultibase = EcKeySupport.encodePublicKeyMultibase(keyPair.public as java.security.interfaces.ECPublicKey, curve),
            privateKeyPkcs8 = keyPair.private.encoded,
        )
    }

    suspend fun ensureActiveKey(accountId: Uuid): StoredSigningKey = repository.findActiveByAccountId(accountId) ?: createKey(accountId)

    suspend fun rotateKey(accountId: Uuid): StoredSigningKey = activatePreparedKey(accountId, prepareKey(accountId))

    suspend fun prepareKey(accountId: Uuid): StoredSigningKey {
        val generated = generateKeyPair()
        return buildStoredKey(
            accountId = accountId,
            algorithm = generated.algorithm,
            publicKeyMultibase = generated.publicKeyMultibase,
            privateKeyPkcs8 = generated.privateKeyPkcs8,
        )
    }

    suspend fun activatePreparedKey(
        accountId: Uuid,
        preparedKey: StoredSigningKey,
    ): StoredSigningKey {
        require(preparedKey.accountId == accountId) { "Prepared signing key belongs to a different account" }
        repository.revokeActiveKeys(accountId)
        return repository.save(preparedKey)
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
        return EcKeySupport.decodePrivateKey(privateKeyBytes)
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
        return EcKeySupport.decodePrivateKey(privateKeyBytes)
    }

    suspend fun importActiveKey(
        accountId: Uuid,
        exportedKey: ExportedSigningKey,
        passphrase: String,
    ): StoredSigningKey =
        activatePreparedKey(
            accountId = accountId,
            preparedKey = prepareImportedKey(accountId, exportedKey, passphrase),
        )

    suspend fun prepareImportedKey(
        accountId: Uuid,
        exportedKey: ExportedSigningKey,
        passphrase: String,
    ): StoredSigningKey {
        val privateKey =
            try {
                decryptExportedKey(exportedKey, passphrase)
            } catch (exception: IllegalArgumentException) {
                throw IdentityLifecycleValidationException("Invalid signing key import payload", exception)
            } catch (exception: Exception) {
                throw IdentityLifecycleValidationException("Unable to decrypt signing key import payload", exception)
            }
        require(exportedKey.publicKeyDidKey == didKeyFor(exportedKey.publicKeyMultibase)) {
            "publicKeyDidKey must match publicKeyMultibase"
        }
        require(EcCurve.fromSigningKeyAlgorithm(exportedKey.algorithm) != null) {
            "Unsupported signing key algorithm: ${exportedKey.algorithm}"
        }
        return buildStoredKey(
            accountId = accountId,
            algorithm = exportedKey.algorithm,
            publicKeyMultibase = exportedKey.publicKeyMultibase,
            privateKeyPkcs8 = privateKey.encoded,
        )
    }

    private suspend fun createKey(accountId: Uuid): StoredSigningKey {
        val generated = generateKeyPair()
        return repository.save(
            buildStoredKey(
                accountId = accountId,
                algorithm = generated.algorithm,
                publicKeyMultibase = generated.publicKeyMultibase,
                privateKeyPkcs8 = generated.privateKeyPkcs8,
            ),
        )
    }

    private fun buildStoredKey(
        accountId: Uuid,
        algorithm: String,
        publicKeyMultibase: String,
        privateKeyPkcs8: ByteArray,
    ): StoredSigningKey =
        StoredSigningKey(
            id = Uuid.random(),
            accountId = accountId,
            algorithm = algorithm,
            publicKeyMultibase = publicKeyMultibase,
            privateKeyEncrypted = encryptPrivateKey(privateKeyPkcs8),
            createdAt = Clock.System.now(),
        )

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
        private val DEFAULT_SIGNING_CURVE: EcCurve = EcCurve.K256
        private const val AES_ALGORITHM = "AES"
        private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val SHA_256_ALGORITHM = "SHA-256"
        private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val GCM_IV_SIZE = 12
        private const val GCM_TAG_BITS = 128
        private const val EXPORT_SALT_SIZE = 16
        private const val EXPORT_KDF_ITERATIONS = 120_000
        private const val EXPORT_AES_KEY_BITS = 256
    }
}

internal fun didKeyFor(publicKeyMultibase: String): String = "did:key:$publicKeyMultibase"
