package app.logdate.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.datetime.Instant

/**
 * A user's device.
 *
 * This data is used to track the devices that a user has used to access the app and allow a
 * user to manage their devices.
 */
@Entity(
    tableName = "user_devices",
)
data class UserDeviceEntity(
    /**
     * The primary identifier for this device.
     */
    @PrimaryKey
    val uid: String,
    /**
     * The UID of the user that owns this device.
     */
    @ColumnInfo(name = "user_id")
    val userId: String,
    /**
     * A user-friendly label for this device.
     */
    val label: String,
    /**
     * The operating system of this device.
     *
     * Examples:
     * - Android
     * - iOS
     * - Windows
     * - macOS
     * - Linux
     */
    @ColumnInfo(name = "operating_system")
    val operatingSystem: String,
    /**
     * The version of the operating system on this device.
     *
     * Examples:
     * - Android 15
     * - iOS 18
     * - Windows 11
     */
    val version: String,
    /**
     * Examples:
     * - Pixel 4
     * - iPhone 18
     */
    val model: String,
    /**
     * The semantic type of this device.
     *
     * This may be used to provide additional context about the device for display purposes.
     */
    val type: String,
    /**
     * When this device was associated with a user.
     */
    val added: Instant,
)
