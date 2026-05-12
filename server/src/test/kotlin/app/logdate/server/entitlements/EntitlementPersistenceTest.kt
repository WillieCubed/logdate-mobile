package app.logdate.server.entitlements

import app.logdate.server.database.LogDateBackupsTable
import app.logdate.server.database.LogDateMediaRecordsTable
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Clock

class EntitlementPersistenceTest {
    @Test
    fun `stored service falls back to active free plan when account has no subscription row`() =
        runTest {
            withDatabase(PlansTable, AccountEntitlementsTable) { database ->
                insertPlan(id = "free", tier = "free", storageBytes = 50_000L, backupCount = 2)

                val entitlement = StoredEntitlementService(database).resolve(UUID.randomUUID())

                assertEquals("free", entitlement.planId)
                assertEquals(EntitlementTier.FREE, entitlement.tier)
                assertEquals(EntitlementStatus.ACTIVE, entitlement.status)
                assertEquals(50_000L, entitlement.limits.storageBytes)
                assertEquals(2, entitlement.limits.backupCount)
            }
        }

    @Test
    fun `stored service maps account plan tier status and nullable unlimited limits`() =
        runTest {
            withDatabase(PlansTable, AccountEntitlementsTable) { database ->
                val accountId = UUID.randomUUID()
                insertPlan(id = "pro", tier = "PRO", storageBytes = null, backupCount = null)
                insertAccountEntitlement(accountId = accountId, planId = "pro", status = "past_due")

                val entitlement = StoredEntitlementService(database).resolve(accountId)

                assertEquals("pro", entitlement.planId)
                assertEquals(EntitlementTier.PRO, entitlement.tier)
                assertEquals(EntitlementStatus.PAST_DUE, entitlement.status)
                assertNull(entitlement.limits.storageBytes)
                assertNull(entitlement.limits.backupCount)
            }
        }

    @Test
    fun `stored service maps standard unlimited grace and cancelled account states`() =
        runTest {
            withDatabase(PlansTable, AccountEntitlementsTable) { database ->
                val standardAccountId = UUID.randomUUID()
                val unlimitedAccountId = UUID.randomUUID()
                insertPlan(id = "standard", tier = "standard", storageBytes = 100L, backupCount = 3)
                insertPlan(id = "unlimited", tier = "unlimited", storageBytes = null, backupCount = null)
                insertAccountEntitlement(accountId = standardAccountId, planId = "standard", status = "grace")
                insertAccountEntitlement(accountId = unlimitedAccountId, planId = "unlimited", status = "cancelled")

                val standard = StoredEntitlementService(database).resolve(standardAccountId)
                val unlimited = StoredEntitlementService(database).resolve(unlimitedAccountId)

                assertEquals(EntitlementTier.STANDARD, standard.tier)
                assertEquals(EntitlementStatus.GRACE, standard.status)
                assertEquals(EntitlementTier.UNLIMITED, unlimited.tier)
                assertEquals(EntitlementStatus.CANCELLED, unlimited.status)
            }
        }

    @Test
    fun `stored service defaults unknown tier and status values conservatively`() =
        runTest {
            withDatabase(PlansTable, AccountEntitlementsTable) { database ->
                val accountId = UUID.randomUUID()
                insertPlan(id = "custom", tier = "custom", storageBytes = 10L, backupCount = 1)
                insertAccountEntitlement(accountId = accountId, planId = "custom", status = "unexpected")

                val entitlement = StoredEntitlementService(database).resolve(accountId)

                assertEquals(EntitlementTier.FREE, entitlement.tier)
                assertEquals(EntitlementStatus.ACTIVE, entitlement.status)
            }
        }

    @Test
    fun `stored service denies by default when selected plan is inactive or missing`() =
        runTest {
            withDatabase(PlansTable, AccountEntitlementsTable) { database ->
                val inactiveAccountId = UUID.randomUUID()
                val missingAccountId = UUID.randomUUID()
                insertPlan(id = "standard", tier = "standard", storageBytes = 100L, backupCount = 1, active = false)
                insertAccountEntitlement(accountId = inactiveAccountId, planId = "standard", status = "active")
                insertAccountEntitlement(accountId = missingAccountId, planId = "missing", status = "canceled")

                val inactivePlanEntitlement = StoredEntitlementService(database).resolve(inactiveAccountId)
                val missingPlanEntitlement = StoredEntitlementService(database).resolve(missingAccountId)

                assertEquals(EntitlementStatus.CANCELLED, inactivePlanEntitlement.status)
                assertEquals(0L, inactivePlanEntitlement.limits.storageBytes)
                assertEquals(0, inactivePlanEntitlement.limits.backupCount)
                assertEquals("missing", missingPlanEntitlement.planId)
                assertEquals(EntitlementStatus.CANCELLED, missingPlanEntitlement.status)
            }
        }

    @Test
    fun `database usage calculator sums non-deleted media and backups for one account`() =
        runTest {
            withDatabase(LogDateMediaRecordsTable, LogDateBackupsTable) { database ->
                val accountId = UUID.randomUUID()
                val otherAccountId = UUID.randomUUID()
                insertMedia(accountId = accountId, mediaId = "media-1", bytes = 30L)
                insertMedia(accountId = accountId, mediaId = "media-2", bytes = 40L, deleted = true)
                insertMedia(accountId = otherAccountId, mediaId = "media-3", bytes = 70L)
                insertBackup(accountId = accountId, bytes = 50L)
                insertBackup(accountId = accountId, bytes = 20L)
                insertBackup(accountId = otherAccountId, bytes = 90L)

                val calculator = DatabaseUsageCalculator(database)

                assertEquals(100L, calculator.storageBytes(accountId))
                assertEquals(2, calculator.backupCount(accountId))
            }
        }

    @Test
    fun `database usage calculator returns zeroes when account has no rows`() =
        runTest {
            withDatabase(LogDateMediaRecordsTable, LogDateBackupsTable) { database ->
                val calculator = DatabaseUsageCalculator(database)

                assertEquals(0L, calculator.storageBytes(UUID.randomUUID()))
                assertEquals(0, calculator.backupCount(UUID.randomUUID()))
            }
        }

    private suspend fun withDatabase(
        vararg tables: Table,
        block: suspend (Database) -> Unit,
    ) {
        val database =
            Database.connect(
                url = "jdbc:h2:mem:entitlement_test_${System.nanoTime()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
                driver = "org.h2.Driver",
                user = "sa",
                password = "",
            )

        transaction(database) {
            SchemaUtils.create(*tables)
        }

        try {
            block(database)
        } finally {
            transaction(database) {
                SchemaUtils.drop(*tables)
            }
        }
    }

    private fun insertPlan(
        id: String,
        tier: String,
        storageBytes: Long?,
        backupCount: Int?,
        active: Boolean = true,
    ) {
        transaction {
            PlansTable.insert {
                it[PlansTable.id] = id
                it[name] = "$id plan"
                it[PlansTable.tier] = tier
                it[monthlyBytesLimit] = storageBytes
                it[backupCountLimit] = backupCount
                it[stripePriceId] = null
                it[playProductId] = null
                it[PlansTable.active] = active
            }
        }
    }

    private fun insertAccountEntitlement(
        accountId: UUID,
        planId: String,
        status: String,
    ) {
        transaction {
            AccountEntitlementsTable.insert {
                it[AccountEntitlementsTable.accountId] = accountId
                it[AccountEntitlementsTable.planId] = planId
                it[entitlementSource] = "test"
                it[externalSubscriptionId] = null
                it[AccountEntitlementsTable.status] = status
                it[currentPeriodEnd] = null
                it[updatedAt] = Clock.System.now()
            }
        }
    }

    private fun insertMedia(
        accountId: UUID,
        mediaId: String,
        bytes: Long,
        deleted: Boolean = false,
    ) {
        transaction {
            LogDateMediaRecordsTable.insert {
                it[userId] = accountId
                it[LogDateMediaRecordsTable.mediaId] = mediaId
                it[contentId] = "content-$mediaId"
                it[fileName] = "$mediaId.jpg"
                it[mimeType] = "image/jpeg"
                it[sizeBytes] = bytes
                it[data] = byteArrayOf(1, 2, 3)
                it[storagePath] = null
                it[createdAt] = 1L
                it[version] = 1L
                it[deviceId] = "device"
                it[LogDateMediaRecordsTable.deleted] = deleted
                it[deletedAt] = if (deleted) 2L else null
                it[encryptionVersion] = null
                it[encryptionKeyId] = null
                it[encryptionMode] = null
            }
        }
    }

    private fun insertBackup(
        accountId: UUID,
        bytes: Long,
    ) {
        transaction {
            LogDateBackupsTable.insert {
                it[id] = UUID.randomUUID()
                it[userId] = accountId
                it[deviceId] = "device"
                it[manifest] = "{}"
                it[storagePath] = "backups/${UUID.randomUUID()}"
                it[createdAt] = 1L
                it[sizeBytes] = bytes
            }
        }
    }
}
