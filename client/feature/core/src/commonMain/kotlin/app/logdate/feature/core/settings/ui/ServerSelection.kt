package app.logdate.feature.core.settings.ui

import app.logdate.shared.model.ServerDescriptor

/**
 * Server preset options for connecting to LogDate servers.
 */
enum class ServerPreset {
    PRODUCTION,
    CUSTOM,
}

/**
 * The server a person is choosing while they sign in or create an account.
 */
data class ServerSelectionState(
    val selectedPreset: ServerPreset = ServerPreset.PRODUCTION,
    val customServerUrl: String = "",
    val validationState: ServerValidationState = ServerValidationState.Idle,
    val activeServerDescriptor: ServerDescriptor? = null,
)

/** Result of validating the selected server configuration before persisting it. */
sealed class ServerValidationState {
    /** No validation has run for the current selection. */
    data object Idle : ServerValidationState()

    /** A connection test is currently in progress. */
    data object Validating : ServerValidationState()

    /** The selected server responded successfully and reported its version. */
    data class Success(
        val serverVersion: String?,
    ) : ServerValidationState()

    /** The selected server could not be reached or rejected the request. */
    data class Error(
        val message: String,
    ) : ServerValidationState()
}
