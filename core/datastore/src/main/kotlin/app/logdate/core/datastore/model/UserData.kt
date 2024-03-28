package app.logdate.core.datastore.model

import java.time.Instant

/**
 * User metadata.
 */
data class UserData(
    val isOnboarded: Boolean,
    val onboardedDate: Instant,
    val favoriteNotes: List<String>,
)
