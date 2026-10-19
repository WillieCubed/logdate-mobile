@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package app.logdate.server.database

import app.logdate.server.auth.Account
import app.logdate.server.identity.HostedPlcOperationConflictException
import app.logdate.server.identity.StoredHostedPlcOperation
import app.logdate.shared.model.PasskeyChallenge
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
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

/**
 * Explicit disposable PostgreSQL only. Checks the recovery-key migration on a migrated database:
 * the operation log takes only one operation after each head, even from concurrent writers, and the
 * step-up challenge kind is stored and spent once.
 */
class RecoveryKeyMigrationPostgresTest {
    @Test
    fun `concurrent appends on one head store exactly one and step up challenges are single use`() =
        runBlocking {
            val url = System.getenv("LOGDATE_DIAGNOSTIC_TEST_DATABASE_URL")
            assumeTrue(System.getenv("LOGDATE_DIAGNOSTIC_TEST_DISPOSABLE") == "true" && url != null)
            require(java.net.URI(requireNotNull(url)).host in setOf("localhost", "127.0.0.1"))
            val previous = TransactionManager.defaultDatabase
            val source =
                DatabaseConfig.createDataSource(
                    databaseUrl = url,
                    username = null,
                    password = System.getenv("DATABASE_PASSWORD"),
                ) as HikariDataSource
            try {
                TransactionManager.defaultDatabase = DatabaseConfig.initializeDatabase(source, autoMigrate = true)
                val accounts = PostgreSQLAccountRepository()
                val suffix = Uuid.random().toHexString().take(8)
                val account =
                    accounts.save(
                        Account(id = Uuid.random(), username = "chain_$suffix", displayName = "Chain", createdAt = Clock.System.now()),
                    )
                val operations = PostgreSQLHostedPlcOperationRepository()
                val did = "did:plc:chain$suffix"

                fun row(
                    cid: String,
                    prevCid: String?,
                ) = StoredHostedPlcOperation(
                    id = Uuid.random(),
                    accountId = account.id,
                    did = did,
                    cid = cid,
                    prevCid = prevCid,
                    operationType = "plc_operation",
                    operationJson = """{"sig":"$cid"}""",
                )
                operations.save(row("genesis", null))
                val attempts =
                    (1..8)
                        .map { attempt ->
                            async(Dispatchers.IO) {
                                runCatching { operations.save(row("update-$attempt", "genesis")) }
                            }
                        }.awaitAll()

                assertEquals(1, attempts.count { it.isSuccess })
                assertEquals(7, attempts.count { it.exceptionOrNull() is HostedPlcOperationConflictException })
                assertEquals(2, operations.listByDid(did).size)

                val challenges = PostgreSQLPasskeyChallengeRepository()
                val now = Clock.System.now()
                val stepUp = PasskeyChallenge(Uuid.random().toString(), account.id, "step_up", (now + 5.minutes).toString())
                challenges.save("passkey", stepUp)
                assertNull(challenges.consume("passkey", stepUp.challenge, "authentication", null, now))
                assertNull(challenges.consume("passkey", stepUp.challenge, "step_up", Uuid.random(), now))
                assertNotNull(challenges.consume("passkey", stepUp.challenge, "step_up", account.id, now))
                assertNull(challenges.consume("passkey", stepUp.challenge, "step_up", account.id, now))

                assertEquals(true, accounts.deleteAccount(account.id))
            } finally {
                TransactionManager.defaultDatabase = previous
                source.close()
            }
        }
}
