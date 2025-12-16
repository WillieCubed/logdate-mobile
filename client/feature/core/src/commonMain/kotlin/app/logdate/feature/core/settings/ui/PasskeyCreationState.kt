package app.logdate.feature.core.settings.ui

import app.logdate.shared.model.LogDateAccount

/**
 * Represents the state of passkey creation operations.
 */
sealed class PasskeyCreationState {
    /**
     * No passkey creation operation is in progress.
     */
    object Idle : PasskeyCreationState()
    
    /**
     * A passkey creation operation is in progress.
     */
    object Creating : PasskeyCreationState()
    
    /**
     * A passkey creation operation completed successfully.
     */
    data class Success(val account: LogDateAccount) : PasskeyCreationState()
    
    /**
     * A passkey creation operation failed.
     */
    data class Error(val message: String) : PasskeyCreationState()
}