package app.logdate.server.database

import app.logdate.server.atproto.AtprotoPasswordCredential
import app.logdate.server.atproto.AtprotoSession
import app.logdate.server.database.support.withH2Database
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class PostgreSQLAtprotoCredentialRepositoriesTest {
    @Test
    fun `password credential repository inserts updates and reads credentials`() =
        runTest {
            withH2Database(AccountsTable, AtprotoPasswordCredentialsTable) {
                val accountId = insertAccount(username = "password-user")
                val repository = PostgreSQLAtprotoPasswordCredentialRepository()
                val first =
                    AtprotoPasswordCredential(
                        accountId = accountId,
                        salt = "salt-1",
                        hash = "hash-1",
                        iterations = 1_000,
                        createdAtEpochMillis = 100L,
                        updatedAtEpochMillis = 200L,
                    )
                val updated =
                    first.copy(
                        salt = "salt-2",
                        hash = "hash-2",
                        iterations = 2_000,
                        updatedAtEpochMillis = 300L,
                    )

                runBlocking {
                    repository.save(first)
                    assertEquals(first, repository.findByAccountId(accountId))

                    repository.save(updated)
                    assertEquals(updated, repository.findByAccountId(accountId))
                    assertNull(repository.findByAccountId(Uuid.random()))
                }
            }
        }

    @Test
    fun `atproto session repository inserts updates reads and revokes sessions`() =
        runTest {
            withH2Database(AccountsTable, AtprotoSessionsTable) {
                val accountId = insertAccount(username = "session-user")
                val repository = PostgreSQLAtprotoSessionRepository()
                val first =
                    AtprotoSession(
                        id = "session-1",
                        accountId = accountId,
                        createdAtEpochMillis = 1_000L,
                        refreshExpiresAtEpochMillis = 2_000L,
                    )
                val updated =
                    first.copy(
                        refreshExpiresAtEpochMillis = 3_000L,
                        revokedAtEpochMillis = 2_500L,
                    )

                runBlocking {
                    repository.save(first)
                    assertEquals(first, repository.findById("session-1"))

                    repository.save(updated)
                    assertEquals(updated, repository.findById("session-1"))
                    assertTrue(repository.revoke("session-1"))
                    assertNotNull(repository.findById("session-1")?.revokedAtEpochMillis)
                    assertFalse(repository.revoke("missing-session"))
                    assertNull(repository.findById("missing-session"))
                }
            }
        }

    @Test
    fun `restore credential repository handles lookup updates and deactivation`() =
        runTest {
            withH2Database(AccountsTable, RestoreCredentialsTable) {
                val accountId = insertAccount(username = "restore-user")
                val otherAccountId = insertAccount(username = "restore-other")
                val repository = PostgreSQLRestoreCredentialRepository()

                runBlocking {
                    assertTrue(repository.store(accountId, "credential-1", byteArrayOf(1, 2, 3), signCount = 4L))
                    assertFalse(repository.store(accountId, "credential-1", byteArrayOf(1, 2, 3), signCount = 4L))
                    assertTrue(repository.store(otherAccountId, "credential-2", byteArrayOf(5, 6, 7), signCount = 8L))

                    val stored = assertNotNull(repository.findByCredentialId("credential-1"))
                    assertEquals("credential-1", stored.credentialId)
                    assertEquals(accountId, stored.userId)
                    assertEquals(4L, stored.signCount)
                    assertContentEquals(byteArrayOf(1, 2, 3), stored.publicKey)
                    insertLegacyRestoreCredential(accountId)
                    assertContentEquals("*".toByteArray(), repository.findByCredentialId("legacy-credential")?.publicKey)
                    assertEquals(listOf("credential-1", "legacy-credential"), repository.getCredentialIdsForUser(accountId))

                    assertTrue(repository.updateSignCount("credential-1", 9L))
                    assertEquals(9L, repository.findByCredentialId("credential-1")?.signCount)
                    assertFalse(repository.updateSignCount("missing-credential", 10L))

                    assertTrue(repository.deactivate("credential-1"))
                    assertNull(repository.findByCredentialId("credential-1"))
                    assertFalse(repository.deactivate("missing-credential"))

                    assertTrue(repository.deactivateAllForUser(otherAccountId))
                    assertEquals(emptyList(), repository.getCredentialIdsForUser(otherAccountId))
                    assertFalse(repository.deactivateAllForUser(Uuid.random()))
                }
            }
        }

    private fun insertAccount(username: String): Uuid {
        val accountId = Uuid.random()
        transaction {
            AccountsTable.insert {
                it[id] = accountId.toJavaUUID()
                it[AccountsTable.username] = username
                it[displayName] = username
                it[createdAt] = Clock.System.now()
                it[isActive] = true
                it[preferences] = "{}"
            }
        }
        return accountId
    }

    private fun insertLegacyRestoreCredential(accountId: Uuid) {
        transaction {
            RestoreCredentialsTable.insert {
                it[RestoreCredentialsTable.accountId] = accountId.toJavaUUID()
                it[credentialId] = "legacy-credential"
                it[publicKey] = "*"
                it[signCount] = 0L
                it[isActive] = true
                it[createdAt] = Clock.System.now()
                it[lastUsedAt] = null
            }
        }
    }
}
