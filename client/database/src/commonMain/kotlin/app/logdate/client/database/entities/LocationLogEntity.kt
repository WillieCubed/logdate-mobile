package app.logdate.client.database.entities

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import kotlinx.datetime.Instant

/**
 * A log of a user's location at a given time using a given device.
 *
 * Logs of this type are used as ground truth for other app functionality, like timelines and
 * location history.
 *
 * Note that the entries in this data are only expected to be as accurate as the device that created
 * them. For example, if a user's device is rooted or jailbroken, the data in this log may be
 * inaccurate or falsified.
 *
 * This data should not be used as a source of truth for a user's location but can be used to
 * approximate a user's location at a given time.
 */
@Entity(
    tableName = "location_logs",
    primaryKeys = ["user_id", "device_id", "timestamp"]
)
data class LocationLogEntity(
    /**
     * The user that created this log.
     */
    @ColumnInfo(name = "user_id")
    val userId: String,
    /**
     * The device that created this log.
     *
     * @see [UserDeviceEntity]
     */
    @ColumnInfo(name = "device_id")
    val deviceId: String,
    /**
     * When this log was created.
     */
    val timestamp: Instant,
    /**
     * The location of the user when this log was created.
     */
    @Embedded
    val location: Coordinates,
    /**
     * How likely this location is to be accurate based on the device's sensors.
     */
    val confidence: Float,
    /**
     * Whether this log was produced on a device that is known to be genuine.
     *
     * For Android devices, this means that the device is not rooted or jailbroken and that the app
     * binary has not been tampered with, as determined by the Play Integrity API.
     */
    @ColumnInfo(name = "is_genuine")
    val isGenuine: Boolean,
)

/**
 * A set of coordinates that represent a location on Earth.
 */
data class Coordinates(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
)
