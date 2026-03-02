package app.logdate.client.database.entities

import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Base class for all note entities.
 *
 * All notes support location capture with a hybrid approach:
 * - Embedded coordinates (latitude, longitude, altitude) for capture fidelity
 * - Optional Place reference for semantic meaning (e.g., "Home", "Work")
 *
 * This design preserves the exact GPS coordinates at note creation while
 * allowing places to be associated later for display purposes.
 */
abstract class GenericNoteData {
    abstract val uid: Uuid
    abstract val lastUpdated: Instant
    abstract val created: Instant

    abstract val syncVersion: Long
    abstract val lastSynced: Instant?
    abstract val deletedAt: Instant?

    // Location fields - nullable to support notes without location
    abstract val latitude: Double?
    abstract val longitude: Double?
    abstract val altitude: Double?
    abstract val locationAccuracy: Float?
    abstract val placeId: Uuid?
}
