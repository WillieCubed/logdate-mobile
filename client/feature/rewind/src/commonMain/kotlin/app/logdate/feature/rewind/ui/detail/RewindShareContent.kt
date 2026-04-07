package app.logdate.feature.rewind.ui.detail

import app.logdate.feature.rewind.ui.BasicTextRewindPanelUiState
import app.logdate.feature.rewind.ui.BigStatisticRewindPanelUiState
import app.logdate.feature.rewind.ui.ImageNoteRewindPanelUiState
import app.logdate.feature.rewind.ui.ImageRewindPanelUiState
import app.logdate.feature.rewind.ui.NarrativeContextRewindPanelUiState
import app.logdate.feature.rewind.ui.RewindPanelUiState
import app.logdate.feature.rewind.ui.SubtitledRewindPanelUiState
import app.logdate.feature.rewind.ui.TextNoteRewindPanelUiState
import app.logdate.feature.rewind.ui.TransitionRewindPanelUiState

/**
 * Plain-data summary of a rewind panel suitable for handing to the system share sheet.
 *
 * The shared text is what most apps will surface (Messages, Mail, social posts), and
 * the optional [mediaUri] gives image-aware targets like Instagram or Photos something
 * richer to attach.
 */
data class RewindShareContent(
    val text: String,
    val mediaUri: String? = null,
    val dateFormatted: String? = null,
)

/**
 * Extracts shareable content from a rewind panel. Returns null when the panel
 * has nothing meaningful to share (e.g. a pure decorative big-stat card with
 * empty fields).
 */
fun RewindPanelUiState.toShareContent(): RewindShareContent? =
    when (this) {
        is TextNoteRewindPanelUiState ->
            RewindShareContent(
                text = content,
                dateFormatted = dateFormatted,
            )
        is ImageRewindPanelUiState ->
            RewindShareContent(
                text = caption ?: dateFormatted,
                mediaUri = imageUri,
                dateFormatted = dateFormatted,
            )
        is ImageNoteRewindPanelUiState ->
            RewindShareContent(
                text = caption ?: dateFormatted,
                mediaUri = imageUri,
                dateFormatted = dateFormatted,
            )
        is NarrativeContextRewindPanelUiState ->
            RewindShareContent(
                text = contextText,
                mediaUri = backgroundImageUri,
            )
        is TransitionRewindPanelUiState ->
            RewindShareContent(text = transitionText)
        is SubtitledRewindPanelUiState ->
            RewindShareContent(
                text = "$title — $subtitle",
                mediaUri = backgroundUri,
            )
        is BasicTextRewindPanelUiState ->
            text.takeIf { it.isNotBlank() }?.let { RewindShareContent(text = it) }
        is BigStatisticRewindPanelUiState ->
            if (statistic.isBlank()) {
                null
            } else {
                val unitsSuffix = if (units.isNotBlank()) " $units" else ""
                RewindShareContent(text = "$title: $statistic$unitsSuffix — $description")
            }
    }
