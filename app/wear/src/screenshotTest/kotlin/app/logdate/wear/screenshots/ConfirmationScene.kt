package app.logdate.wear.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.wear.compose.foundation.CurvedDirection
import androidx.wear.compose.foundation.CurvedLayout
import androidx.wear.compose.material3.ConfirmationDialogDefaults
import androidx.wear.compose.material3.LocalContentColor
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.confirmationDialogCurvedText

/**
 * A confirmation dialog's content as it looks once it has appeared. A screenshot is the first frame,
 * when the dialog's entrance animation has only just started and its text is still transparent, so
 * [text] is drawn again at full opacity where the dialog places it: along the bottom edge, anchored
 * at 90 degrees, in the colors of the failure dialog when [failed] and otherwise the default ones.
 * The dialog's icon is still its first animation frame.
 */
@Composable
internal fun SettledConfirmationScene(
    text: String,
    failed: Boolean,
    dialog: @Composable () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        dialog()
        val colors = if (failed) ConfirmationDialogDefaults.failureColors() else ConfirmationDialogDefaults.colors()
        val style = ConfirmationDialogDefaults.curvedTextStyle
        CompositionLocalProvider(LocalContentColor provides colors.textColor) {
            CurvedLayout(anchor = 90f, angularDirection = CurvedDirection.Angular.Reversed) {
                confirmationDialogCurvedText(text, style)
            }
        }
    }
}
