package app.logdate.feature.core.settings.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import app.logdate.client.device.restore.NativeBackupQuotaFailure
import app.logdate.client.device.restore.NativeBackupStatus
import app.logdate.ui.theme.LogDateTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class NativeBackupSettingsTest {
    @Test
    fun backupHasANamedSettingsEntryWithoutAnAccount() =
        runDesktopComposeUiTest(width = 411, height = 900) {
            var visits = 0
            setContent {
                LogDateTheme {
                    SettingsDataSection(
                        userProfile = UserProfile("Fixture", "fixture"),
                        onNavigateToSync = { visits++ },
                        onNavigateToExport = {},
                        onNavigateToReset = {},
                        onNavigateToCloudAccountCreation = {},
                        onNavigateToSignIn = {},
                    )
                }
            }
            onNodeWithText("Backup").performClick()
            assertEquals(1, visits)
        }

    @Test
    fun largeTextKeepsTheAccountFreeExportReachable() =
        runDesktopComposeUiTest(width = 411, height = 900) {
            var exports = 0
            setContent {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                    LogDateTheme {
                        SyncSettingsContent(
                            onBack = {},
                            syncStatus = null,
                            isAuthenticated = false,
                            onSyncNow = {},
                            onNavigateToSignIn = {},
                            quotaUsage = StorageQuotaUi(100, 0, 0f, formattedTotal = "100 B", formattedUsed = "0 B"),
                            isQuotaAvailable = false,
                            snackbarHostState = SnackbarHostState(),
                            nativeBackupStatus = NativeBackupStatus.Available(NativeBackupQuotaFailure(30_000_000, 25_000_000)),
                            onExportArchive = { exports++ },
                        )
                    }
                }
            }
            onNodeWithText("Export archive").performScrollTo().performClick()
            assertEquals(1, exports)
        }

    @Test
    fun quotaFailureOffersArchiveExportWithoutAnAccount() =
        runDesktopComposeUiTest(width = 411, height = 900) {
            var exports = 0
            setContent {
                LogDateTheme {
                    NativeBackupSettingsSection(
                        NativeBackupStatus.Available(NativeBackupQuotaFailure(30_000_000, 25_000_000)),
                        onExportArchive = { exports++ },
                    )
                }
            }
            onNodeWithText("Android backup").assertExists()
            onNodeWithText("Android reported a backup over its storage limit. That attempt did not save a new backup.").assertExists()
            onNodeWithText("Export archive").performClick()
            assertEquals(1, exports)
            onNodeWithText("Create account").assertDoesNotExist()
            onNodeWithText("Backed up").assertDoesNotExist()
        }

    @Test
    fun otherPlatformsDoNotShowAndroidBackup() =
        runDesktopComposeUiTest {
            setContent { NativeBackupSettingsSection(NativeBackupStatus.Unsupported, {}) }
            onNodeWithText("Android backup").assertDoesNotExist()
        }
}
