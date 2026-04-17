package app.logdate.server.billing

/**
 * Which billing backend(s) are active for this deployment.
 *
 * The default is [Disabled] — self-hosters don't need to configure anything to run the server.
 * In that mode the billing routes are never registered and the entitlement service hands out
 * unlimited access.
 *
 * Selection is via the `BILLING_PROVIDER` environment variable (`disabled` | `stripe` | `play` |
 * `both`). Invalid values log a warning and fall back to [Disabled] so a typo can't accidentally
 * break checkout without also breaking sync.
 */
sealed class BillingProvider {
    abstract val stripeEnabled: Boolean
    abstract val playEnabled: Boolean

    data object Disabled : BillingProvider() {
        override val stripeEnabled: Boolean = false
        override val playEnabled: Boolean = false
    }

    data object Stripe : BillingProvider() {
        override val stripeEnabled: Boolean = true
        override val playEnabled: Boolean = false
    }

    data object Play : BillingProvider() {
        override val stripeEnabled: Boolean = false
        override val playEnabled: Boolean = true
    }

    data object Both : BillingProvider() {
        override val stripeEnabled: Boolean = true
        override val playEnabled: Boolean = true
    }

    val isEnabled: Boolean get() = stripeEnabled || playEnabled

    companion object {
        const val ENV_VAR: String = "BILLING_PROVIDER"

        fun fromEnvironment(readEnv: (String) -> String? = System::getenv): BillingProvider =
            when (readEnv(ENV_VAR)?.trim()?.lowercase()) {
                "stripe" -> Stripe
                "play" -> Play
                "both" -> Both
                null, "", "disabled", "off" -> Disabled
                else -> Disabled
            }
    }
}
