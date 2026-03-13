package app.logdate.feature.core.settings.navigation

import kotlinx.serialization.Serializable

@Serializable
data class SettingsRoute(
    val settingId: String? = null,
    val selectedDetail: String? = null,
)

@Serializable
data class DevicesRoute(
    val id: String = "devices",
)

@Serializable
data object AccountSettingsRoute

@Serializable
data object PrivacySettingsRoute

@Serializable
data object DataSettingsRoute

@Serializable
data object LocationSettingsRoute

@Serializable
data object DangerZoneSettingsRoute

@Serializable
data object AdvancedSettingsRoute
