package app.logdate.shared.model.profile

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * LogDate Profile - Local profile data that exists independently of cloud accounts.
 * 
 * This represents the user's local profile information that's always available
 * for app personalization, regardless of whether they have a LogDate Cloud account.
 * When a cloud account is connected, this data syncs with the cloud account.
 */
@Serializable
data class LogDateProfile(
    /**
     * The user's display name - always editable locally.
     */
    val displayName: String = "",
    
    /**
     * The user's birthday - optional personal information.
     */
    val birthday: Instant? = null,
    
    /**
     * URI to the user's profile photo stored locally.
     */
    val profilePhotoUri: String? = null,
    
    /**
     * The user's bio - a short description about themselves.
     * Can be original user input or LLM-paraphrased version.
     */
    val bio: String? = null, 
    
    /**
     * The original bio text as entered by the user, before LLM processing.
     */
    val originalBio: String? = null,
    
    /**
     * When this profile was created locally.
     */
    val createdAt: Instant = Instant.DISTANT_PAST,
    
    /**
     * When this profile was last updated locally.
     */
    val lastUpdatedAt: Instant = Instant.DISTANT_PAST
)