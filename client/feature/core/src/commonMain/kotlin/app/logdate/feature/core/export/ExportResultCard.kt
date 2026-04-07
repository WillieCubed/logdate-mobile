@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.export

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.logdate.client.domain.export.ExportStats
import app.logdate.feature.core.common.DataStatsGrid
import app.logdate.feature.core.common.OperationFailureCard
import app.logdate.ui.theme.Spacing
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.action_browse
import logdate.client.feature.core.generated.resources.export_complete_description
import logdate.client.feature.core.generated.resources.export_complete_title
import logdate.client.feature.core.generated.resources.export_failed_title
import logdate.client.ui.generated.resources.common_done
import org.jetbrains.compose.resources.stringResource
import logdate.client.ui.generated.resources.Res as UiRes

@Composable
internal fun ExportSuccessCard(
    fileName: String,
    stats: ExportStats?,
    onBrowse: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Rounded.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.primary,
        )

        Spacer(modifier = Modifier.height(Spacing.md))

        Text(
            text = stringResource(Res.string.export_complete_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(Spacing.sm))

        Text(
            text = stringResource(Res.string.export_complete_description),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        stats?.let {
            Spacer(modifier = Modifier.height(Spacing.lg))

            DataStatsGrid(
                journalCount = it.journalCount,
                noteCount = it.noteCount,
                draftCount = it.draftCount,
                mediaCount = it.mediaCount,
            )
        }

        Spacer(modifier = Modifier.height(Spacing.xl))

        Button(
            onClick = onBrowse,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(Res.string.action_browse))
        }
        TextButton(
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
