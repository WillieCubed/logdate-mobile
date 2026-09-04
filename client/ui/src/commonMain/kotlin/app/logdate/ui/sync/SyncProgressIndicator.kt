@file:Suppress("ktlint:standard:function-naming")

package app.logdate.ui.sync

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Material 3 Expressive loading indicator for a sync/backup run: determinate when [total] is a
 * real, positive count, indeterminate otherwise (unknown total, or a total that hasn't arrived
 * yet).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SyncProgressIndicator(
    total: Int?,
    completed: Int,
    modifier: Modifier = Modifier,
) {
    if (total != null && total > 0) {
        LoadingIndicator(
            progress = { completed.toFloat() / total.toFloat() },
            modifier = modifier,
        )
    } else {
        LoadingIndicator(modifier = modifier)
    }
}
