package app.logdate.feature.rewind.ui.detail

import app.logdate.feature.rewind.ui.ImageRewindPanelUiState
import app.logdate.shared.model.RewindContent

/** Android's configured Coil loader extracts the video frame shown in this panel. */
internal fun RewindContent.Video.toVideoFramePanel(dateFormatted: String): ImageRewindPanelUiState =
    ImageRewindPanelUiState(
        sourceId = sourceId,
        timestamp = timestamp,
        imageUri = uri,
        caption = caption,
        dateFormatted = dateFormatted,
        isVideoFrame = true,
    )
