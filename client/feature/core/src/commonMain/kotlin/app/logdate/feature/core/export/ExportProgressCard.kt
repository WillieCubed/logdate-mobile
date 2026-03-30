@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.export

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.logdate.feature.core.common.DataTransferProgressCard
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.export_progress_title
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ExportProgressCard(
    progressPercent: Int,
    message: String,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DataTransferProgressCard(
        title = stringResource(Res.string.export_progress_title),
        progressPercent = progressPercent,
        message = message,
        onCancel = onCancel,
        modifier = modifier,
    )
}
