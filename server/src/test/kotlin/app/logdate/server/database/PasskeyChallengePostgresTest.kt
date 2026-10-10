@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package app.logdate.server.database

import app.logdate.shared.model.PasskeyChallenge
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.junit.Assume.assumeTrue
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.uuid.Uuid

class PasskeyChallengePostgresTest {
    @Test
    fun `migrated PostgreSQL keeps challenges durable isolated and single use`() {
        val url = System.getenv("LOGDATE_PASSKEY_TEST_DATABASE_URL")
        assumeTrue(url != null && System.getenv("LOGDATE_PASSKEY_TEST_DISPOSABLE") == "true")
        val uri = URI(requireNotNull(url))
        require(uri.host in setOf("localhost", "127.0.0.1") && uri.path.startsWith("/logdate_agent_passkey_"))
        val previous = TransactionManager.defaultDatabase
        val source = DatabaseConfig.createDataSource(databaseUrl = url, username = "logdate", password = "logdate") as HikariDataSource
        try {
            TransactionManager.defaultDatabase = DatabaseConfig.initializeDatabase(source, autoMigrate = true)
            runBlocking {
                val now = Clock.System.now()
                val challenge = PasskeyChallenge(Uuid.random().toString(), Uuid.random(), "registration", (now + 5.minutes).toString())
                PostgreSQLPasskeyChallengeRepository().save("passkey", challenge)
                assertNull(
                    PostgreSQLPasskeyChallengeRepository().consume("restore", challenge.challenge, "restore-registration", null, now),
                )
                assertNull(
                    PostgreSQLPasskeyChallengeRepository().consume("passkey", challenge.challenge, "registration", Uuid.random(), now),
                )
                val attempts =
                    (1..16)
                        .map {
                            async(Dispatchers.IO) {
                                PostgreSQLPasskeyChallengeRepository().consume(
                                    "passkey",
                                    challenge.challenge,
                                    "registration",
                                    challenge.userId,
                                    now,
                                )
                            }
                        }.awaitAll()
                assertEquals(1, attempts.count { it != null })
                val restore = challenge.copy(challenge = Uuid.random().toString(), type = "restore-authentication")
                PostgreSQLPasskeyChallengeRepository().save("restore", restore)
                assertNotNull(PostgreSQLPasskeyChallengeRepository().consume("restore", restore.challenge, restore.type, null, now))
                val expiry = Instant.fromEpochSeconds(now.epochSeconds + 60, 123_456_789)
                val expired = challenge.copy(challenge = Uuid.random().toString(), expiresAt = expiry.toString())
                PostgreSQLPasskeyChallengeRepository().save("passkey", expired)
                assertNull(PostgreSQLPasskeyChallengeRepository().consume("passkey", expired.challenge, expired.type, null, expiry))
            }
        } finally {
            TransactionManager.defaultDatabase = previous
            source.close()
        }
    }
}
