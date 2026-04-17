package app.logdate.server.billing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BillingProviderTest {
    @Test
    fun `unset BILLING_PROVIDER defaults to disabled`() {
        assertEquals(BillingProvider.Disabled, BillingProvider.fromEnvironment { null })
    }

    @Test
    fun `disabled provider reports both flags as false`() {
        val disabled = BillingProvider.Disabled
        assertFalse(disabled.stripeEnabled)
        assertFalse(disabled.playEnabled)
        assertFalse(disabled.isEnabled)
    }

    @Test
    fun `stripe provider enables only stripe`() {
        val stripe = BillingProvider.fromEnvironment { if (it == "BILLING_PROVIDER") "stripe" else null }
        assertEquals(BillingProvider.Stripe, stripe)
        assertTrue(stripe.stripeEnabled)
        assertFalse(stripe.playEnabled)
        assertTrue(stripe.isEnabled)
    }

    @Test
    fun `play provider enables only play`() {
        val play = BillingProvider.fromEnvironment { if (it == "BILLING_PROVIDER") "play" else null }
        assertEquals(BillingProvider.Play, play)
        assertFalse(play.stripeEnabled)
        assertTrue(play.playEnabled)
    }

    @Test
    fun `both provider enables both stripe and play`() {
        val both = BillingProvider.fromEnvironment { if (it == "BILLING_PROVIDER") "both" else null }
        assertEquals(BillingProvider.Both, both)
        assertTrue(both.stripeEnabled)
        assertTrue(both.playEnabled)
    }

    @Test
    fun `unknown value falls back to disabled`() {
        val unknown = BillingProvider.fromEnvironment { if (it == "BILLING_PROVIDER") "bogus" else null }
        assertEquals(BillingProvider.Disabled, unknown)
    }

    @Test
    fun `case-insensitive parsing`() {
        val upper = BillingProvider.fromEnvironment { "STRIPE" }
        assertEquals(BillingProvider.Stripe, upper)
    }
}
