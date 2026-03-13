package app.logdate.client.domain.identity

import kotlin.time.Instant

/**
 * Unified, read-only snapshot of the current user's identity, resolved from
 * local preferences, cloud account, and session state with local-first semantics.
 */
data class ResolvedUserIdentity(
    /** Local display name → cloud display name → empty string. */
    val displayName: String,
    /** Cloud-only username, null without an account. */
    val username: String?,
    /** Local profile photo URI, null if unset. */
    val profilePhotoUri: String?,
    /** Local bio → cloud bio, null if unset. */
    val bio: String?,
    /** Local birthday, null if unset or sentinel. */
    val birthday: Instant?,
    /** Date the user completed onboarding, null if unset or sentinel. */
    val onboardedDate: Instant?,
    /** Whether the user has a valid authenticated session. */
    val isAuthenticated: Boolean,
    /** Cloud account ID, null without an account. */
    val cloudAccountId: String?,
)
