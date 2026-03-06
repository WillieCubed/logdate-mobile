package app.logdate.feature.core.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.datastore.LogdatePreferencesDataSource
import app.logdate.client.datastore.SessionStorage
import app.logdate.client.domain.account.GetCurrentAccountUseCase
import app.logdate.client.domain.profile.UpdateProfileUseCase
import app.logdate.client.repository.account.PasskeyAccountRepository
import app.logdate.client.repository.user.UserStateRepository
import app.logdate.shared.model.LogDateAccount
import app.logdate.shared.model.user.UserData
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Instant

data class AccountSettingsState(
    val userData: UserData,
    val currentAccount: LogDateAccount,
    val isAuthenticated: Boolean,
)

sealed class ProfileUpdateState {
    data object Idle : ProfileUpdateState()

    data object Updating : ProfileUpdateState()

    data object Success : ProfileUpdateState()

    data class Error(
        val message: String,
    ) : ProfileUpdateState()
}

sealed class BirthdayUpdateState {
    data object Idle : BirthdayUpdateState()

    data object Updating : BirthdayUpdateState()

    data object Success : BirthdayUpdateState()

    data class Error(
        val message: String,
    ) : BirthdayUpdateState()
}

class AccountSettingsViewModel(
    private val userStateRepository: UserStateRepository,
    private val getCurrentAccountUseCase: GetCurrentAccountUseCase,
    private val updateProfileUseCase: UpdateProfileUseCase,
    private val passkeyAccountRepository: PasskeyAccountRepository,
    private val sessionStorage: SessionStorage,
    private val preferencesDataSource: LogdatePreferencesDataSource,
) : ViewModel() {
    private val _profileUpdateState = MutableStateFlow<ProfileUpdateState>(ProfileUpdateState.Idle)
    val profileUpdateState: StateFlow<ProfileUpdateState> = _profileUpdateState

    private val _birthdayUpdateState = MutableStateFlow<BirthdayUpdateState>(BirthdayUpdateState.Idle)
    val birthdayUpdateState: StateFlow<BirthdayUpdateState> = _birthdayUpdateState

    private val currentAccountFlow: Flow<LogDateAccount?> =
        flow {
            val result = getCurrentAccountUseCase(GetCurrentAccountUseCase.AccountRequest.GetCurrentAccount)
            when (result) {
                is GetCurrentAccountUseCase.AccountResult.CurrentAccount -> {
                    result.account.collect { emit(it) }
                }
                else -> emit(null)
            }
        }

    val state: StateFlow<AccountSettingsState> =
        combine(
            userStateRepository.userData,
            currentAccountFlow,
            sessionStorage.getSessionFlow(),
        ) { userData, currentAccount, session ->
            AccountSettingsState(
                userData = userData.orDefault(),
                currentAccount = currentAccount.orDefault(),
                isAuthenticated = session != null,
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            AccountSettingsState(
                userData = (null as UserData?).orDefault(),
                currentAccount = (null as LogDateAccount?).orDefault(),
                isAuthenticated = false,
            ),
        )

    fun updateProfile(
        displayName: String,
        username: String,
    ) {
        val trimmedDisplayName = displayName.trim()
        val trimmedUsername = username.trim()
        val displayNameUpdate = trimmedDisplayName.takeIf { it.isNotEmpty() }
        val usernameUpdate = trimmedUsername.takeIf { it.isNotEmpty() }

        if (displayNameUpdate == null && usernameUpdate == null) {
            _profileUpdateState.value = ProfileUpdateState.Error("No profile changes to save")
            return
        }

        viewModelScope.launch {
            _profileUpdateState.value = ProfileUpdateState.Updating

            when (val result = updateProfileUseCase(displayName = displayNameUpdate, username = usernameUpdate)) {
                is UpdateProfileUseCase.Result.Success -> {
                    if (displayNameUpdate != null) {
                        preferencesDataSource.updateDisplayName(displayNameUpdate)
                    }
                    getCurrentAccountUseCase(GetCurrentAccountUseCase.AccountRequest.RefreshAccountInfo)
                    _profileUpdateState.value = ProfileUpdateState.Success
                }
                is UpdateProfileUseCase.Result.Error -> {
                    val error = result.error
                    val message =
                        when (error) {
                            is UpdateProfileUseCase.ProfileUpdateError.InvalidDisplayName -> "Invalid display name"
                            is UpdateProfileUseCase.ProfileUpdateError.InvalidUsername -> "Invalid username"
                            is UpdateProfileUseCase.ProfileUpdateError.NetworkError -> "Network error updating profile"
                            is UpdateProfileUseCase.ProfileUpdateError.Unknown -> error.message
                        }
                    _profileUpdateState.value = ProfileUpdateState.Error(message)
                }
            }
        }
    }

    fun updateBirthday(birthday: Instant) {
        Napier.d("AccountSettingsViewModel: updateBirthday called with $birthday")
        viewModelScope.launch {
            try {
                _birthdayUpdateState.value = BirthdayUpdateState.Updating
                userStateRepository.setBirthday(birthday)
                _birthdayUpdateState.value = BirthdayUpdateState.Success
            } catch (e: Exception) {
                Napier.e("AccountSettingsViewModel: failed to update birthday", e)
                _birthdayUpdateState.value =
                    BirthdayUpdateState.Error(
                        e.message ?: "Failed to update birthday",
                    )
            }
        }
    }

    fun resetBirthdayUpdateState() {
        _birthdayUpdateState.value = BirthdayUpdateState.Idle
    }

    /**
     * Signs out the user. On success, [state]`.isAuthenticated` will become `false`
     * through the reactive session flow, which the UI observes.
     *
     * @param onError Called with an error message if sign-out fails.
     */
    fun signOut(onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            try {
                Napier.i("Signing out user")
                passkeyAccountRepository.signOut()
                preferencesDataSource.setBackgroundSyncEnabled(false)
                Napier.i("Session cleared successfully")
            } catch (e: Exception) {
                Napier.e("Failed to sign out", e)
                onError(e.message ?: "Failed to sign out")
            }
        }
    }
}
