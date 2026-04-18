package app.logdate.screenshots.flows.flow06_rewind

import androidx.compose.runtime.Composable
import app.logdate.feature.rewind.ui.settings.RewindSettingsContent
import app.logdate.screenshots.common.ScreenshotPreviewMatrix
import app.logdate.screenshots.common.ScreenshotTheme
import com.android.tools.screenshot.PreviewTest

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S01_RewindSettingsBothEnabled() {
    ScreenshotTheme {
        RewindSettingsContent(
            autoGenerationEnabled = true,
            notificationsEnabled = true,
            reflectionRepliesEnabled = true,
            onAutoGenerationToggled = {},
            onNotificationsToggled = {},
            onReflectionRepliesToggled = {},
            onBack = {},
        )
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S02_RewindSettingsAutoGenerationOff() {
    ScreenshotTheme {
        RewindSettingsContent(
            autoGenerationEnabled = false,
            notificationsEnabled = true,
            reflectionRepliesEnabled = true,
            onAutoGenerationToggled = {},
            onNotificationsToggled = {},
            onReflectionRepliesToggled = {},
            onBack = {},
        )
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S03_RewindSettingsNotificationsOff() {
    ScreenshotTheme {
        RewindSettingsContent(
            autoGenerationEnabled = true,
            notificationsEnabled = false,
            reflectionRepliesEnabled = true,
            onAutoGenerationToggled = {},
            onNotificationsToggled = {},
            onReflectionRepliesToggled = {},
            onBack = {},
        )
    }
}
