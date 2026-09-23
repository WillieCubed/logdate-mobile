package app.logdate.client.data.account

import app.logdate.shared.model.LogDateAccount
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The single in-memory source of truth for [DefaultPasskeyAccountRepository]'s current account
 * and authentication status, shared across its collaborator classes so they can report account
 * and session changes without each holding its own copy of the state.
 */
internal class PasskeyAccountSessionState {
    private val _currentAccount = MutableStateFlow<LogDateAccount?>(null)
    val currentAccount: StateFlow<LogDateAccount?> = _currentAccount.asStateFlow()

    private val _isAuthenticated = MutableStateFlow(false)
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()

    /** The account most recently observed, without subscribing to [currentAccount]. */
    val account: LogDateAccount? get() = _currentAccount.value

    /** Marks [account] as the signed-in account. */
    fun markAuthenticated(account: LogDateAccount) {
        _currentAccount.value = account
        _isAuthenticated.value = true
    }

    /** Updates the current account without changing authentication status. */
    fun updateAccount(account: LogDateAccount) {
        _currentAccount.value = account
    }

    fun setAuthenticated(value: Boolean) {
        _isAuthenticated.value = value
    }

    fun clearAccount() {
        _currentAccount.value = null
    }

    /** Clears both the current account and authentication status, e.g. on sign-out. */
    fun clear() {
        _currentAccount.value = null
        _isAuthenticated.value = false
    }
}
