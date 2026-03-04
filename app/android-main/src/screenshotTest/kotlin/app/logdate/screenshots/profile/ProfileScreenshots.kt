package app.logdate.screenshots.profile

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import app.logdate.feature.core.profile.ui.ProfileEditState
import app.logdate.feature.core.profile.ui.ProfileScreenContent
import app.logdate.feature.core.profile.ui.ProfileUiState
import app.logdate.feature.core.profile.ui.ProfileUpdateState
import app.logdate.shared.model.profile.LogDateProfile
import app.logdate.shared.model.LogDateAccount
import app.logdate.screenshots.common.ScreenshotTestData.PHONE
import app.logdate.screenshots.common.ScreenshotTheme
import com.android.tools.screenshot.PreviewTest

private val sampleProfile = LogDateProfile(displayName = "Alex Johnson")
private val sampleAccount = LogDateAccount(username = "alex_j", displayName = "Alex Johnson")

// ─── Profile Screen States ──────────────────────────────────────────────────────

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun Profile_Default() {
    val snackbarHostState = remember { SnackbarHostState() }
    ScreenshotTheme {
        ProfileScreenContent(
            uiState = ProfileUiState(
                localProfile = sampleProfile,
                account = sampleAccount,
                userData = null,
            ),
            onBack = {},
            onStartEditingDisplayName = {},
            onCancelEditing = {},
            onSaveDisplayName = {},
            onRefresh = {},
            snackbarHostState = snackbarHostState,
        )
    }
}

@PreviewTest
@Preview(showBackground = true, device = PHONE, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
fun Profile_Default_Dark() {
    val snackbarHostState = remember { SnackbarHostState() }
    ScreenshotTheme(darkTheme = true) {
        ProfileScreenContent(
            uiState = ProfileUiState(
                localProfile = sampleProfile,
                account = sampleAccount,
                userData = null,
            ),
            onBack = {},
            onStartEditingDisplayName = {},
            onCancelEditing = {},
            onSaveDisplayName = {},
            onRefresh = {},
            snackbarHostState = snackbarHostState,
        )
    }
}

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun Profile_EditDisplayName() {
    val snackbarHostState = remember { SnackbarHostState() }
    ScreenshotTheme {
        ProfileScreenContent(
            uiState = ProfileUiState(
                localProfile = sampleProfile,
                account = sampleAccount,
                userData = null,
                editState = ProfileEditState.DisplayName("Alex Johnson"),
            ),
            onBack = {},
            onStartEditingDisplayName = {},
            onCancelEditing = {},
            onSaveDisplayName = {},
            onRefresh = {},
            snackbarHostState = snackbarHostState,
        )
    }
}

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun Profile_Updating() {
    val snackbarHostState = remember { SnackbarHostState() }
    ScreenshotTheme {
        ProfileScreenContent(
            uiState = ProfileUiState(
                localProfile = sampleProfile,
                account = sampleAccount,
                userData = null,
                updateState = ProfileUpdateState.Updating,
            ),
            onBack = {},
            onStartEditingDisplayName = {},
            onCancelEditing = {},
            onSaveDisplayName = {},
            onRefresh = {},
            snackbarHostState = snackbarHostState,
        )
    }
}

@PreviewTest
@Preview(showBackground = true, device = PHONE)
@Composable
fun Profile_NoAccount() {
    val snackbarHostState = remember { SnackbarHostState() }
    ScreenshotTheme {
        ProfileScreenContent(
            uiState = ProfileUiState(
                localProfile = LogDateProfile(displayName = "Local User"),
                account = null,
                userData = null,
            ),
            onBack = {},
            onStartEditingDisplayName = {},
            onCancelEditing = {},
            onSaveDisplayName = {},
            onRefresh = {},
            snackbarHostState = snackbarHostState,
        )
    }
}
