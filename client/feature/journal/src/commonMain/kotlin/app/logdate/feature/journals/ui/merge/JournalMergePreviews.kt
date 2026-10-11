@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.journals.ui.merge

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import app.logdate.client.repository.journals.JournalMergeCandidate
import app.logdate.client.repository.journals.JournalMergePreview
import app.logdate.shared.model.Journal
import app.logdate.ui.theme.LogDateTheme
import app.logdate.ui.theme.Spacing
import kotlin.uuid.Uuid

private val previewSource = Journal(id = previewId(201), title = "Summer in the mountains")
private val previewDestination = Journal(id = previewId(202), title = "Journeys with friends and family")
private val previewMerge =
    JournalMergePreview(
        previewSource,
        previewDestination,
        (1..12).mapTo(mutableSetOf()) { previewId(it) },
        (9..36).mapTo(mutableSetOf()) { previewId(it) },
    )
private val previewPicker =
    JournalMergeUiState(
        sourceTitle = previewSource.title,
        source = JournalMergeCandidate(previewSource, 12),
        loading = false,
        candidates =
            listOf(
                JournalMergeCandidate(previewDestination, 28),
                JournalMergeCandidate(Journal(id = previewId(203), title = "Weekend field notes"), 16),
            ),
    )

@Preview(widthDp = 400)
@Composable
private fun MergeSourceContextPreview() {
    MergeComponentPreview { JournalMergeSourceContext(previewPicker) }
}

@Preview(widthDp = 480, heightDp = 600)
@Composable
private fun MergeDestinationPickerPreview() {
    LogDateTheme { JournalMergeDestinationPicker(previewPicker, {}, {}) }
}

@Preview(widthDp = 400)
@Composable
private fun MergeIdentityComparisonPreview() {
    MergeComponentPreview { JournalMergeIdentityComparison(previewMerge, false) }
}

@Preview(widthDp = 480)
@Composable
private fun MergeSelectionPreview() {
    MergeComponentPreview { JournalMergeSelection(previewMerge, {}, true) }
}

@Preview(widthDp = 480)
@Composable
private fun MergeConsequencesPreview() {
    MergeComponentPreview { JournalMergeConsequences(false) }
}

@Preview(widthDp = 400)
@Composable
private fun MergeActionsPreview() {
    MergeComponentPreview {
        JournalMergeActions(previewPicker.copy(stage = JournalMergeStage.Review(previewMerge)), {}, {})
    }
}

@Preview(widthDp = 400)
@Composable
private fun MergeFeedbackPreview() {
    MergeComponentPreview { JournalMergeFeedback(JournalMergeError.Failed) }
}

@Preview(widthDp = 411, heightDp = 891)
@Preview(widthDp = 1280, heightDp = 800)
@Composable
private fun MergePickerActivityPreview() {
    LogDateTheme { JournalMergeScreenContent(previewPicker) }
}

@Preview(widthDp = 411, heightDp = 891)
@Preview(widthDp = 1280, heightDp = 800)
@Composable
private fun MergeReviewActivityPreview() {
    LogDateTheme { JournalMergeScreenContent(previewPicker.copy(stage = JournalMergeStage.Review(previewMerge))) }
}

@Composable
private fun MergeComponentPreview(content: @Composable () -> Unit) {
    LogDateTheme { Surface { Column(Modifier.padding(Spacing.lg)) { content() } } }
}

private fun previewId(value: Int) = Uuid.parse("00000000-0000-0000-0000-${value.toString().padStart(12, '0')}")
