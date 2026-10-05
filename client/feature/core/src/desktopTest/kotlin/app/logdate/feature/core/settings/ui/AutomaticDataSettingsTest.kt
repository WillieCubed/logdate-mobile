package app.logdate.feature.core.settings.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import app.logdate.feature.core.export.ExportState
import app.logdate.feature.core.restore.RestoreState
import app.logdate.ui.foldable.FoldableHingeBounds
import app.logdate.ui.foldable.FoldableHingeInfo
import app.logdate.ui.foldable.FoldableHingeOrientation
import app.logdate.ui.foldable.FoldableHingeState
import app.logdate.ui.foldable.FoldableLayoutInfo
import app.logdate.ui.foldable.FoldableOcclusionType
import app.logdate.ui.foldable.FoldablePosture
import app.logdate.ui.foldable.provideFoldableLayoutInfo
import app.logdate.ui.theme.LogDateTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class AutomaticDataSettingsTest {
    @Test
    fun phoneDoesNotAskUsersToRepairSyncMetadata() = assertAutomaticRecovery(FoldableLayoutInfo(), 411)

    @Test
    fun bookPostureDoesNotAskUsersToRepairSyncMetadata() =
        assertAutomaticRecovery(
            FoldableLayoutInfo(
                isFoldable = true,
                posture = FoldablePosture.Book,
                hinge =
                    FoldableHingeInfo(
                        orientation = FoldableHingeOrientation.Vertical,
                        state = FoldableHingeState.HalfOpened,
                        occlusionType = FoldableOcclusionType.Full,
                        bounds = FoldableHingeBounds(708.dp, 0.dp, 732.dp, 900.dp, 24.dp, 900.dp),
                        isSeparating = true,
                    ),
            ),
            1440,
        )

    private fun assertAutomaticRecovery(
        layout: FoldableLayoutInfo,
        width: Int,
    ) = runDesktopComposeUiTest(width = width, height = 900) {
        var exports = 0
        var imports = 0
        setContent {
            LogDateTheme(darkTheme = false) {
                provideFoldableLayoutInfo(layout) {
                    DataSettingsContent(
                        onBack = {},
                        quotaUsage = StorageQuotaUi(100, 0, 0f, formattedTotal = "100 B", formattedUsed = "0 B"),
                        isQuotaAvailable = false,
                        exportState = ExportState.Idle,
                        onShowExportOptions = { exports++ },
                        onUpdateExportOptions = {},
                        onConfirmExport = {},
                        onCancelExport = {},
                        onRetryExport = {},
                        onDismissExport = {},
                        onBrowseExport = {},
                        restoreState = RestoreState.Idle,
                        onShowRestoreSheet = { imports++ },
                        onSelectRestoreFile = {},
                        onUpdateImportOptions = {},
                        onConfirmImport = {},
                        onCancelRestore = {},
                        onRetryRestore = {},
                        onDismissRestore = {},
                        integrityState = IntegrityState(errorMessage = "Internal metadata failure"),
                        onRunIntegrityCheck = {},
                        onRepairIntegrity = {},
                        snackbarHostState = SnackbarHostState(),
                        cloudArchiveStatus = CloudArchiveStatus(CloudArchivePhase.FAILED),
                        isAuthenticated = true,
                    )
                }
            }
        }
        onNodeWithText("Integrity check").assertDoesNotExist()
        onNodeWithText("Repair").assertDoesNotExist()
        onNodeWithText("Internal metadata failure").assertDoesNotExist()
        onAllNodesWithText("Sync couldn't finish").assertCountEquals(1)
        onNodeWithText("Export").performClick()
        onNodeWithText("Import").performClick()
        assertEquals(1, exports)
        assertEquals(1, imports)
    }
}
