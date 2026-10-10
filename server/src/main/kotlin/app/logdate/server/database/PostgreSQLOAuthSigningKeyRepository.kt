package app.logdate.server.database

import app.logdate.server.oauth.OAuthSigningKeyRepository
import app.logdate.server.oauth.StoredOAuthSigningKey
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

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
