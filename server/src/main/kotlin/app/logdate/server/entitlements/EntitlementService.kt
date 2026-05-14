package app.logdate.server.entitlements

import java.util.UUID

/**
 * The one interface the rest of the server talks to when it needs to decide "can this account do X?".
 *
 * Deliberately provider-agnostic: sync/backup endpoints call [resolve] and then check the returned
 * [Entitlement.limits] or [Entitlement.features]. How the entitlement got populated — Stripe,
 * Google Play, a manual grant, or the permissive self-host default — is the billing module's
 * problem, not theirs.
 *
 * Self-hosters get [UnlimitedEntitlementService] by default so the backend works with no billing
 * code reachable. Production binds [StoredEntitlementService], which reads the
 * `account_entitlements` table populated by webhook handlers.
 */
interface EntitlementService {
    /**
     * Resolve the effective entitlement for [accountId]. Always returns a value — unrecognized or
     * missing accounts fall through to the free plan so the UI doesn't have to branch on null.
     */
    suspend fun resolve(accountId: UUID): Entitlement
}

/**
 * What [accountId] is allowed to do right now.
 *
 * [limits] is the numeric cap (null = unlimited). [status] is the subscription-state lens — a
 * past_due or grace user still has access but is on borrowed time; the UI should nudge them to
 * fix billing. [features] is a JSON bag for plan-level flags the product team can ship without
 * requiring a schema migration.
 */
data class Entitlement(
    val planId: String,
    val tier: EntitlementTier,
    val status: EntitlementStatus,
    val limits: EntitlementLimits,
    val features: Map<String, Boolean> = emptyMap(),
) {
    fun hasFeature(feature: EntitlementFeature): Boolean = features[feature.key] == true
}

enum class EntitlementFeature(
    val key: String,
) {
    CLOUD_TRANSCRIPTION_REALTIME("cloud_transcription_realtime"),
    CLOUD_TRANSCRIPT_REFINEMENT("cloud_transcript_refinement"),

    /**
     * Gates the Android Digital Credentials email-verification flow
     * (`/api/v1/auth/me/email/verify/{begin,complete}` plus the onboarding
     * sub-step and Settings entry point). Currently on for self-host /
     * unlimited installs; paid plans default off until we promote the
     * underlying alpha credentials artifact.
     */
    EMAIL_VERIFICATION("email_verification"),
}

enum class EntitlementTier { FREE, STANDARD, PRO, UNLIMITED }

enum class EntitlementStatus {
    /** Paid and current. Full access. */
    ACTIVE,

    /** Payment failed; within grace window. Treat as active but warn the user. */
    PAST_DUE,

    /** Provider-declared grace (e.g. Play hold). Treat as active but warn. */
    GRACE,

    /** Subscription ended. Reads work, writes rejected over quota. */
    CANCELLED,

    /**
     * Unbillable deployment (self-host, BILLING_PROVIDER=disabled). Unlimited access with no
     * provider-backed contract. The UI should not show billing-related UI for these accounts.
     */
    SELF_HOST,
}

/**
 * Quota caps. `null` means unlimited for that dimension. Enforcement happens at the point of
 * write — see `SyncRoutes` where media uploads consult `storageBytes`.
 */
data class EntitlementLimits(
    val storageBytes: Long?,
    val backupCount: Int?,
    val transcriptionSecondsPerMonth: Long? = null,
)
