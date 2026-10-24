@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.export

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.logdate.client.domain.export.ExportStats
import app.logdate.feature.core.common.DataTransferArchiveSummary
import app.logdate.feature.core.common.DataTransferResultHeader
import app.logdate.feature.core.common.OperationFailureCard
import app.logdate.feature.core.common.dataTransferCountsSummary
import app.logdate.ui.platform.PlatformIcons
import app.logdate.ui.theme.Spacing
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.export_complete_description
import logdate.client.feature.core.generated.resources.export_complete_title
import logdate.client.feature.core.generated.resources.export_failed_title
import logdate.client.ui.generated.resources.common_done
import org.jetbrains.compose.resources.stringResource
import logdate.client.ui.generated.resources.Res as UiRes

/**
 * Confirmation card shown after an export archive is saved.
 *
 * The saved archive name is intentionally rendered as first-class content so
 * users can verify the file they just created before dismissing it.
 */
@Composable
internal fun ExportSuccessCard(
    fileName: String,
    stats: ExportStats?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        DataTransferResultHeader(
            title = stringResource(Res.string.export_complete_title),
            description = stringResource(Res.string.export_complete_description),
            icon = PlatformIcons.checkCircle(),
            iconTint = MaterialTheme.colorScheme.primary,
        )
        DataTransferArchiveSummary(
            fileName = fileName,
            fileDetails = null,
            countsSummary =
                stats?.let { dataTransferCountsSummary(it.journalCount, it.noteCount, it.draftCount, it.mediaCount) },
        )

        Button(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(UiRes.string.common_done))
        }
    }
}

@Composable
internal fun ExportFailureCard(
    reason: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OperationFailureCard(
        title = stringResource(Res.string.export_failed_title),
        reason = reason,
        onRetry = onRetry,
        onDismiss = onDismiss,
        modifier = modifier,
    )
}
