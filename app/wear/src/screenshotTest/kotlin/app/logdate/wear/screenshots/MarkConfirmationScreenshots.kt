package app.logdate.wear.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.logdate.wear.R
import app.logdate.wear.v2.mark.MarkConfirmationContent
import app.logdate.wear.v2.mark.MarkFeedback
import app.logdate.wear.v2.theme.LogDateWearTheme
import com.android.tools.screenshot.PreviewTest

/** What Mark shows when it saves a mark, and when it can't. */
class MarkConfirmationScreenshots {
    @PreviewTest
    @WearAllSizesPreview
    @Composable
    fun M01_Marked() {
        DialogScene(MarkFeedback.MARKED)
    }

    @PreviewTest
    @WearAllSizesPreview
    @Composable
    fun M02_MarkFailed() {
        DialogScene(MarkFeedback.FAILED)
    }
}

@Composable
private fun DialogScene(feedback: MarkFeedback) {
    val failed = feedback == MarkFeedback.FAILED
    val text = stringResource(if (failed) R.string.wear_mark_failed else R.string.wear_mark_saved)
    LogDateWearTheme(dynamicColor = false) {
        SettledConfirmationScene(text = text, failed = failed) { MarkConfirmationContent(feedback) }
    }
}
