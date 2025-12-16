package app.logdate.feature.core.account.ui

/**
 * Shared state for the account setup flow.
 *
 * This class holds the information collected throughout the account setup process,
 * allowing data to be passed between screens in the flow.
 */
class AccountSetupState {
    /**
     * The selected username for the account.
     */
    var username: String = ""
    
    /**
     * The selected display name for the account.
     */
    var displayName: String = ""
    
    /**
     * The account ID after successful creation.
     */
    var accountId: String? = null
    
    /**
     * Whether the setup process has been completed.
     */
    var isSetupCompleted: Boolean = false
    
    /**
     * Resets the state to default values.
     */
    fun reset() {
        username = ""
        displayName = ""
        accountId = null
        isSetupCompleted = false
    }
}