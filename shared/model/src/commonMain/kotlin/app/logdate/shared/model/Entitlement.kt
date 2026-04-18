package app.logdate.shared.model

import kotlinx.serialization.Serializable

/**
 * Wire shape returned by `GET /api/v1/auth/me/entitlement`.
 *
 * Every field is client-safe — no internal IDs, no billing-provider details. Clients read this to
 * render the Settings "Your plan" row, to decide whether to show a "You're over quota" banner,
 * and to cap the size of uploads before hitting a server-side 402.
 */
@Serializable
data class EntitlementResponse(
    val planId: String,
    val tier: EntitlementTierWire,
    val status: EntitlementStatusWire,
    val storageBytesLimit: Long?,
    val backupCountLimit: Int?,
)

@Serializable
enum class EntitlementTierWire {
    FREE,
    STANDARD,
    PRO,
    UNLIMITED,
}

@Serializable
enum class EntitlementStatusWire {
    /** Paid, current, full access. */
    ACTIVE,

    /** Payment failed but still inside the grace window — treat as active with a nudge. */
    PAST_DUE,

    /** Provider-declared grace period (e.g. Play hold). */
    GRACE,

    /** Subscription ended. Reads work, writes start failing with 402 once over quota. */
    CANCELLED,

    /**
     * The server is running without billing (self-host). UI should hide billing-related surfaces
     * and treat the account as unlimited.
     */
    SELF_HOST,
}
