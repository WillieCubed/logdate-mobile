@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package app.logdate.server.database

import app.logdate.server.devices.DeviceLinkCode
import app.logdate.server.devices.DeviceSessionLimits
import app.logdate.server.devices.LinkCodeRedemption
import app.logdate.server.devices.NewDeviceSession
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.uuid.Uuid

/** Explicit disposable PostgreSQL only; races real transactions on one link code. */
class DeviceLinkCodePostgresTest {
    @Test
    fun `concurrent redemptions of one link code create exactly one session`() =
        runBlocking {
            val url = System.getenv("LOGDATE_DIAGNOSTIC_TEST_DATABASE_URL")
            assumeTrue(System.getenv("LOGDATE_DIAGNOSTIC_TEST_DISPOSABLE") == "true" && url != null)
            require(java.net.URI(requireNotNull(url)).host in setOf("localhost", "127.0.0.1"))
            val previous = TransactionManager.defaultDatabase
            val source = DatabaseConfig.createDataSource(databaseUrl = url, username = null, password = null) as HikariDataSource
            val owner = Uuid.random().toJavaUUID()
            try {
                TransactionManager.defaultDatabase = DatabaseConfig.initializeDatabase(source, autoMigrate = true)
                transaction {
                    AccountsTable.insert {
                        it[AccountsTable.id] = owner
                        it[username] = "link_race_${owner.toString().take(8)}"
                        it[displayName] = "Link race"
                        it[createdAt] = Clock.System.now()
                        it[isActive] = true
                        it[preferences] = "{}"
                    }
                }
                val repository = PostgreSQLDeviceSessionRepository()
                val limits = DeviceSessionLimits(maxActiveSessions = 5, maxCodesPerHour = 10)
                val now = System.currentTimeMillis()
                val hash = "race-${Uuid.random().toHexString()}"
                repository.createLinkCode(DeviceLinkCode(hash, owner, now, now + 120_000), limits)

                val sessions =
                    (1..8).map { index ->
                        NewDeviceSession(Uuid.random().toJavaUUID(), "Watch $index", "wear_os", now + index, now + 86_400_000)
                    }
                val outcomes =
                    sessions
                        .map { session ->
                            async(Dispatchers.IO) { runCatching { repository.redeemLinkCode(hash, session, limits) }.getOrNull() }
                        }.awaitAll()

                assertEquals(1, outcomes.count { it is LinkCodeRedemption.Redeemed })
                assertEquals(1, repository.listActive(owner, now + 100).size)
            } finally {
                transaction { AccountsTable.deleteWhere { AccountsTable.id eq owner } }
                source.close()
                TransactionManager.defaultDatabase = previous
            }
        }
}
