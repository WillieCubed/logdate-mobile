package app.logdate.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class RegisterDeviceRequest(
    val name: String,
    val platform: String,
    val appVersion: String,
)

@Serializable
data class RegisteredDevice(
    val id: String,
    val name: String,
    val platform: String,
    val appVersion: String,
    val createdAt: Long,
    val lastActive: Long,
)
