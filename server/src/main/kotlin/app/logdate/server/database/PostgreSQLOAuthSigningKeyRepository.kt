package app.logdate.server.database

import app.logdate.server.identity.EncryptedPrivateKey
import app.logdate.server.oauth.OAuthSigningKeyRepository
import app.logdate.server.oauth.OAuthSigningKeySet
import app.logdate.server.oauth.StoredOAuthSigningKey
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.time.Instant

internal class PostgreSQLOAuthSigningKeyRepository : OAuthSigningKeyRepository {
    override fun getOrCreate(candidate: () -> StoredOAuthSigningKey): StoredOAuthSigningKey = getOrInsert(ACTIVE_SLOT, candidate)

    override fun keySet(): OAuthSigningKeySet =
        transaction {
            val bySlot = OAuthSigningKeysTable.selectAll().associate { it[OAuthSigningKeysTable.slot] to it.toStoredKey() }
            OAuthSigningKeySet(active = bySlot[ACTIVE_SLOT], next = bySlot[NEXT_SLOT], retiring = bySlot[RETIRING_SLOT])
        }

    override fun stageNext(candidate: () -> StoredOAuthSigningKey): StoredOAuthSigningKey = getOrInsert(NEXT_SLOT, candidate)

    override fun promoteNext(
        expectedActiveKeyId: String,
        expectedNextKeyId: String,
        retireAfter: Instant,
    ): Boolean =
        try {
            transaction {
                OAuthSigningKeysTable.deleteWhere { slot eq RETIRING_SLOT }
                val retired =
                    OAuthSigningKeysTable.update({
                        (OAuthSigningKeysTable.slot eq ACTIVE_SLOT) and (OAuthSigningKeysTable.keyId eq expectedActiveKeyId)
                    }) {
                        it[slot] = RETIRING_SLOT
                        it[OAuthSigningKeysTable.retireAfter] = retireAfter
                    }
                // Another instance promoted first: roll back, including the delete above.
                if (retired != 1) throw PromotionLost()
                val promoted =
                    OAuthSigningKeysTable.update({
                        (OAuthSigningKeysTable.slot eq NEXT_SLOT) and (OAuthSigningKeysTable.keyId eq expectedNextKeyId)
                    }) {
                        it[slot] = ACTIVE_SLOT
                    }
                if (promoted != 1) throw PromotionLost()
            }
            true
        } catch (_: PromotionLost) {
            false
        }

    override fun deleteRetired(now: Instant): Boolean =
        transaction {
            OAuthSigningKeysTable.deleteWhere { (slot eq RETIRING_SLOT) and (retireAfter lessEq now) } > 0
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

    private fun getOrInsert(
        slotName: String,
        candidate: () -> StoredOAuthSigningKey,
    ): StoredOAuthSigningKey =
        transaction {
            find(slotName) ?: run {
                val key = candidate()
                // The slot is the primary key, so a concurrent instance's insert is ignored and both
                // instances read back whichever key was stored first.
                OAuthSigningKeysTable.insertIgnore {
                    it[slot] = slotName
                    it[keyId] = key.keyId
                    it[privateKeyEncrypted] = key.privateKeyEncrypted
                    it[publicKeySpki] = key.publicKeySpki
                    it[createdAt] = key.createdAt
                }
                checkNotNull(find(slotName)) { "OAuth signing key was not stored in the $slotName slot" }
            }
        }

    private fun find(slotName: String): StoredOAuthSigningKey? =
        OAuthSigningKeysTable
            .selectAll()
            .where { OAuthSigningKeysTable.slot eq slotName }
            .singleOrNull()
            ?.toStoredKey()

    private fun ResultRow.toStoredKey() =
        StoredOAuthSigningKey(
            keyId = this[OAuthSigningKeysTable.keyId],
            privateKeyEncrypted = this[OAuthSigningKeysTable.privateKeyEncrypted],
            publicKeySpki = this[OAuthSigningKeysTable.publicKeySpki],
            createdAt = this[OAuthSigningKeysTable.createdAt],
            retireAfter = this[OAuthSigningKeysTable.retireAfter],
        )

    private class PromotionLost : RuntimeException()

    private companion object {
        const val ACTIVE_SLOT = "active"
        const val NEXT_SLOT = "next"
        const val RETIRING_SLOT = "retiring"
    }
}
