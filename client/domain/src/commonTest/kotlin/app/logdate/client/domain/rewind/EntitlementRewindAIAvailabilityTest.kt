package app.logdate.client.domain.rewind

import app.logdate.client.domain.account.GetCurrentEntitlementUseCase
import app.logdate.client.intelligence.availability.RewindAITier
import app.logdate.shared.model.EntitlementResponse
import app.logdate.shared.model.EntitlementStatusWire
import app.logdate.shared.model.EntitlementTierWire
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Maps server-side entitlement state to a [RewindAITier] for the Rewind generator's
 * pre-flight strategy selection.
 */
class EntitlementRewindAIAvailabilityTest {
    @Test
    fun `not signed in returns NONE`() =
        runTest {
            val availability = EntitlementRewindAIAvailability { GetCurrentEntitlementUseCase.Result.NotSignedIn }

            assertEquals(RewindAITier.NONE, availability.current())
        }

    @Test
    fun `entitlement fetch error fails closed to NONE`() =
        runTest {
            val availability = EntitlementRewindAIAvailability { GetCurrentEntitlementUseCase.Result.Error("timeout") }

            assertEquals(RewindAITier.NONE, availability.current())
        }

    @Test
    fun `FREE tier returns NONE`() =
        runTest {
            val availability =
                EntitlementRewindAIAvailability {
                    GetCurrentEntitlementUseCase.Result.Success(
                        entitlement(tier = EntitlementTierWire.FREE, status = EntitlementStatusWire.ACTIVE),
                    )
                }

            assertEquals(RewindAITier.NONE, availability.current())
        }

    @Test
    fun `STANDARD tier returns QUOTES_ONLY`() =
        runTest {
            val availability =
                EntitlementRewindAIAvailability {
                    GetCurrentEntitlementUseCase.Result.Success(
                        entitlement(tier = EntitlementTierWire.STANDARD, status = EntitlementStatusWire.ACTIVE),
                    )
                }

            assertEquals(RewindAITier.QUOTES_ONLY, availability.current())
        }

    @Test
    fun `PRO tier returns FULL`() =
        runTest {
            val availability =
                EntitlementRewindAIAvailability {
                    GetCurrentEntitlementUseCase.Result.Success(
                        entitlement(tier = EntitlementTierWire.PRO, status = EntitlementStatusWire.ACTIVE),
                    )
                }

            assertEquals(RewindAITier.FULL, availability.current())
        }

    @Test
    fun `UNLIMITED tier returns FULL`() =
        runTest {
            val availability =
                EntitlementRewindAIAvailability {
                    GetCurrentEntitlementUseCase.Result.Success(
                        entitlement(tier = EntitlementTierWire.UNLIMITED, status = EntitlementStatusWire.ACTIVE),
                    )
                }

            assertEquals(RewindAITier.FULL, availability.current())
        }

    @Test
    fun `SELF_HOST status returns FULL regardless of tier`() =
        runTest {
            val availability =
                EntitlementRewindAIAvailability {
                    GetCurrentEntitlementUseCase.Result.Success(
                        entitlement(tier = EntitlementTierWire.FREE, status = EntitlementStatusWire.SELF_HOST),
                    )
                }

            assertEquals(RewindAITier.FULL, availability.current())
        }

    @Test
    fun `CANCELLED status returns NONE even on a paid tier`() =
        runTest {
            val availability =
                EntitlementRewindAIAvailability {
                    GetCurrentEntitlementUseCase.Result.Success(
                        entitlement(tier = EntitlementTierWire.PRO, status = EntitlementStatusWire.CANCELLED),
                    )
                }

            assertEquals(RewindAITier.NONE, availability.current())
        }

    @Test
    fun `PAST_DUE status preserves the tier mapping`() =
        runTest {
            val availability =
                EntitlementRewindAIAvailability {
                    GetCurrentEntitlementUseCase.Result.Success(
                        entitlement(tier = EntitlementTierWire.PRO, status = EntitlementStatusWire.PAST_DUE),
                    )
                }

            assertEquals(RewindAITier.FULL, availability.current())
        }

    @Test
    fun `rewind_narrative_full feature flag upgrades a FREE user to FULL`() =
        runTest {
            val availability =
                EntitlementRewindAIAvailability {
                    GetCurrentEntitlementUseCase.Result.Success(
                        entitlement(
                            tier = EntitlementTierWire.FREE,
                            status = EntitlementStatusWire.ACTIVE,
                            features = mapOf("rewind_narrative_full" to true),
                        ),
                    )
                }

            assertEquals(RewindAITier.FULL, availability.current())
        }

    @Test
    fun `rewind_quotes_only feature flag upgrades a FREE user to QUOTES_ONLY`() =
        runTest {
            val availability =
                EntitlementRewindAIAvailability {
                    GetCurrentEntitlementUseCase.Result.Success(
                        entitlement(
                            tier = EntitlementTierWire.FREE,
                            status = EntitlementStatusWire.ACTIVE,
                            features = mapOf("rewind_quotes_only" to true),
                        ),
                    )
                }

            assertEquals(RewindAITier.QUOTES_ONLY, availability.current())
        }

    @Test
    fun `rewind_narrative_full=false does not downgrade a PRO user`() =
        runTest {
            val availability =
                EntitlementRewindAIAvailability {
                    GetCurrentEntitlementUseCase.Result.Success(
                        entitlement(
                            tier = EntitlementTierWire.PRO,
                            status = EntitlementStatusWire.ACTIVE,
                            features = mapOf("rewind_narrative_full" to false),
                        ),
                    )
                }

            assertEquals(RewindAITier.FULL, availability.current())
        }

    @Test
    fun `narrative_full flag wins over quotes_only when both are set`() =
        runTest {
            val availability =
                EntitlementRewindAIAvailability {
                    GetCurrentEntitlementUseCase.Result.Success(
                        entitlement(
                            tier = EntitlementTierWire.FREE,
                            status = EntitlementStatusWire.ACTIVE,
                            features =
                                mapOf(
                                    "rewind_narrative_full" to true,
                                    "rewind_quotes_only" to true,
                                ),
                        ),
                    )
                }

            assertEquals(RewindAITier.FULL, availability.current())
        }

    @Test
    fun `CANCELLED beats feature flags`() =
        runTest {
            val availability =
                EntitlementRewindAIAvailability {
                    GetCurrentEntitlementUseCase.Result.Success(
                        entitlement(
                            tier = EntitlementTierWire.PRO,
                            status = EntitlementStatusWire.CANCELLED,
                            features = mapOf("rewind_narrative_full" to true),
                        ),
                    )
                }

            assertEquals(RewindAITier.NONE, availability.current())
        }

    private fun entitlement(
        tier: EntitlementTierWire,
        status: EntitlementStatusWire,
        features: Map<String, Boolean> = emptyMap(),
    ): EntitlementResponse =
        EntitlementResponse(
            planId = "test-plan",
            tier = tier,
            status = status,
            storageBytesLimit = null,
            backupCountLimit = null,
            features = features,
        )
}
