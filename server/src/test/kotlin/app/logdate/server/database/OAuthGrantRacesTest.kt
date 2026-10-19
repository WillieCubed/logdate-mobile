package app.logdate.server.database

import app.logdate.server.database.support.withH2Database
import app.logdate.server.oauth.OAuthRuntimeStateRepository
import app.logdate.server.oauth.StoredAuthorizationCode
import app.logdate.server.oauth.StoredRefreshToken
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.Assume.assumeTrue
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * A grant ends when the account revokes it, even for a refresh already in progress, and a refresh
 * token or authorization code can be redeemed once, however many requests race for it.
 */
class OAuthGrantRacesTest {
    @Test
    fun `a rotated token replaces the old one and the old one cannot be rotated again`() =
        withRuntimeTables {
            val repository = PostgreSQLOAuthRuntimeStateRepository()
            val now = Clock.System.now()
            val subject = subject()
            repository.saveRefreshToken(refreshToken("old", subject, grantedAt = now - 1.minutes, now = now))

            assertTrue(repository.rotateRefreshToken("old", refreshToken("new", subject, now - 1.minutes, now), now))
            assertFalse(repository.rotateRefreshToken("old", refreshToken("other", subject, now - 1.minutes, now), now))
            assertNull(repository.findRefreshToken("old"))
        }

    @Test
    fun `a grant approved before a revocation can no longer be refreshed or saved`() =
        withRuntimeTables {
            val repository = PostgreSQLOAuthRuntimeStateRepository()
            val now = Clock.System.now()
            val subject = subject()
            repository.saveRefreshToken(refreshToken("live", subject, grantedAt = now - 1.minutes, now = now))
            repository.saveAuthorizationCode(code("pending", subject, grantedAt = now - 1.minutes, now = now))

            repository.revokeRefreshTokensForSubject(subject, now)

            assertFalse(repository.rotateRefreshToken("live", refreshToken("next", subject, now - 1.minutes, now), now))
            assertFalse(repository.saveGrantedRefreshToken(refreshToken("late", subject, grantedAt = now - 1.minutes, now = now)))
            assertNull(repository.takeAuthorizationCode("pending"), "unredeemed codes are deleted")
            assertTrue(repository.saveGrantedRefreshToken(refreshToken("fresh", subject, grantedAt = now + 1.seconds, now = now)))
        }

    @Test
    fun `parallel refreshes of one token on PostgreSQL let exactly one through`() =
        withPostgres { repository ->
            val now = Clock.System.now()
            val subject = subject()
            repository.saveRefreshToken(refreshToken("$subject-old", subject, grantedAt = now - 1.minutes, now = now))

            val results =
                (1..12)
                    .map { attempt ->
                        async(Dispatchers.IO) {
                            repository.rotateRefreshToken(
                                "$subject-old",
                                refreshToken("$subject-new-$attempt", subject, now - 1.minutes, now),
                                now,
                            )
                        }
                    }.awaitAll()

            assertEquals(1, results.count { it }, "exactly one refresh replaces the token")
        }

    @Test
    fun `a refresh racing a revocation on PostgreSQL never leaves a usable token`() =
        withPostgres { repository ->
            repeat(10) { round ->
                val subject = subject()
                val grantedAt = Clock.System.now() - 1.minutes
                val now = Clock.System.now()
                repository.saveRefreshToken(refreshToken("$subject-old", subject, grantedAt, now))

                val rotate =
                    async(Dispatchers.IO) {
                        repository.rotateRefreshToken("$subject-old", refreshToken("$subject-new", subject, grantedAt, now), now)
                    }
                val revoke = async(Dispatchers.IO) { repository.revokeRefreshTokensForSubject(subject, Clock.System.now()) }
                rotate.await()
                revoke.await()

                val later = Clock.System.now()
                listOf("$subject-old", "$subject-new").forEach { survivor ->
                    assertFalse(
                        repository.rotateRefreshToken(survivor, refreshToken("$subject-after-$survivor", subject, grantedAt, later), later),
                        "round $round: $survivor is still usable after the revocation",
                    )
                }
            }
        }

    @Test
    fun `a code exchange racing a revocation on PostgreSQL never leaves a usable token`() =
        withPostgres { repository ->
            repeat(10) { round ->
                val subject = subject()
                val now = Clock.System.now()
                repository.saveAuthorizationCode(code("$subject-code", subject, now, now))

                val exchange =
                    async(Dispatchers.IO) {
                        val approved = repository.takeAuthorizationCode("$subject-code") ?: return@async false
                        repository.saveGrantedRefreshToken(refreshToken("$subject-token", subject, approved.grantedAt, now))
                    }
                val revoke = async(Dispatchers.IO) { repository.revokeRefreshTokensForSubject(subject, Clock.System.now()) }
                exchange.await()
                revoke.await()

                val later = Clock.System.now()
                assertFalse(
                    repository.rotateRefreshToken(
                        "$subject-token",
                        refreshToken("$subject-after", subject, later - 1.minutes, later),
                        later,
                    ),
                    "round $round: the token from an exchange that raced the revocation is still usable",
                )
            }
        }

    @Test
    fun `parallel exchanges of one authorization code on PostgreSQL redeem it once`() =
        withPostgres { repository ->
            val now = Clock.System.now()
            val subject = subject()
            repository.saveAuthorizationCode(code("$subject-code", subject, now, now))

            val redeemed = (1..12).map { async(Dispatchers.IO) { repository.takeAuthorizationCode("$subject-code") } }.awaitAll()

            assertEquals(1, redeemed.count { it != null })
        }

    private fun subject() = "did:plc:race${UUID.randomUUID().toString().replace("-", "").take(16)}"

    private fun refreshToken(
        token: String,
        subject: String,
        grantedAt: Instant,
        now: Instant,
    ) = StoredRefreshToken(
        token = token,
        clientId = "https://viewer.example.com/client.json",
        subjectDid = subject,
        scope = "atproto",
        dpopKeyThumbprint = "thumbprint",
        clientAuthKeyId = null,
        clientAuthKeyThumbprint = null,
        expiresAt = now + 14.days,
        grantedAt = grantedAt,
    )

    private fun code(
        code: String,
        subject: String,
        grantedAt: Instant,
        now: Instant,
    ) = StoredAuthorizationCode(
        code = code,
        clientId = "https://viewer.example.com/client.json",
        redirectUri = "https://viewer.example.com/callback",
        subjectDid = subject,
        subjectHandle = "race.logdate.app",
        scope = "atproto",
        codeChallenge = "example-challenge",
        dpopKeyThumbprint = "thumbprint",
        clientAuthKeyId = null,
        clientAuthKeyThumbprint = null,
        expiresAt = now + 1.minutes,
        grantedAt = grantedAt,
    )

    private fun withRuntimeTables(block: suspend () -> Unit) =
        withH2Database(OAuthAuthorizationCodesTable, OAuthRefreshTokensTable, OAuthSubjectRevocationsTable) {
            runBlocking { block() }
        }

    /** Runs on the PostgreSQL CI declares in `DATABASE_URL`, after Flyway applies every migration. */
    private fun withPostgres(block: suspend kotlinx.coroutines.CoroutineScope.(OAuthRuntimeStateRepository) -> Unit) {
        val url = System.getenv("DATABASE_URL")
        assumeTrue("DATABASE_URL is not set", !url.isNullOrBlank())
        val previous = TransactionManager.defaultDatabase
        val source = DatabaseConfig.createDataSource() as HikariDataSource
        try {
            TransactionManager.defaultDatabase = DatabaseConfig.initializeDatabase(source, autoMigrate = true)
            runBlocking { block(PostgreSQLOAuthRuntimeStateRepository()) }
        } finally {
            transaction {
                OAuthRefreshTokensTable.deleteWhere { clientId eq "https://viewer.example.com/client.json" }
                OAuthAuthorizationCodesTable.deleteWhere { clientId eq "https://viewer.example.com/client.json" }
                OAuthSubjectRevocationsTable.deleteWhere { subjectDid like "did:plc:race%" }
            }
            TransactionManager.defaultDatabase = previous
            source.close()
        }
    }
}
