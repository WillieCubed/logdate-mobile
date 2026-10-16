@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.restore

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.logdate.feature.core.common.DataStatsGrid
import app.logdate.feature.core.common.OperationFailureCard
import app.logdate.ui.platform.PlatformIcons
import app.logdate.ui.theme.Spacing
import app.logdate.util.toReadableDateShort
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.restore_complete_description
import logdate.client.feature.core.generated.resources.restore_complete_title
import logdate.client.feature.core.generated.resources.restore_failed_title
import logdate.client.feature.core.generated.resources.restore_interrupted_description
import logdate.client.feature.core.generated.resources.restore_interrupted_title
import logdate.client.feature.core.generated.resources.restore_partial_description
import logdate.client.feature.core.generated.resources.restore_partial_title
import logdate.client.feature.core.generated.resources.restore_paused_description
import logdate.client.feature.core.generated.resources.restore_paused_title
import logdate.client.feature.core.generated.resources.restore_recover_media
import logdate.client.feature.core.generated.resources.restore_recovering_description
import logdate.client.feature.core.generated.resources.restore_recovering_media
import logdate.client.feature.core.generated.resources.restore_resume_import
import logdate.client.feature.core.generated.resources.restore_resuming_description
import logdate.client.feature.core.generated.resources.restore_resuming_title
import logdate.client.feature.core.generated.resources.restore_unreadable_description
import logdate.client.feature.core.generated.resources.restore_unreadable_title
import logdate.client.ui.generated.resources.common_done
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant
import logdate.client.ui.generated.resources.Res as UiRes

@Composable
internal fun RestoreSuccessCard(
    summary: RestoreSummary,
    onDismiss: () -> Unit,
    onRecoverMedia: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val incomplete =
        summary.needsAnotherArchive || summary.metadataIncomplete || summary.unavailableOriginals + summary.unavailablePreviews > 0
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = PlatformIcons.checkCircle(),
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.primary,
        )

        Spacer(modifier = Modifier.height(Spacing.md))

        Text(
            text =
                stringResource(
                    if (summary.metadataIncomplete && summary.recoveringMedia) {
                        Res.string.restore_resuming_title
                    } else if (summary.metadataIncomplete && summary.canResume) {
                        Res.string.restore_paused_title
                    } else if (summary.metadataIncomplete) {
                        Res.string.restore_interrupted_title
                    } else if (summary.needsAnotherArchive) {
                        Res.string.restore_unreadable_title
                    } else if (summary.recoveringMedia) {
                        Res.string.restore_recovering_media
                    } else if (incomplete) {
                        Res.string.restore_partial_title
                    } else {
                        Res.string.restore_complete_title
                    },
                ),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(Spacing.sm))

        Text(
            text =
                if (summary.metadataIncomplete && summary.recoveringMedia) {
                    stringResource(Res.string.restore_resuming_description)
                } else if (summary.metadataIncomplete && summary.canResume) {
                    stringResource(Res.string.restore_paused_description)
                } else if (summary.metadataIncomplete) {
                    stringResource(Res.string.restore_interrupted_description)
                } else if (summary.needsAnotherArchive) {
                    stringResource(Res.string.restore_unreadable_description)
                } else if (summary.recoveringMedia) {
                    stringResource(Res.string.restore_recovering_description)
                } else if (incomplete) {
                    stringResource(
                        Res.string.restore_partial_description,
                        summary.unavailableOriginals,
                        summary.unavailablePreviews,
                    )
                } else {
                    stringResource(Res.string.restore_complete_description)
                },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(Spacing.lg))

        ArchiveMetadataRow(
            fileName = summary.source,
            exportDate = summary.exportDate,
            appVersion = summary.appVersion,
        )

        Spacer(modifier = Modifier.height(Spacing.lg))

        DataStatsGrid(
            journalCount = summary.journalsImported,
            noteCount = summary.notesImported,
            draftCount = summary.draftsImported,
            mediaCount = summary.mediaImported,
        )

        Spacer(modifier = Modifier.height(Spacing.xl))

        if (incomplete &&
            (!summary.recoveringMedia || summary.needsAnotherArchive && !summary.metadataIncomplete) &&
            summary.recoveryId != null
        ) {
            TextButton(onClick = onRecoverMedia) {
                Text(stringResource(if (summary.canResume) Res.string.restore_resume_import else Res.string.restore_recover_media))
            }
        }
        TextButton(onClick = onDismiss) {
            Text(stringResource(UiRes.string.common_done))
        }
    }
}

@Composable
private fun ArchiveMetadataRow(
    fileName: String,
    exportDate: Instant?,
    appVersion: String?,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
        ) {
            Text(
                text = fileName,
                style = MaterialTheme.typography.labelLarge,
            )
            val details =
                buildList {
                    exportDate?.let { add("Exported ${it.toReadableDateShort()}") }
                    appVersion?.let { add("v$it") }
                }.joinToString(" \u00B7 ")
            if (details.isNotEmpty()) {
                Text(
                    text = details,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun RestoreFailureCard(
    reason: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OperationFailureCard(
        title = stringResource(Res.string.restore_failed_title),
        reason = reason,
        onRetry = onRetry,
        onDismiss = onDismiss,
        modifier = modifier,
    )
}
