package app.logdate.feature.rewind.ui

sealed interface RewindDetailUiState {
    data object Loading : RewindDetailUiState
    data class Success(
        val panels: List<RewindPanelUiState> = listOf(),
    ) : RewindDetailUiState

    data class Error(
        val type: String,
        val message: String,
    ) : RewindDetailUiState
}

val RewindDetailUiState.Success.totalPanels
    get() = panels.size

/**
 * UI state representing some part of a Rewind.
 */
sealed interface RewindPanelUiState

data class BasicTextRewindPanelUiState(
    val text: String,
    val background: RewindPanelBackgroundSpec,
) : RewindPanelUiState

/**
 * UI state representing a panel with a title and subtitle.
 *
 * @param title The title of the panel.
 * @param subtitle The subtitle of the panel.
 * @param backgroundUri The URI of the background image for the panel.
 */
data class SubtitledRewindPanelUiState(
    val title: String,
    val subtitle: String,
    val backgroundUri: String? = null,
) : RewindPanelUiState

data class BigStatisticRewindPanelUiState(
    val title: String,
    val statistic: Number,
    val units: String,
    val description: String,
    val background: RewindPanelBackgroundSpec,
) : RewindPanelUiState


/**
 * Information about the background of a panel.
 *
 * @param uri The URI of the background image. If null, the panel should use a solid color.
 * @param color The color of the panel background.
 */
data class RewindPanelBackgroundSpec(
    val uri: String? = null,
    val color: Int? = null,
)

/**
 * Whether the panel should use a background image.
 */
val RewindPanelBackgroundSpec.useBackgroundImage
    get() = uri != null