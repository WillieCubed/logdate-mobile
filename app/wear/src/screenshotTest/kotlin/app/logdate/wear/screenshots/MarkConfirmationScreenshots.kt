package app.logdate.wear.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.wear.compose.foundation.CurvedDirection
import androidx.wear.compose.foundation.CurvedLayout
import androidx.wear.compose.material3.ConfirmationDialogDefaults
import androidx.wear.compose.material3.LocalContentColor
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.confirmationDialogCurvedText
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

/**
 * The dialog as it looks once it has appeared. A screenshot is the first frame, when the dialog's
 * entrance animation has only just started and its text is still transparent, so the text is drawn
 * again at full opacity where the dialog places it: along the bottom edge, anchored at 90 degrees.
 */
@Composable
private fun DialogScene(feedback: MarkFeedback) {
    LogDateWearTheme(dynamicColor = false) {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            MarkConfirmationContent(feedback)
            SettledText(feedback)
        }
    }
}

@Composable
private fun SettledText(feedback: MarkFeedback) {
    val marked = feedback == MarkFeedback.MARKED
    val text = stringResource(if (marked) R.string.wear_mark_saved else R.string.wear_mark_failed)
    val colors = if (marked) ConfirmationDialogDefaults.colors() else ConfirmationDialogDefaults.failureColors()
    val style = ConfirmationDialogDefaults.curvedTextStyle
    CompositionLocalProvider(LocalContentColor provides colors.textColor) {
        CurvedLayout(anchor = 90f, angularDirection = CurvedDirection.Angular.Reversed) {
            confirmationDialogCurvedText(text, style)
        }
    }
}
