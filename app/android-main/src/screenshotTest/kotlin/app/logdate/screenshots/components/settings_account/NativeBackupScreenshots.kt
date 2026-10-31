package app.logdate.screenshots.components.settings_account

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.logdate.client.device.restore.NativeBackupQuotaFailure
import app.logdate.client.device.restore.NativeBackupStatus
import app.logdate.feature.core.settings.ui.NativeBackupSettingsSection
import app.logdate.screenshots.common.OsIntegrationPreviewMatrix
import app.logdate.screenshots.common.ScreenshotTheme
import app.logdate.screenshots.shared.SharedScreenshotScene
import app.logdate.screenshots.shared.SharedScreenshotSceneId
import app.logdate.ui.common.DefaultSettingsContentContainer
import app.logdate.ui.theme.Spacing
import com.android.tools.screenshot.PreviewTest

@PreviewTest
@OsIntegrationPreviewMatrix
@Composable
fun NativeBackupSignedOut() {
    ScreenshotTheme { SharedScreenshotScene(SharedScreenshotSceneId.NativeBackupSignedOut) }
}

@PreviewTest
@OsIntegrationPreviewMatrix
@Composable
fun NativeBackupReady() {
    SettingsSectionScreenshot {
        NativeBackupSettingsSection(NativeBackupStatus.Available())
    }
}

@PreviewTest
@OsIntegrationPreviewMatrix
@Composable
fun NativeBackupQuota() {
    SettingsSectionScreenshot {
        NativeBackupSettingsSection(
            NativeBackupStatus.Available(NativeBackupQuotaFailure(30_000_000, 25_000_000)),
        )
    }
}

/** Keep named component states visible; shared scenes continue to cover the full settings page. */
@Composable
internal fun SettingsSectionScreenshot(content: @Composable () -> Unit) {
    ScreenshotTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            DefaultSettingsContentContainer {
                Column(
                    Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(Spacing.lg),
                ) {
                    content()
                }
            }
        }
    }
}
