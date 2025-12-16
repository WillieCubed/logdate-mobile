package app.logdate.client.device.models

import kotlinx.datetime.Instant
import kotlin.uuid.Uuid

/**
 * Represents a device in the LogDate ecosystem.
 */
data class DeviceInfo(
    /**
     * Unique identifier for this device.
     */
    val id: Uuid,

    /**
     * User-friendly name for this device.
     */
    val name: String,

    /**
     * The platform this device runs on.
     */
    val platform: DevicePlatform,

    /**
     * When this device was first registered.
     */
    val createdAt: Instant,

    /**
     * When this device was last active.
     */
    val lastActive: Instant,

    /**
     * The app version running on this device.
     */
    val appVersion: String,

    /**
     * Whether this is the current device.
     */
    val isCurrentDevice: Boolean = false
)

