package app.logdate.feature.core.settings.ui

import androidx.compose.runtime.staticCompositionLocalOf

data class SettingsLayoutInfo(
    val isInTwoPaneMode: Boolean,
    val selectedDetail: String?,
    val isDetailPane: Boolean,
)

val LocalSettingsLayoutInfo =
    staticCompositionLocalOf {
        SettingsLayoutInfo(
            isInTwoPaneMode = false,
            selectedDetail = null,
            isDetailPane = false,
        )
    }
