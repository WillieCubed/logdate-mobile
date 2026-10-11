package app.logdate.server.database

import app.logdate.server.identity.EncryptedPrivateKey
import app.logdate.server.oauth.OAuthSigningKeyRepository
import app.logdate.server.oauth.StoredOAuthSigningKey
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update

internal class PostgreSQLOAuthSigningKeyRepository : OAuthSigningKeyRepository {
    override fun getOrCreate(candidate: () -> StoredOAuthSigningKey): StoredOAuthSigningKey =
        transaction {
            findActive() ?: run {
                val key = candidate()
                // The fixed slot is the primary key, so a concurrent instance's insert is ignored
                // and both instances read back whichever key was stored first.
                OAuthSigningKeysTable.insertIgnore {
                    it[slot] = ACTIVE_SLOT
                    it[keyId] = key.keyId
                    it[privateKeyEncrypted] = key.privateKeyEncrypted
                    it[publicKeySpki] = key.publicKeySpki
                    it[createdAt] = key.createdAt
                }
                checkNotNull(findActive()) { "OAuth signing key was not stored" }
            }
        }

    override suspend fun encryptedPrivateKeys(): List<EncryptedPrivateKey> =
        transaction {
            OAuthSigningKeysTable.selectAll().map {
                EncryptedPrivateKey(
                    id = it[OAuthSigningKeysTable.keyId],
                    privateKeyEncrypted = it[OAuthSigningKeysTable.privateKeyEncrypted],
                )
            }
        }

    override suspend fun replaceEncryptedPrivateKey(
        id: String,
        expected: String,
        replacement: String,
    ): Boolean =
        transaction {
            OAuthSigningKeysTable.update({
                (OAuthSigningKeysTable.keyId eq id) and
                    (OAuthSigningKeysTable.privateKeyEncrypted eq expected)
            }) {
                it[privateKeyEncrypted] = replacement
            } == 1
        }

    private fun findActive(): StoredOAuthSigningKey? =
        OAuthSigningKeysTable
            .selectAll()
            .where { OAuthSigningKeysTable.slot eq ACTIVE_SLOT }
            .singleOrNull()
            ?.let { row ->
                StoredOAuthSigningKey(
                    keyId = row[OAuthSigningKeysTable.keyId],
                    privateKeyEncrypted = row[OAuthSigningKeysTable.privateKeyEncrypted],
                    publicKeySpki = row[OAuthSigningKeysTable.publicKeySpki],
                    createdAt = row[OAuthSigningKeysTable.createdAt],
                )
            }

    private companion object {
        const val ACTIVE_SLOT = "active"
    }
}
