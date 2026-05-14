package app.logdate.client.permissions

import app.logdate.client.networking.EmailVerificationCompletion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Instant

/**
 * Pins the wire -> UI mapping that drives every email-verification entry point
 * (onboarding sub-step + Settings row). Keeping this exhaustive over
 * [EmailVerificationCompletion] catches a future variant that forgets to map
 * — Kotlin's `when` on a sealed type only guards exhaustiveness at the
 * mapping site, not at the test boundary.
 */
class EmailVerificationOutcomeMappingTest {
    private val verifiedAt = Instant.fromEpochSeconds(1_775_083_422)

    @Test
    fun `success completion carries email and verifiedAt onto the outcome`() {
        val outcome =
            mapCompletionToOutcome(
                EmailVerificationCompletion.Success(email = "u@example.com", verifiedAt = verifiedAt),
            )

        val success = assertIs<EmailVerificationOutcome.Success>(outcome)
        assertEquals("u@example.com", success.email)
        assertEquals(verifiedAt, success.verifiedAt)
    }

    @Test
    fun `conflict completion carries the human message onto the outcome`() {
        val outcome =
            mapCompletionToOutcome(
                EmailVerificationCompletion.Conflict(message = "This email is already attached to another LogDate account."),
            )

        val conflict = assertIs<EmailVerificationOutcome.Conflict>(outcome)
        assertEquals("This email is already attached to another LogDate account.", conflict.message)
    }

    @Test
    fun `failed completion carries the stable reason code onto the outcome`() {
        val outcome =
            mapCompletionToOutcome(
                EmailVerificationCompletion.Failed(reason = "issuer_signature_invalid"),
            )

        val failed = assertIs<EmailVerificationOutcome.Failed>(outcome)
        assertEquals("issuer_signature_invalid", failed.reason)
    }
}
