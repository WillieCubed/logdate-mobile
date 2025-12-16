package app.logdate.feature.core.account.ui

import app.logdate.client.domain.account.AccountSetupData
import app.logdate.client.domain.account.GetAccountSetupDataUseCase
import io.github.aakira.napier.Napier

/**
 * Helper use case to save account setup data.
 * This is used to pass data between screens in the account creation flow.
 */
class SaveAccountSetupDataUseCase(
    private val getAccountSetupDataUseCase: GetAccountSetupDataUseCase
) {
    /**
     * Saves the account setup data.
     * @param username The username to save
     * @param displayName The display name to save
     * @param email Optional email to save
     */
    operator fun invoke(username: String, displayName: String, email: String? = null) {
        try {
            val accountSetupData = AccountSetupData(
                username = username,
                displayName = displayName,
                email = email
            )
            getAccountSetupDataUseCase(
                action = GetAccountSetupDataUseCase.Action.Save,
                data = accountSetupData
            )
            Napier.d("Account setup data saved successfully")
        } catch (e: Exception) {
            Napier.e("Failed to save account setup data", e)
        }
    }
}