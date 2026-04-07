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
data object AdvancedSettingsRoute

@Serializable
data object MemoriesSettingsRoute

@Serializable
data object VoiceNotesSettingsRoute

@Serializable
data object StreakSettingsRoute

@Serializable
data object TimelineSettingsRoute

@Serializable
data object SyncSettingsRoute

@Serializable
data object ExportSettingsRoute

@Serializable
data object LibrarySettingsRoute

@Serializable
data object ResetSettingsRoute

@Serializable
data object BirthdaySettingsRoute

@Serializable
data object DayBoundarySettingsRoute

@Serializable
data object RecommendationSettingsRoute

@Serializable
data object ClearDataSettingsRoute

@Serializable
data object ResetAppSettingsRoute
