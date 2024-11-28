package app.logdate.shared.model

import kotlinx.datetime.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid


sealed interface ActivityUpdate {
    data class NoteUpdate(
        val noteId: String,
        val content: String,
    ) : ActivityUpdate

    data class LocationUpdate(
        val location: Place,
    ) : ActivityUpdate
}

/**
 * A single activity in a timeline.
 *
 * Examples:
 * - User shares a photo
 */
@OptIn(ExperimentalUuidApi::class)
data class ActivityTimelineItem(
    val uid: Uuid,
    val timestamp: Instant,
    val location: Location,
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