@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.restore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Warning
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
import app.logdate.client.domain.restore.IntegrityCategory
import app.logdate.feature.core.common.DataStatsGrid
import app.logdate.feature.core.common.OperationFailureCard
import app.logdate.ui.theme.Spacing
import app.logdate.util.toReadableDateShort
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.export_category_drafts
import logdate.client.feature.core.generated.resources.export_category_journals
import logdate.client.feature.core.generated.resources.export_category_media
import logdate.client.feature.core.generated.resources.export_category_notes
import logdate.client.feature.core.generated.resources.restore_complete_description
import logdate.client.feature.core.generated.resources.restore_complete_title
import logdate.client.feature.core.generated.resources.restore_failed_title
import logdate.client.feature.core.generated.resources.restore_integrity_mismatch
import logdate.client.feature.core.generated.resources.restore_warnings_title
import logdate.client.ui.generated.resources.common_done
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant
import logdate.client.ui.generated.resources.Res as UiRes

@Composable
internal fun RestoreSuccessCard(
    summary: RestoreSummary,
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
            text = stringResource(Res.string.restore_complete_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(Spacing.sm))

        Text(
            text = stringResource(Res.string.restore_complete_description),
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

        val allWarnings =
            buildList {
                addAll(
                    summary.integrityMismatches.map { mismatch ->
                        val categoryName =
                            when (mismatch.category) {
                                IntegrityCategory.JOURNALS -> stringResource(Res.string.export_category_journals)
                                IntegrityCategory.NOTES -> stringResource(Res.string.export_category_notes)
                                IntegrityCategory.DRAFTS -> stringResource(Res.string.export_category_drafts)
                                IntegrityCategory.MEDIA -> stringResource(Res.string.export_category_media)
                                IntegrityCategory.PROFILE -> "Profile"
                                IntegrityCategory.PLACES -> "Places"
                                IntegrityCategory.LOCATION_HISTORY -> "Location History"
                            }
                        stringResource(
                            Res.string.restore_integrity_mismatch,
                            mismatch.expected,
                            categoryName,
                            mismatch.actual,
                        )
                    },
                )
                addAll(summary.warnings)
            }

        if (allWarnings.isNotEmpty()) {
            Spacer(modifier = Modifier.height(Spacing.lg))

            RestoreWarningsSection(warnings = allWarnings)
        }

        Spacer(modifier = Modifier.height(Spacing.xl))

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
private fun RestoreWarningsSection(
    warnings: List<String>,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Text(
                    text = stringResource(Res.string.restore_warnings_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }

            Spacer(modifier = Modifier.height(Spacing.sm))

            warnings.forEach { warning ->
                Text(
                    text = warning,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(start = Spacing.lg + Spacing.sm),
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
