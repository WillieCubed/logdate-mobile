package app.logdate.wear.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import app.logdate.wear.R
import app.logdate.wear.v2.capture.CaptureConfirmationContent
import app.logdate.wear.v2.capture.CaptureResult
import app.logdate.wear.v2.capture.CaptureSheet
import app.logdate.wear.v2.capture.CaptureSheetUiState
import app.logdate.wear.v2.theme.LogDateWearTheme
import com.android.tools.screenshot.PreviewTest

/** The + sheet in each state, and what a save from it shows. */
class CaptureSheetScreenshots {
    @PreviewTest
    @WearAllSizesPreview
    @Composable
    fun C01_Sheet() {
        CaptureScene(CaptureSheetUiState())
    }

    @PreviewTest
    @WearAllSizesPreview
    @Composable
    fun C02_PhoneInReach() {
        CaptureScene(CaptureSheetUiState(phoneInReach = true), anchorItem = PHONE_CAMERA_ITEM)
    }

    @PreviewTest
    @WearAllSizesPreview
    @Composable
    fun C03_ChoosingAFeeling() {
        CaptureScene(CaptureSheetUiState(choosingFeeling = true), anchorItem = FIRST_FEELING_ITEM)
    }

    @PreviewTest
    @WearAllSizesPreview
    @Composable
    fun C04_Saved() {
        ConfirmationScene(CaptureResult.SAVED)
    }

    @PreviewTest
    @WearAllSizesPreview
    @Composable
    fun C05_SaveFailed() {
        ConfirmationScene(CaptureResult.FAILED)
    }
}

@Composable
private fun ConfirmationScene(result: CaptureResult) {
    val failed = result == CaptureResult.FAILED
    val text = stringResource(if (failed) R.string.wear_capture_failed else R.string.wear_capture_saved)
    LogDateWearTheme(dynamicColor = false) {
        SettledConfirmationScene(text = text, failed = failed) { CaptureConfirmationContent(result) }
    }
}

private const val FIRST_FEELING_ITEM = 3
private const val PHONE_CAMERA_ITEM = 3

@Composable
private fun CaptureScene(
    state: CaptureSheetUiState,
    anchorItem: Int = 0,
) {
    LogDateWearTheme(dynamicColor = false) {
        AppScaffold(timeText = {}) {
            CaptureSheet(
                state = state,
                onWrite = {},
                onToggleFeelings = {},
                onFeeling = {},
                onPhoneCamera = {},
                listState = rememberTransformingLazyColumnState(initialAnchorItemIndex = anchorItem),
            )
        }
    }
}
