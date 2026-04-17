package app.logdate.server.entitlements

import app.logdate.server.billing.BillingProvider
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin bindings for entitlement resolution and quota enforcement.
 *
 * Selection is driven by [BillingProvider.fromEnvironment]:
 *
 *  - [BillingProvider.Disabled] (default, self-host) binds [UnlimitedEntitlementService] + a
 *    [UnlimitedUsageCalculator] — every account sees unlimited limits, no database traffic.
 *  - Any active provider binds [StoredEntitlementService] + [DatabaseUsageCalculator] so webhook
 *    handlers and the sync endpoints see the same `account_entitlements` table.
 *
 * Keeping this in its own module means the billing-disabled deployment never touches the
 * `plans` / `account_entitlements` tables, and a self-hoster running without Postgres still works.
 */
fun entitlementsModule(
    provider: BillingProvider = BillingProvider.fromEnvironment(),
    databaseAvailable: Boolean,
): Module =
    module {
        single { provider }
        if (provider == BillingProvider.Disabled || !databaseAvailable) {
            single<EntitlementService> { UnlimitedEntitlementService() }
            single<UsageCalculator> { UnlimitedUsageCalculator }
        } else {
            // Exposed's TransactionManager tracks the default Database set by Database.connect()
            // during DatabaseConfig.initializeDatabase — the rest of the server's Postgres repos
            // rely on the same thread-local handle. Binding it here makes the dependency explicit
            // for the services that consume it without requiring a broader refactor first.
            single<Database> { TransactionManager.defaultDatabase!! }
            single<EntitlementService> { StoredEntitlementService(database = get()) }
            single<UsageCalculator> { DatabaseUsageCalculator(database = get()) }
        }
        single { EntitlementEnforcer(entitlementService = get(), usageCalculator = get()) }
    }

/**
 * No-op usage calculator used when billing is disabled. Returns zero for every dimension so the
 * [EntitlementEnforcer]'s quota checks always pass the `current + pending < limit` comparison —
 * which is immaterial anyway because [UnlimitedEntitlementService.resolve] returns `null` limits.
 */
internal object UnlimitedUsageCalculator : UsageCalculator {
    override suspend fun storageBytes(accountId: java.util.UUID): Long = 0L

    override suspend fun backupCount(accountId: java.util.UUID): Int = 0
}
