@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package app.logdate.server.database

import app.logdate.server.database.support.localTestPostgresDataSource
import app.logdate.server.database.support.localTestPostgresUrl
import app.logdate.server.devices.DeviceLinkCode
import app.logdate.server.devices.DeviceSessionLimits
import app.logdate.server.devices.LinkCodeRedemption
import app.logdate.server.devices.NewDeviceSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.uuid.Uuid

/** Runs against the local test PostgreSQL only; races real transactions on one link code. */
class DeviceLinkCodePostgresTest {
    @Test
    fun `concurrent redemptions of one link code create exactly one session`() =
        runBlocking {
            val url = localTestPostgresUrl()
            val previous = TransactionManager.defaultDatabase
            val source = localTestPostgresDataSource(url)
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
                deleteTestAccountRow(owner)
                source.close()
                TransactionManager.defaultDatabase = previous
            }
        }
}
