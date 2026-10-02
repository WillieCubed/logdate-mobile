package app.logdate.feature.rewind.ui

import app.logdate.feature.rewind.ui.overview.RewindPreviewUiState

internal fun uniqueRewindPreviews(previews: List<RewindPreviewUiState>): List<RewindPreviewUiState> = previews.distinctBy { it.rewindId }
