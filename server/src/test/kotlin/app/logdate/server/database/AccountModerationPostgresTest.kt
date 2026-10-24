package app.logdate.server.database

import app.logdate.server.auth.Account
import app.logdate.server.auth.AccountModeration
import app.logdate.server.database.support.localTestPostgresDataSource
import app.logdate.server.database.support.withOwnPostgresDatabase
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Suspending or taking down an account, stored in a real PostgreSQL through the real migrations:
 * the value survives a read, an ordinary save never overwrites it, the database refuses a value it
 * does not know, and the public listing of accounts still pages through them.
 */
@OptIn(ExperimentalUuidApi::class)
class AccountModerationPostgresTest {
    private fun onPostgres(test: suspend (PostgreSQLAccountRepository) -> Unit) =
        withOwnPostgresDatabase("moderation_") { url ->
            localTestPostgresDataSource(url).use { source ->
                val previous = TransactionManager.defaultDatabase
                try {
                    TransactionManager.defaultDatabase = DatabaseConfig.initializeDatabase(source, autoMigrate = true)
                    runBlocking { test(PostgreSQLAccountRepository()) }
                } finally {
                    TransactionManager.defaultDatabase = previous
                }
            }
        }

    private fun account(name: String) = Account(id = Uuid.random(), username = name, displayName = name, createdAt = Clock.System.now())

    @Test
    fun `moderation is stored read back and cleared`() =
        onPostgres { accounts ->
            val alice = account("alice")
            accounts.create(alice)
            assertNull(accounts.findById(alice.id)?.moderation)

            assertTrue(accounts.setModeration(alice.id, AccountModeration.TAKENDOWN))
            assertEquals(AccountModeration.TAKENDOWN, accounts.findById(alice.id)?.moderation)
            assertTrue(accounts.setModeration(alice.id, AccountModeration.SUSPENDED))
            assertEquals(AccountModeration.SUSPENDED, accounts.findById(alice.id)?.moderation)
            assertTrue(accounts.setModeration(alice.id, null))
            assertNull(accounts.findById(alice.id)?.moderation)
        }

    @Test
    fun `an account that does not exist cannot be moderated`() =
        onPostgres { accounts ->
            assertFalse(accounts.setModeration(Uuid.random(), AccountModeration.SUSPENDED))
        }

    @Test
    fun `saving an account read before it was moderated does not undo the moderation`() =
        onPostgres { accounts ->
            val alice = account("alice")
            accounts.create(alice)
            val stale = accounts.findById(alice.id)!!
            accounts.setModeration(alice.id, AccountModeration.TAKENDOWN)

            accounts.save(stale.copy(displayName = "Alice renamed"))

            val stored = accounts.findById(alice.id)!!
            assertEquals("Alice renamed", stored.displayName)
            assertEquals(AccountModeration.TAKENDOWN, stored.moderation)
        }

    @Test
    fun `the database refuses a moderation value it does not know`() =
        onPostgres { accounts ->
            val alice = account("alice")
            accounts.create(alice)

            assertFailsWith<Exception> {
                org.jetbrains.exposed.v1.jdbc.transactions.transaction {
                    exec("UPDATE accounts SET moderation = 'banished' WHERE id = '${alice.id}'")
                }
            }
        }
}
