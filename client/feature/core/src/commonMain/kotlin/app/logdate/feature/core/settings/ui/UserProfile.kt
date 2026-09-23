package app.logdate.feature.core.settings.ui

import kotlin.time.Instant

/** The person's name and handle as settings screens show them. */
data class UserProfile(
    val name: String,
    /** The handle without the leading `@`. */
    val username: String,
    val isEditable: Boolean = true,
    val isAuthenticated: Boolean = false,
    val email: String? = null,
    val emailVerified: Boolean = false,
    val emailVerifiedAt: Instant? = null,
)
