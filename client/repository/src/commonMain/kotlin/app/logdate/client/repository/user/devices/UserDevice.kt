package app.logdate.client.repository.user.devices

import kotlinx.datetime.Instant

/**
 * A user's device.
 *
 * This data is used to track the devices that a user has used to access the app and allow a
 * user to manage their devices.
 */
data class UserDevice(
    /**
     * The primary app instance identifier for this device.
     *
     * Note that this device ID is not strictly a hardware identifier is is not guaranteed to be
     * unique for a given device.
     *
     * If a user logs in on a new device, a new device ID will be generated. Likewise, if a user
     * signs out of a device and signs back in, a new device ID may be generated. Two users each
     * using a separate operating system profile on the same device may have different device IDs.
     * If the operating system updates, the device ID is not expected to change.
     *
     * Implementers are expected to use additional logic to determine if two device entities
     * refer to the same physical device for purposes of managing authentication sessions or other
     * tasks, like reconciling location data.
     */
    val uid: String,
    /**
     * The user that this device is associated with.
     */
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
    val type: DeviceType,
    /**
     * When this device was associated with a user.
     */
    val added: Instant,
)

