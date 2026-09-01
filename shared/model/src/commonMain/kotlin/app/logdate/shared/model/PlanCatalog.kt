package app.logdate.shared.model

import kotlinx.serialization.Serializable

/**
 * A plan a server offers, as returned by `GET /api/v1/plans`.
 *
 * This is the catalog of what someone *could* have, which is different from
 * [EntitlementResponse] — that describes what a signed-in account actually has. The catalog is
 * readable without an account so it can be shown while someone is deciding whether to create one.
 *
 * Prices deliberately do not appear here. A store returns them already formatted for the viewer's
 * locale and currency, so the store product identifiers below are the join key and the store is
 * asked for the price at display time. Sending a price string from the server would be wrong for
 * everyone outside its assumed currency.
 */
@Serializable
data class PlanOption(
    /** Stable public identifier, such as `free` or `standard`. */
    val id: String,
    /** Display name for the plan. */
    val name: String,
    /** Coarse tier used for UI copy and capability decisions. */
    val tier: EntitlementTierWire,
    /** Maximum synced media storage in bytes, or null when the plan is unlimited. */
    val storageBytesLimit: Long?,
    /** Maximum retained backup snapshots, or null when the plan is unlimited. */
    val backupCountLimit: Int?,
    /** Client-safe feature flags this plan unlocks. */
    val features: Map<String, Boolean> = emptyMap(),
    /** Google Play subscription product id, or null when the plan is not purchasable there. */
    val playProductId: String? = null,
    /** Stripe price id, or null when the plan is not purchasable there. */
    val stripePriceId: String? = null,
) {
    /** Whether this plan can be bought, as opposed to being the tier an account starts on. */
    val isPurchasable: Boolean
        get() = playProductId != null || stripePriceId != null
}

/**
 * Envelope for `GET /api/v1/plans`.
 *
 * An empty [data] list is a valid answer, not an error: it is what a self-hosted deployment with
 * billing switched off returns, and clients hide plan selection entirely in that case.
 */
@Serializable
data class PlanCatalogResponse(
    val success: Boolean,
    val data: List<PlanOption>,
)
