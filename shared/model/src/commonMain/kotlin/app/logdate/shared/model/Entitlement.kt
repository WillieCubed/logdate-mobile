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
    /** Stable public plan identifier, such as `free` or `cloud-standard`. */
    val planId: String,
    /** Coarse plan tier used by UI copy and broad capability decisions. */
    val tier: EntitlementTierWire,
    /** Billing/subscription state that determines whether paid features are active. */
    val status: EntitlementStatusWire,
    /** Maximum synced media storage in bytes, or null for unlimited deployments. */
    val storageBytesLimit: Long?,
    /** Maximum retained backup snapshots, or null for unlimited deployments. */
    val backupCountLimit: Int?,
    /** Maximum Cloud transcription seconds per month, or null for unlimited deployments. */
    val transcriptionSecondsPerMonthLimit: Long? = null,
    /** Client-safe feature flags used to reveal premium controls before calling gated endpoints. */
    val features: Map<String, Boolean> = emptyMap(),
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
