package app.logdate.model

import kotlinx.datetime.Instant

/**
 * A single activity in a timeline.
 *
 * Examples:
 * - User shares a photo
 */
data class ActivityTimelineItem(
    val uid: String,
    val date: Instant,
    val location: UserPlace,
)

/**
 *
 */
data class ActivityActor(
    /**
     * The primary identifier for this actor.
     *
     * This ID is only guaranteed to be unique on this device and must not be used as a universal
     * identifier.
     */
    val id: String,
    /**
     * For ActivityPub-compatible clients,
     */
    val remoteId: String,
    val name: String,
)