package app.logdate.server.entitlements

import java.util.UUID

/**
 * Summarises how much of their [Entitlement.limits] an account has consumed. Used by
 * entitlement enforcement at the sync/backup endpoints to turn "is there room for this upload?"
 * into a boolean.
 *
 * Concrete implementation reads the existing media + backup tables. Keeping it behind an
 * interface lets tests feed synthetic usage without spinning up a database.
 */
interface UsageCalculator {
    suspend fun storageBytes(accountId: UUID): Long

    suspend fun backupCount(accountId: UUID): Int
}

/**
 * Indicates whether a pending write would exceed the account's quota. Returned by
 * [EntitlementEnforcer]; endpoints translate it into a 402 Payment Required with a structured
 * error when violated.
 */
sealed class QuotaCheck {
    data object Allowed : QuotaCheck()

    data class Denied(
        val reason: QuotaReason,
        val limit: Long,
        val current: Long,
    ) : QuotaCheck()
}

enum class QuotaReason { STORAGE_BYTES, BACKUP_COUNT }

/**
 * Quota gate used by write endpoints. Computes `current + incremental vs. limit` for the
 * dimension in question and emits [QuotaCheck.Denied] when the write would push past.
 *
 * **Status handling — payment lifecycle edge cases.** The enforcer deliberately reads only the
 * plan's *limits*, not the account's [EntitlementStatus]:
 *
 *  - `ACTIVE` and `PAST_DUE` and `GRACE` all yield the same quota. This is by design: a user in
 *    grace shouldn't get surprised with 402s, they should get a separate "your payment failed"
 *    nudge from the billing UI. Uploads keep working until the provider actually cancels.
 *  - `CANCELLED` keeps whatever plan row the account still points at — typically the plan they
 *    had before cancelling, until a later webhook overwrites the row to the `free` plan. That
 *    means downgrades coast through the end of the billing period rather than going cliff-edge
 *    the moment Stripe/Play notifies us, which matches standard SaaS expectations.
 *  - Provider swaps (a user who had Stripe buys on Play, or vice versa) are "last webhook write
 *    wins" because both providers' webhook handlers overwrite the single
 *    `account_entitlements` row keyed on account_id. Nothing extra to do here.
 */
class EntitlementEnforcer(
    private val entitlementService: EntitlementService,
    private val usageCalculator: UsageCalculator,
) {
    suspend fun checkMediaUpload(
        accountId: UUID,
        pendingBytes: Long,
    ): QuotaCheck {
        val entitlement = entitlementService.resolve(accountId)
        val limit = entitlement.limits.storageBytes ?: return QuotaCheck.Allowed
        val current = usageCalculator.storageBytes(accountId)
        val projected = current + pendingBytes
        return if (projected > limit) {
            QuotaCheck.Denied(QuotaReason.STORAGE_BYTES, limit = limit, current = current)
        } else {
            QuotaCheck.Allowed
        }
    }

    suspend fun checkBackupUpload(
        accountId: UUID,
        pendingBytes: Long,
    ): QuotaCheck {
        val entitlement = entitlementService.resolve(accountId)
        val storageLimit = entitlement.limits.storageBytes
        if (storageLimit != null) {
            val current = usageCalculator.storageBytes(accountId)
            if (current + pendingBytes > storageLimit) {
                return QuotaCheck.Denied(QuotaReason.STORAGE_BYTES, limit = storageLimit, current = current)
            }
        }
        val backupLimit = entitlement.limits.backupCount ?: return QuotaCheck.Allowed
        val current = usageCalculator.backupCount(accountId)
        return if (current + 1 > backupLimit) {
            QuotaCheck.Denied(QuotaReason.BACKUP_COUNT, limit = backupLimit.toLong(), current = current.toLong())
        } else {
            QuotaCheck.Allowed
        }
    }
}
