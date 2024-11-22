package app.logdate.feature.rewind.ui.overview

import kotlin.uuid.Uuid

data class RewindHistoryUiState(
    /**
     * The UID of the [app.logdate.model.Rewind] this state represents.
     */
    val uid: Uuid,
    /**
     * The title of the [app.logdate.model.Rewind] this state represents.
     */
    val title: String,
)