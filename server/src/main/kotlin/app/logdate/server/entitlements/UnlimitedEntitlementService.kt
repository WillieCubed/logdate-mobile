package app.logdate.server.entitlements

import java.util.UUID

/**
 * The default binding when `BILLING_PROVIDER=disabled`, i.e. the self-host path. Every account
 * sees unlimited storage and backups, and the billing-related routes are never registered so
 * there's nothing to misconfigure.
 *
 * Also useful in tests that don't care about quotas.
 */
class UnlimitedEntitlementService : EntitlementService {
    private val unlimited =
        Entitlement(
            planId = "self_host_unlimited",
            tier = EntitlementTier.UNLIMITED,
            status = EntitlementStatus.SELF_HOST,
            limits = EntitlementLimits(storageBytes = null, backupCount = null),
        )

    override suspend fun resolve(accountId: UUID): Entitlement = unlimited
}
