package app.logdate.client.device.restore

import kotlinx.serialization.Serializable

@Serializable
data class NativePrivacyRepairIdentity(
    val generation: String,
    val accountDeliveryId: String,
    val contentState: NativePrivacyRepairContentState,
    val contentDeliveryId: String?,
    val retainedPreferences: Boolean,
    val privacyReceived: Boolean,
    val deviceInitialized: Boolean,
    val accountState: NativePrivacyRepairAccountState,
)

@Serializable
enum class NativePrivacyRepairContentState {
    PRESENT,
    MISSING,
    COMPLETE,
    ACKNOWLEDGED,
}

@Serializable
enum class NativePrivacyRepairAccountState {
    PENDING,
    ACKNOWLEDGEMENT_WRITTEN,
    RESOLVED,
}
