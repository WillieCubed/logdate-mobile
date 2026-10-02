package app.logdate.server.accountkeys

import app.logdate.server.crypto.AesGcmCipher
import app.logdate.server.crypto.EncryptionException
import app.logdate.server.crypto.EncryptionKeyring
import app.logdate.server.crypto.PayloadHeaderCodec
import java.security.MessageDigest
import java.util.UUID

class AccountKeyMaterial(
    val identityKey: ByteArray,
    val mediaKey: ByteArray,
) {
    init {
        require(identityKey.size == KEY_LENGTH && mediaKey.size == KEY_LENGTH)
    }

    companion object {
        const val KEY_LENGTH = 32
    }
}

interface AccountKeyRepository {
    suspend fun get(accountId: UUID): ByteArray?

    suspend fun insertIfAbsent(
        accountId: UUID,
        ciphertext: ByteArray,
    ): Boolean
}

class InMemoryAccountKeyRepository : AccountKeyRepository {
    private val rows = mutableMapOf<UUID, ByteArray>()

    override suspend fun get(accountId: UUID): ByteArray? = synchronized(rows) { rows[accountId]?.copyOf() }

    override suspend fun insertIfAbsent(
        accountId: UUID,
        ciphertext: ByteArray,
    ): Boolean =
        synchronized(rows) {
            if (rows.containsKey(accountId)) {
                false
            } else {
                rows[accountId] = ciphertext.copyOf()
                true
            }
        }
}

enum class AccountKeySaveResult { CREATED, ALREADY_PRESENT, CONFLICT }

class AccountKeyVaultUnavailable : IllegalStateException("Account key encryption is unavailable")

class AccountKeyVault(
    private val repository: AccountKeyRepository,
    private val keyring: EncryptionKeyring?,
    private val cipher: AesGcmCipher = AesGcmCipher(),
) {
    /** Whether a server keyring is configured; without one every read and write answers unavailable. */
    val isAvailable: Boolean get() = keyring != null

    suspend fun get(accountId: UUID): AccountKeyMaterial? {
        val keys = keyring ?: throw AccountKeyVaultUnavailable()
        val stored = repository.get(accountId) ?: return null
        val header = PayloadHeaderCodec.decode(stored, PREFIX)
        val key = keys.getKey(header.keyId) ?: throw EncryptionException("Account key encryption key is unavailable")
        val plaintext =
            cipher.decrypt(
                stored.copyOfRange(header.ciphertextOffset, stored.size),
                key.keyBytes,
                header.iv,
                associatedData(accountId),
            )
        try {
            if (plaintext.size != 1 + 2 * AccountKeyMaterial.KEY_LENGTH || plaintext[0] != MATERIAL_VERSION) {
                throw EncryptionException("Invalid account key material")
            }
            return AccountKeyMaterial(
                plaintext.copyOfRange(1, 33),
                plaintext.copyOfRange(33, 65),
            )
        } finally {
            plaintext.fill(0)
        }
    }

    suspend fun save(
        accountId: UUID,
        material: AccountKeyMaterial,
    ): AccountKeySaveResult {
        val keys = keyring ?: throw AccountKeyVaultUnavailable()
        get(accountId)?.let { existing ->
            return if (existing.matches(material)) {
                AccountKeySaveResult.ALREADY_PRESENT
            } else {
                AccountKeySaveResult.CONFLICT
            }
        }

        val active = keys.getActiveKey()
        val plaintext = byteArrayOf(MATERIAL_VERSION) + material.identityKey + material.mediaKey
        val encrypted =
            try {
                val iv = cipher.generateIV()
                val ciphertext = cipher.encrypt(plaintext, active.keyBytes, iv, associatedData(accountId))
                PayloadHeaderCodec.encode(PREFIX, active.keyId, iv, ciphertext)
            } finally {
                plaintext.fill(0)
            }
        if (repository.insertIfAbsent(accountId, encrypted)) return AccountKeySaveResult.CREATED
        val existing = get(accountId) ?: throw EncryptionException("Account key disappeared during creation")
        return if (existing.matches(material)) AccountKeySaveResult.ALREADY_PRESENT else AccountKeySaveResult.CONFLICT
    }

    private fun AccountKeyMaterial.matches(other: AccountKeyMaterial): Boolean =
        MessageDigest.isEqual(identityKey, other.identityKey) && MessageDigest.isEqual(mediaKey, other.mediaKey)

    private fun associatedData(accountId: UUID): ByteArray = "logdate-account-key-v1:$accountId".toByteArray(Charsets.UTF_8)

    private companion object {
        val PREFIX = "LDKEY".toByteArray(Charsets.US_ASCII)
        const val MATERIAL_VERSION: Byte = 1
    }
}
