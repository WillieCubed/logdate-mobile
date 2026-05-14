package app.logdate.client.domain.account

import app.logdate.client.datastore.SessionStorage
import app.logdate.client.permissions.EmailVerificationManager
import app.logdate.client.permissions.EmailVerificationOutcome

/**
 * Drives the full Android Digital Credentials email-verification flow on behalf
 * of the signed-in user.
 *
 * Fetches the current session's access token, hands it to
 * [EmailVerificationManager.verifyEmail], and returns the outcome verbatim. The
 * use case stays minimal so the manager and its outcome variants remain the
 * single source of truth — UI layers consume [EmailVerificationOutcome] directly.
 *
 * Returns [EmailVerificationOutcome.Failed] with reason `not_signed_in` when no
 * session is present; callers should never see this path because both entry
 * points (onboarding + settings) only render after sign-in.
 */
open class VerifyEmailUseCase(
    private val sessionStorage: SessionStorage,
    private val manager: EmailVerificationManager,
) {
    open suspend operator fun invoke(): EmailVerificationOutcome {
        val session = sessionStorage.getSession() ?: return EmailVerificationOutcome.Failed("not_signed_in")
        return manager.verifyEmail(session.accessToken)
    }
}
