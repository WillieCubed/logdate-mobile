@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package app.logdate.server.database

import app.logdate.server.auth.Account
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * Explicit disposable PostgreSQL only. Checks that the conditional identity write is what keeps
 * two server instances from both assigning a DID, and that it never inserts a deleted account.
 */
class AccountIdentityWritePostgresTest {
    @Test
    fun `only one concurrent identity write wins and a deleted account stays deleted`() =
        runBlocking {
            val url = System.getenv("LOGDATE_DIAGNOSTIC_TEST_DATABASE_URL")
            assumeTrue(System.getenv("LOGDATE_DIAGNOSTIC_TEST_DISPOSABLE") == "true" && url != null)
            require(java.net.URI(requireNotNull(url)).host in setOf("localhost", "127.0.0.1"))
            val previous = TransactionManager.defaultDatabase
            val source = DatabaseConfig.createDataSource(databaseUrl = url, username = null, password = null) as HikariDataSource
            try {
                TransactionManager.defaultDatabase = DatabaseConfig.initializeDatabase(source, autoMigrate = true)
                val repository = PostgreSQLAccountRepository()
                val suffix = Uuid.random().toHexString().take(8)
                val account =
                    repository.save(
                        Account(
                            id = Uuid.random(),
                            username = "cas_$suffix",
                            displayName = "Conditional",
                            createdAt = Clock.System.now(),
                            bio = "Unrelated column",
                        ),
                    )

                val attempts =
                    (1..8)
                        .map { attempt ->
                            async(Dispatchers.IO) {
                                repository.updateIdentityIfUnchanged(
                                    expected = account,
                                    updated =
                                        account.copy(
                                            handle = "cas-$suffix-$attempt.logdate.test",
                                            did = "did:plc:cas$suffix$attempt",
                                            signingKeyPublic = "zKey$attempt",
                                            bio = "Never written",
                                        ),
                                )
                            }
                        }.awaitAll()

                val winners = attempts.filterNotNull()
                assertEquals(1, winners.size)
                val stored = assertNotNull(repository.findById(account.id))
                assertEquals(winners.single().did, stored.did)
                assertEquals("Unrelated column", stored.bio)

                repository.deleteAccount(account.id)
                assertNull(repository.updateIdentityIfUnchanged(expected = stored, updated = stored.copy(handle = "back.logdate.test")))
                assertNull(repository.findById(account.id))
            } finally {
                TransactionManager.defaultDatabase = previous
                source.close()
            }
        }
}
