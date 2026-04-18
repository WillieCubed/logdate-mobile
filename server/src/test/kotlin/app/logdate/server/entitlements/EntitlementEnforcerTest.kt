package app.logdate.server.entitlements

import kotlinx.coroutines.test.runTest
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class EntitlementEnforcerTest {
    private val accountId = UUID.randomUUID()

    @Test
    fun `unlimited plan allows any media upload regardless of usage`() =
        runTest {
            val enforcer =
                EntitlementEnforcer(
                    entitlementService = FakeService(entitlement = unlimited()),
                    usageCalculator = StaticUsage(bytes = 999_999_999_999L, backups = 999_999),
                )
            val result = enforcer.checkMediaUpload(accountId, pendingBytes = 1_000_000)
            assertIs<QuotaCheck.Allowed>(result)
        }

    @Test
    fun `free plan denies media upload once limit is exceeded`() =
        runTest {
            val enforcer =
                EntitlementEnforcer(
                    entitlementService = FakeService(entitlement = tier(storage = 1_000_000L)),
                    usageCalculator = StaticUsage(bytes = 900_000L),
                )
            val result = enforcer.checkMediaUpload(accountId, pendingBytes = 200_000L)
            val denied = assertIs<QuotaCheck.Denied>(result)
            assertEquals(QuotaReason.STORAGE_BYTES, denied.reason)
            assertEquals(1_000_000L, denied.limit)
        }

    @Test
    fun `free plan allows upload that fits under the storage limit`() =
        runTest {
            val enforcer =
                EntitlementEnforcer(
                    entitlementService = FakeService(entitlement = tier(storage = 1_000_000L)),
                    usageCalculator = StaticUsage(bytes = 900_000L),
                )
            val result = enforcer.checkMediaUpload(accountId, pendingBytes = 50_000L)
            assertIs<QuotaCheck.Allowed>(result)
        }

    @Test
    fun `backup upload denies once backup-count limit is hit even when storage has room`() =
        runTest {
            val enforcer =
                EntitlementEnforcer(
                    entitlementService =
                        FakeService(entitlement = tier(storage = 1_000_000_000L, backups = 3)),
                    usageCalculator = StaticUsage(bytes = 0L, backups = 3),
                )
            val result = enforcer.checkBackupUpload(accountId, pendingBytes = 1_000L)
            val denied = assertIs<QuotaCheck.Denied>(result)
            assertEquals(QuotaReason.BACKUP_COUNT, denied.reason)
        }

    @Test
    fun `backup upload denies on storage limit even if backup count has room`() =
        runTest {
            val enforcer =
                EntitlementEnforcer(
                    entitlementService =
                        FakeService(entitlement = tier(storage = 1_000L, backups = 100)),
                    usageCalculator = StaticUsage(bytes = 900L, backups = 0),
                )
            val result = enforcer.checkBackupUpload(accountId, pendingBytes = 500L)
            val denied = assertIs<QuotaCheck.Denied>(result)
            assertEquals(QuotaReason.STORAGE_BYTES, denied.reason)
        }

    @Test
    fun `UnlimitedEntitlementService always returns SELF_HOST status`() =
        runTest {
            val service = UnlimitedEntitlementService()
            val result = service.resolve(accountId)
            assertEquals(EntitlementStatus.SELF_HOST, result.status)
            assertTrue(result.limits.storageBytes == null)
            assertTrue(result.limits.backupCount == null)
        }

    private fun unlimited(): Entitlement =
        Entitlement(
            planId = "unlimited",
            tier = EntitlementTier.UNLIMITED,
            status = EntitlementStatus.ACTIVE,
            limits = EntitlementLimits(storageBytes = null, backupCount = null),
        )

    private fun tier(
        storage: Long?,
        backups: Int? = null,
    ): Entitlement =
        Entitlement(
            planId = "standard",
            tier = EntitlementTier.STANDARD,
            status = EntitlementStatus.ACTIVE,
            limits = EntitlementLimits(storageBytes = storage, backupCount = backups),
        )

    private class FakeService(
        val entitlement: Entitlement,
    ) : EntitlementService {
        override suspend fun resolve(accountId: UUID): Entitlement = entitlement
    }

    private class StaticUsage(
        val bytes: Long = 0L,
        val backups: Int = 0,
    ) : UsageCalculator {
        override suspend fun storageBytes(accountId: UUID): Long = bytes

        override suspend fun backupCount(accountId: UUID): Int = backups
    }
}
