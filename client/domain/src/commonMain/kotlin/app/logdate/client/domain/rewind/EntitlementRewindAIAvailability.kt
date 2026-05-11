package app.logdate.client.domain.rewind

import app.logdate.client.domain.account.GetCurrentEntitlementUseCase
import app.logdate.client.intelligence.availability.RewindAIAvailability
import app.logdate.client.intelligence.availability.RewindAITier
import app.logdate.shared.model.EntitlementResponse
import app.logdate.shared.model.EntitlementStatusWire
import app.logdate.shared.model.EntitlementTierWire

/**
 * [RewindAIAvailability] backed by the caller's server-side entitlement.
 *
 * Maps plan tier → AI tier with two override channels:
 *  - Status `SELF_HOST` always upgrades to [RewindAITier.FULL] (self-hosters get the
 *    full experience regardless of plan id).
 *  - Status `CANCELLED` always downgrades to [RewindAITier.NONE] (paid features off).
 *  - Per-feature flags in [EntitlementResponse.features] let the server roll AI to
 *    specific users without a client release. Flags are additive — `false` never
 *    downgrades a paid tier.
 *
 * Sign-in errors fail closed to [RewindAITier.NONE] — when in doubt, give the user a
 * quality local Rewind instead of risking a surprise paid call.
 */
class EntitlementRewindAIAvailability(
    private val getCurrentEntitlement: suspend () -> GetCurrentEntitlementUseCase.Result,
) : RewindAIAvailability {
    override suspend fun current(): RewindAITier {
        val entitlement =
            when (val result = getCurrentEntitlement()) {
                is GetCurrentEntitlementUseCase.Result.NotSignedIn -> return RewindAITier.NONE
                is GetCurrentEntitlementUseCase.Result.Error -> return RewindAITier.NONE
                is GetCurrentEntitlementUseCase.Result.Success -> result.entitlement
            }

        // Status overrides run before tier so a cancelled subscription on a paid plan still
        // produces NONE, and a self-host server (no billing wired up at all) gets FULL
        // regardless of what nominal tier the response carries.
        when (entitlement.status) {
            EntitlementStatusWire.CANCELLED -> return RewindAITier.NONE
            EntitlementStatusWire.SELF_HOST -> return RewindAITier.FULL
            else -> Unit
        }

        val tierDefault =
            when (entitlement.tier) {
                EntitlementTierWire.FREE -> RewindAITier.NONE
                EntitlementTierWire.STANDARD -> RewindAITier.QUOTES_ONLY
                EntitlementTierWire.PRO -> RewindAITier.FULL
                EntitlementTierWire.UNLIMITED -> RewindAITier.FULL
            }

        // Feature flags are additive: a `true` upgrades, a missing or `false` flag is a
        // no-op. narrative_full wins over quotes_only when both are set.
        val narrativeFull = entitlement.features[FEATURE_REWIND_NARRATIVE_FULL] == true
        val quotesOnly = entitlement.features[FEATURE_REWIND_QUOTES_ONLY] == true

        return when {
            narrativeFull -> RewindAITier.FULL
            quotesOnly -> maxOf(tierDefault, RewindAITier.QUOTES_ONLY)
            else -> tierDefault
        }
    }

    companion object {
        /** Server-side feature flag that upgrades the caller to [RewindAITier.FULL]. */
        const val FEATURE_REWIND_NARRATIVE_FULL = "rewind_narrative_full"

        /** Server-side feature flag that upgrades the caller to [RewindAITier.QUOTES_ONLY]. */
        const val FEATURE_REWIND_QUOTES_ONLY = "rewind_quotes_only"
    }
}
