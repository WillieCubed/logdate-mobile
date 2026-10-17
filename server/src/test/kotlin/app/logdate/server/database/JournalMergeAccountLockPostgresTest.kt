package app.logdate.server.database

import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assume.assumeTrue
import java.net.URI
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

/**
 * Production reaches Postgres through a transaction-pooling proxy, which may run consecutive
 * autocommit statements from one client connection on different server backends. Point
 * LOGDATE_JOURNAL_MERGE_TEST_DATABASE_URL at such a proxy (for example PgBouncer with
 * `pool_mode = transaction`) to reproduce that routing; a direct connection also passes.
 */
class JournalMergeAccountLockPostgresTest {
    @Test
    fun `account lock is released when other traffic takes the backend that acquired it`() =
        withDisposableDataSource { dataSource ->
            val store = PostgreSQLJournalMergeStore(dataSource)
            val otherInstance = PostgreSQLJournalMergeStore(dataSource)
            val user = UUID.randomUUID()
            repeat(3) {
                dataSource.connection.use { otherTraffic ->
                    store.withAccountLock(user) {
                        // Opens a transaction on the most recently released pooled backend.
                        otherTraffic.createStatement().use { it.execute("SELECT 1") }
                    }
                    assertAcquires(otherInstance, user)
                    otherTraffic.rollback()
                }
            }
            assertEquals(0, advisoryLockCount(dataSource, user), "An account lock outlived its holder")
        }

    @Test
    fun `account lock is released when its action fails`() =
        withDisposableDataSource { dataSource ->
            val store = PostgreSQLJournalMergeStore(dataSource)
            val user = UUID.randomUUID()
            assertFailsWith<IllegalStateException> {
                store.withAccountLock(user) { error("Merge write failed") }
            }
            assertAcquires(PostgreSQLJournalMergeStore(dataSource), user)
            assertEquals(0, advisoryLockCount(dataSource, user), "A failed action left its account lock held")
        }

    private suspend fun assertAcquires(
        store: PostgreSQLJournalMergeStore,
        user: UUID,
    ) {
        // Not a child of the test: a blocked lock statement cannot be cancelled, only abandoned.
        val attempt = CoroutineScope(Dispatchers.IO).async { store.withAccountLock(user) { } }
        assertNotNull(withTimeoutOrNull(5_000) { attempt.await() }, "Another instance could not take the released account lock")
    }

    private fun advisoryLockCount(
        dataSource: HikariDataSource,
        user: UUID,
    ): Int =
        dataSource.connection.use { connection ->
            val key = user.mostSignificantBits xor user.leastSignificantBits
            connection
                .prepareStatement(
                    "SELECT count(*) FROM pg_locks WHERE locktype = 'advisory' AND objsubid = 1 AND classid::bigint = ? AND objid::bigint = ?",
                ).use { statement ->
                    statement.setLong(1, key ushr 32)
                    statement.setLong(2, key and 0xFFFFFFFFL)
                    statement.executeQuery().use { rows ->
                        rows.next()
                        rows.getInt(1)
                    }
                }
        }

    private fun withDisposableDataSource(test: suspend (HikariDataSource) -> Unit) {
        val url = System.getenv("LOGDATE_JOURNAL_MERGE_TEST_DATABASE_URL")
        assumeTrue(url != null && System.getenv("LOGDATE_JOURNAL_MERGE_TEST_DISPOSABLE") == "true")
        val uri = URI(requireNotNull(url))
        require(uri.host in setOf("localhost", "127.0.0.1") && uri.path.startsWith("/logdate_agent_journal_merge_"))
        // Credentials come from the URL, for example postgresql://user:password@localhost:6432/...
        val dataSource = DatabaseConfig.createDataSource(databaseUrl = url, username = null, password = null) as HikariDataSource
        // Closing the pool also ends any lock statement left blocked by a failed assertion.
        dataSource.use { runBlocking { test(it) } }
    }
}
