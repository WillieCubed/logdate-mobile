package app.logdate.screenshots.audit.adaptive

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.logdate.client.permissions.ContactsPermissionState
import app.logdate.feature.core.people.ui.PeopleSettingsContent
import app.logdate.feature.core.people.ui.PeopleSettingsViewModel
import app.logdate.screenshots.common.ScreenshotTheme
import app.logdate.ui.foldable.FoldableHingeBounds
import app.logdate.ui.foldable.FoldableHingeInfo
import app.logdate.ui.foldable.FoldableHingeOrientation
import app.logdate.ui.foldable.FoldableHingeState
import app.logdate.ui.foldable.FoldableLayoutInfo
import app.logdate.ui.foldable.FoldableOcclusionType
import app.logdate.ui.foldable.FoldablePosture
import app.logdate.ui.foldable.provideFoldableLayoutInfo
import com.android.tools.screenshot.PreviewTest

private const val BOOK_FOLDABLE = "spec:width=1440dp,height=900dp"

private val bookPostureLayoutInfo =
    FoldableLayoutInfo(
        isFoldable = true,
        posture = FoldablePosture.Book,
        hinge =
            FoldableHingeInfo(
                orientation = FoldableHingeOrientation.Vertical,
                state = FoldableHingeState.HalfOpened,
                occlusionType = FoldableOcclusionType.Full,
                bounds =
                    FoldableHingeBounds(
                        left = 708.dp,
                        top = 0.dp,
                        right = 732.dp,
                        bottom = 900.dp,
                        width = 24.dp,
                        height = 900.dp,
                    ),
                isSeparating = true,
            ),
    )

/**
 * Stable fake state representing an enabled People feature on a quiet, ordinary
 * setup: a small handful of imported contacts and one cluster awaiting review.
 */
private val enabledPeopleState =
    PeopleSettingsViewModel.UiState(
        isPeopleEnabled = true,
        supportsSelectedContactsPicker = true,
        totalPeopleCount = 12,
        pendingReviewCount = 1,
        notice = null,
        isImporting = false,
    )

private val grantedContactsPermissionState =
    ContactsPermissionState(
        hasPermission = true,
        shouldShowRationale = false,
        permissionRequested = true,
        requestPermission = {},
    )

@PreviewTest
@Preview(name = "People settings book posture", showBackground = true, device = BOOK_FOLDABLE)
@Composable
fun A127_PeopleSettingsBookPosture() {
    provideFoldableLayoutInfo(bookPostureLayoutInfo) {
        ScreenshotTheme {
            PeopleSettingsContent(
                uiState = enabledPeopleState,
                onBack = {},
                onBrowsePeople = {},
                onOpenReviewInbox = {},
                contactsPermissionState = grantedContactsPermissionState,
                onPeopleEnabledChanged = {},
                onImportAllContacts = {},
                onImportSelectedContacts = {},
                onDismissMessage = {},
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
