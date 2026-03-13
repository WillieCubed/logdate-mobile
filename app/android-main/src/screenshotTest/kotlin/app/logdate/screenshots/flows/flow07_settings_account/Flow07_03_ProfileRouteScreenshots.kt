package app.logdate.screenshots.flows.flow07_settings_account

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import app.logdate.feature.core.profile.ui.ProfileEditState
import app.logdate.feature.core.profile.ui.ProfileScreenContent
import app.logdate.feature.core.profile.ui.ProfileUiState
import app.logdate.feature.core.profile.ui.ProfileUpdateState
import app.logdate.shared.model.LogDateAccount
import app.logdate.shared.model.profile.LogDateProfile
import app.logdate.screenshots.common.ScreenshotPreviewMatrix
import app.logdate.screenshots.common.ScreenshotTheme
import com.android.tools.screenshot.PreviewTest

private val sampleProfile = LogDateProfile(displayName = "Alex Johnson")
private val sampleAccount =
    LogDateAccount(
        username = "alex_j",
        displayName = "Alex Johnson",
        passkeyCredentialIds = listOf("credential-1"),
    )

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S01_ProfileLoading() {
    val snackbarHostState = remember { SnackbarHostState() }
    ScreenshotTheme {
        ProfileScreenContent(
            uiState =
                ProfileUiState(
                    localProfile = sampleProfile,
                    account = sampleAccount,
                    userData = null,
                    isLoading = true,
                ),
            onBack = {},
            onStartEditingDisplayName = {},
            onCancelEditing = {},
            onSaveDisplayName = {},
            onNavigateToBirthday = {},
            snackbarHostState = snackbarHostState,
        )
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S02_ProfileDefault() {
    val snackbarHostState = remember { SnackbarHostState() }
    ScreenshotTheme {
        ProfileScreenContent(
            uiState =
                ProfileUiState(
                    localProfile = sampleProfile,
                    account = sampleAccount,
                    userData = null,
                ),
            onBack = {},
            onStartEditingDisplayName = {},
            onCancelEditing = {},
            onSaveDisplayName = {},
            onNavigateToBirthday = {},
            snackbarHostState = snackbarHostState,
        )
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S03_ProfileEditDisplayName() {
    val snackbarHostState = remember { SnackbarHostState() }
    ScreenshotTheme {
        ProfileScreenContent(
            uiState =
                ProfileUiState(
                    localProfile = sampleProfile,
                    account = sampleAccount,
                    userData = null,
                    editState = ProfileEditState.DisplayName("Alex Johnson"),
                ),
            onBack = {},
            onStartEditingDisplayName = {},
            onCancelEditing = {},
            onSaveDisplayName = {},
            onNavigateToBirthday = {},
            snackbarHostState = snackbarHostState,
        )
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S04_ProfileUpdating() {
    val snackbarHostState = remember { SnackbarHostState() }
    ScreenshotTheme {
        ProfileScreenContent(
            uiState =
                ProfileUiState(
                    localProfile = sampleProfile,
                    account = sampleAccount,
                    userData = null,
                    updateState = ProfileUpdateState.Updating,
                ),
            onBack = {},
            onStartEditingDisplayName = {},
            onCancelEditing = {},
            onSaveDisplayName = {},
            onNavigateToBirthday = {},
            snackbarHostState = snackbarHostState,
        )
    }
}

@PreviewTest
@ScreenshotPreviewMatrix
@Composable
fun S05_ProfileNoAccount() {
    val snackbarHostState = remember { SnackbarHostState() }
    ScreenshotTheme {
        ProfileScreenContent(
            uiState =
                ProfileUiState(
                    localProfile = LogDateProfile(displayName = "Local User"),
                    account = null,
                    userData = null,
                ),
            onBack = {},
            onStartEditingDisplayName = {},
            onCancelEditing = {},
            onSaveDisplayName = {},
            onNavigateToBirthday = {},
            snackbarHostState = snackbarHostState,
        )
    }
}
