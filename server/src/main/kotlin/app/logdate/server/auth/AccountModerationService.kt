package app.logdate.server.auth

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Suspends, takes down or restores an account, and tells the people who follow it.
 *
 * The change is stored first. Followers of the repository event stream are then told with an
 * `#account` event naming the account's new status, so relays stop serving a moderated account and
 * resume serving a restored one. A moderated account is also no longer shown by the public repository
 * methods, which report its status instead of its contents.
 */
@OptIn(ExperimentalUuidApi::class)
class AccountModerationService(
    private val accounts: AccountRepository,
    private val statusEvents: AccountStatusEvents? = null,
) {
    /**
     * Sets [moderation] on [accountId], or clears it with `null`. Returns `false` when the account does
     * not exist. The account's own `isActive` flag decides what a cleared account reports: active, or
     * `deactivated`.
     */
    suspend fun moderate(
        accountId: Uuid,
        moderation: AccountModeration?,
    ): Boolean {
        if (!accounts.setModeration(accountId, moderation)) return false
        val account = accounts.findById(accountId) ?: return false
        val did = account.did ?: return true
        val status =
            when {
                moderation != null -> moderation.wireValue
                !account.isActive -> "deactivated"
                else -> null
            }
        statusEvents?.accountStatusChanged(did, active = status == null, status = status)
        return true
    }
}
