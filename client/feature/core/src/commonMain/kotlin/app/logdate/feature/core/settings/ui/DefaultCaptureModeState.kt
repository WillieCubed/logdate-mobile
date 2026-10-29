package app.logdate.feature.core.settings.ui

import app.logdate.client.datastore.EntryStartMode

sealed interface DefaultCaptureModeState {
    data object Loading : DefaultCaptureModeState

    data object Unavailable : DefaultCaptureModeState

    data class Ready(
        val mode: EntryStartMode,
        val saving: Boolean = false,
        val saveFailed: Boolean = false,
    ) : DefaultCaptureModeState
}
