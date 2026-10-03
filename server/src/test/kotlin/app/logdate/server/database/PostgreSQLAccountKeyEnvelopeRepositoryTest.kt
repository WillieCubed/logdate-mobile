@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package app.logdate.server.database

import app.logdate.server.database.support.withH2Database
import app.logdate.shared.model.PasskeyInfo
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

class PostgreSQLAccountKeyEnvelopeRepositoryTest {
    @Test
    fun `opaque storage survives reopening rejects replacement and cascades with passkey deletion`() =
        withH2Database(AccountsTable, PasskeysTable, AccountKeyEnvelopeTable) {
            val owner = Uuid.random()
            val other = Uuid.random()
            transaction {
                listOf(owner, other).forEachIndexed { index, account ->
                    AccountsTable.insert {
                        it[id] = account.toJavaUuid()
                        it[username] = "key-envelope-$index"
                        it[displayName] = "Test account"
                        it[createdAt] = Clock.System.now()
                    }
                }
            }
            runBlocking {
                val passkeys = PostgreSQLPasskeyRepository()
                passkeys.storePasskey(
                    owner,
                    "AQ",
                    byteArrayOf(1),
                    0,
                    PasskeyInfo(Uuid.random(), "AQ", null, "platform", Clock.System.now(), null),
                )
                val repository = PostgreSQLAccountKeyEnvelopeRepository()
                assertFalse(repository.put(other.toJavaUuid(), "AQ", "ciphertext"))
                assertFalse(repository.put(owner.toJavaUuid(), "missing", "ciphertext"))
                assertTrue(repository.put(owner.toJavaUuid(), "AQ", "ciphertext"))
                val reopened = PostgreSQLAccountKeyEnvelopeRepository()
                assertTrue(reopened.put(owner.toJavaUuid(), "AQ", "ciphertext"))
                assertFalse(reopened.put(owner.toJavaUuid(), "AQ", "replacement"))
                assertEquals("ciphertext", reopened.get(owner.toJavaUuid(), "AQ"))
                assertNull(reopened.get(other.toJavaUuid(), "AQ"))
                passkeys.deactivatePasskey("AQ", owner)
                assertNull(reopened.get(owner.toJavaUuid(), "AQ"))
                assertFalse(reopened.put(owner.toJavaUuid(), "AQ", "ciphertext"))
                transaction { PasskeysTable.deleteWhere { credentialId eq "AQ" } }
                assertNull(reopened.get(owner.toJavaUuid(), "AQ"))
                transaction {
                    AccountsTable.deleteWhere { id eq owner.toJavaUuid() }
                }
            }
        }
}
