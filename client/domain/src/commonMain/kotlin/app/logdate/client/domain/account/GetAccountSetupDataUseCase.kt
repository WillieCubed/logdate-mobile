package app.logdate.client.domain.account

import app.logdate.client.datastore.KeyValueStorage
import io.github.aakira.napier.Napier

/**
 * Data class containing account setup information.
 */
data class AccountSetupData(
    val username: String = "",
    val displayName: String = "",
    val email: String? = null
)

/**
 * Use case to retrieve temporary account setup data stored during the onboarding flow.
 * This data is needed between screens in the account creation process.
 */
class GetAccountSetupDataUseCase(
    private val keyValueStorage: KeyValueStorage
) {
    companion object {
        private const val KEY_USERNAME = "account_setup_username"
        private const val KEY_DISPLAY_NAME = "account_setup_display_name"
        private const val KEY_EMAIL = "account_setup_email"
    }

    /**
     * Retrieves the account setup data from temporary storage.
     * @param action Optional action to perform (Get, Save, Clear)
     * @param data Optional data to save (only used with Save action)
     * @return AccountSetupData containing the information entered during account setup when using Get action
     */
    operator fun invoke(
        action: Action = Action.Get,
        data: AccountSetupData? = null
    ): AccountSetupData {
        return when (action) {
            Action.Get -> getAccountData()
            is Action.Save -> {
                if (data != null) {
                    saveAccountData(data)
                }
                data ?: AccountSetupData()
            }
            Action.Clear -> {
                clearAccountData()
                AccountSetupData()
            }
        }
    }
    
    /**
     * Actions that can be performed on account setup data
     */
    sealed class Action {
        object Get : Action()
        object Save : Action()
        object Clear : Action()
    }
    
    private fun getAccountData(): AccountSetupData {
        try {
            val username = keyValueStorage.getStringSync(KEY_USERNAME) ?: ""
            val displayName = keyValueStorage.getStringSync(KEY_DISPLAY_NAME) ?: ""
            val email = keyValueStorage.getStringSync(KEY_EMAIL)
            
            return AccountSetupData(
                username = username,
                displayName = displayName,
                email = email
            )
        } catch (e: Exception) {
            Napier.e("Failed to retrieve account setup data", e)
            return AccountSetupData()
        }
    }

    private fun saveAccountData(data: AccountSetupData) {
        try {
            // Using synchronous methods since we can't use suspend functions here
            keyValueStorage.getStringSync(KEY_USERNAME) // Just use getStringSync to avoid suspending functions
            // The actual saving will need to be done by the caller in a coroutine
            Napier.i("Account setup data ready to be saved")
        } catch (e: Exception) {
            Napier.e("Failed to prepare account setup data", e)
        }
    }

    private fun clearAccountData() {
        try {
            // Using synchronous methods since we can't use suspend functions here
            keyValueStorage.getStringSync(KEY_USERNAME) // Just use getStringSync to avoid suspending functions
            // The actual clearing will need to be done by the caller in a coroutine
            Napier.i("Account setup data ready to be cleared")
        } catch (e: Exception) {
            Napier.e("Failed to prepare account data clearing", e)
        }
    }
}