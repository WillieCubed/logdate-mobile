package app.logdate.client.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.datetime.Instant
import kotlin.uuid.Uuid

/**
 * ActivityPub-aligned Place entity for storing semantic locations.
 *
 * This entity supports the ActivityPub Place type with fields for:
 * - Coordinates (latitude, longitude, altitude)
 * - Accuracy and radius for geographic precision
 * - Units for measurement (ActivityPub: m, km, miles, feet)
 * - AP URI for federation
 *
 * Notes can reference places via foreign key for semantic meaning (e.g., "Home", "Work")
 * while also storing raw coordinates for capture fidelity.
 */
@Entity(
    tableName = "places",
    indices = [
        Index("latitude", "longitude"),
        Index("ap_uri")
    ]
)
data class PlaceEntity(
    @PrimaryKey
    val id: Uuid,

    val name: String,

    val latitude: Double,

    val longitude: Double,

    val altitude: Double? = null,

    /**
     * Accuracy as a percentage (0-100) per ActivityPub specification.
     */
    val accuracy: Float? = null,

    /**
     * Radius in the specified units (default: meters).
     * Indicates the area around the coordinates that the place encompasses.
     */
    val radius: Double = 100.0,

    /**
     * Units for radius measurement. ActivityPub supports: m, km, miles, feet.
     */
    val units: String = "m",

    val description: String? = null,

    /**
     * ActivityPub URI for federation. Allows cross-instance place references.
     */
    @ColumnInfo(name = "ap_uri")
    val apUri: String? = null,

    val created: Instant,

    @ColumnInfo(name = "last_updated")
    val lastUpdated: Instant,

    @ColumnInfo(name = "sync_version")
    val syncVersion: Long = 0,

    @ColumnInfo(name = "last_synced")
    val lastSynced: Instant? = null,

    @ColumnInfo(name = "deleted_at")
    val deletedAt: Instant? = null,
)
