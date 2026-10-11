@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.journals.ui.merge

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.clearAndSetSemantics
import app.logdate.client.repository.journals.JournalMergePreview
import logdate.client.feature.journal.generated.resources.Res
import logdate.client.feature.journal.generated.resources.journal_merge_combined
import logdate.client.feature.journal.generated.resources.journal_merge_current_count
import logdate.client.feature.journal.generated.resources.journal_merge_destination_count
import logdate.client.feature.journal.generated.resources.journal_merge_item_count
import logdate.client.feature.journal.generated.resources.journal_merge_overlap
import logdate.client.feature.journal.generated.resources.journal_merge_result_count
import logdate.client.feature.journal.generated.resources.journal_merge_shared_count
import logdate.client.feature.journal.generated.resources.journal_merge_source_count
import org.jetbrains.compose.resources.pluralStringResource

@Composable
internal fun JournalMergeCardMetadata(
    preview: JournalMergePreview,
    source: Boolean,
) {
    val count = if (source) preview.sourceCount else preview.destinationContentIds.size
    val description =
        pluralStringResource(
            if (source) Res.plurals.journal_merge_source_count else Res.plurals.journal_merge_destination_count,
            count,
            count,
        )
    val labels =
        mutableListOf(
            pluralStringResource(
                if (source) Res.plurals.journal_merge_item_count else Res.plurals.journal_merge_current_count,
                count,
                count,
            ),
        )
    val descriptions = mutableListOf(description)
    if (source && preview.overlapCount > 0) {
        labels += pluralStringResource(Res.plurals.journal_merge_shared_count, preview.overlapCount, preview.overlapCount)
        descriptions += pluralStringResource(Res.plurals.journal_merge_overlap, preview.overlapCount, preview.overlapCount)
    } else if (!source) {
        labels += pluralStringResource(Res.plurals.journal_merge_result_count, preview.combinedCount, preview.combinedCount)
        descriptions += pluralStringResource(Res.plurals.journal_merge_combined, preview.combinedCount, preview.combinedCount)
    }
    Text(
        labels.joinToString(" · "),
        Modifier.clearAndSetSemantics { this[SemanticsProperties.ContentDescription] = descriptions },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
