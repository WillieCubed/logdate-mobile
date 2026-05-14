package app.logdate.feature.core.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.datastore.LogdatePreferencesDataSource
import app.logdate.client.datastore.SessionStorage
import app.logdate.client.domain.account.EmailVerificationAvailability
import app.logdate.client.domain.account.GetCurrentAccountUseCase
import app.logdate.client.domain.account.VerifyEmailUseCase
import app.logdate.client.domain.identity.ObserveUserIdentityUseCase
import app.logdate.client.domain.identity.ResolvedUserIdentity
import app.logdate.client.domain.profile.UpdateProfileUseCase
import app.logdate.client.domain.streak.ObserveStreakUseCase
import app.logdate.client.domain.streak.RefreshStreakUseCase
import app.logdate.client.domain.streak.StreakData
import app.logdate.client.permissions.EmailVerificationOutcome
import app.logdate.client.repository.account.AccountHostedPlcOperation
import app.logdate.client.repository.account.AccountIdentityRepository
import app.logdate.client.repository.account.AccountIdentityStatus
import app.logdate.client.repository.account.PasskeyAccountRepository
import app.logdate.client.repository.user.UserStateRepository
import app.logdate.client.sync.metadata.SyncMetadataService
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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class AccountSettingsState(
    val userData: UserData,
    val currentAccount: LogDateAccount,
    val isAuthenticated: Boolean,
    /** Cached from [EmailVerificationAvailability]; hides the Settings row when false. */
    val isEmailVerificationAvailable: Boolean = false,
    val isVerifyingEmail: Boolean = false,
    val emailVerificationOutcome: EmailVerificationOutcome? = null,
)

data class AccountIdentityState(
    val isLoading: Boolean = false,
    val status: AccountIdentityStatus? = null,
    val operations: List<AccountHostedPlcOperation> = emptyList(),
    val actionState: IdentityActionState = IdentityActionState.Idle,
    val exportedKeyJson: String? = null,
    val derivedRecoveryDidKey: String? = null,
)

sealed class ProfileUpdateState {
    data object Idle : ProfileUpdateState()

    data object Updating : ProfileUpdateState()

    data object Success : ProfileUpdateState()

    data class Error(
        val message: String,
    ) : ProfileUpdateState()
}

sealed class IdentityActionState {
    data object Idle : IdentityActionState()

    data class Working(
        val label: String,
    ) : IdentityActionState()

    data class Success(
        val message: String,
    ) : IdentityActionState()

    data class Error(
        val message: String,
    ) : IdentityActionState()
}

/** Internal aggregator for the Settings-bottom-sheet email-verification slice. */
private data class EmailVerificationViewState(
    val isAvailable: Boolean = false,
    val isVerifying: Boolean = false,
    val outcome: EmailVerificationOutcome? = null,
)

class AccountSettingsViewModel(
    private val userStateRepository: UserStateRepository,
    private val getCurrentAccountUseCase: GetCurrentAccountUseCase,
    private val updateProfileUseCase: UpdateProfileUseCase,
    private val accountIdentityRepository: AccountIdentityRepository,
    private val passkeyAccountRepository: PasskeyAccountRepository,
    private val sessionStorage: SessionStorage,
    private val syncMetadataService: SyncMetadataService,
    private val preferencesDataSource: LogdatePreferencesDataSource,
    private val observeUserIdentityUseCase: ObserveUserIdentityUseCase,
    observeStreakUseCase: ObserveStreakUseCase,
    private val refreshStreakUseCase: RefreshStreakUseCase,
    private val verifyEmailUseCase: VerifyEmailUseCase,
    private val emailVerificationAvailability: EmailVerificationAvailability,
) : ViewModel() {
    private val _profileUpdateState = MutableStateFlow<ProfileUpdateState>(ProfileUpdateState.Idle)
    val profileUpdateState: StateFlow<ProfileUpdateState> = _profileUpdateState

    private val identityJson =
        Json {
            prettyPrint = true
            encodeDefaults = true
        }

    private val _identityState = MutableStateFlow(AccountIdentityState())
    val identityState: StateFlow<AccountIdentityState> = _identityState

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

    private val emailVerificationStateFlow = MutableStateFlow(EmailVerificationViewState())

    val state: StateFlow<AccountSettingsState> =
        combine(
            userStateRepository.userData,
            currentAccountFlow,
            sessionStorage.getSessionFlow(),
            emailVerificationStateFlow,
        ) { userData, currentAccount, session, emailState ->
            AccountSettingsState(
                userData = userData.orDefault(),
                currentAccount = currentAccount.orDefault(),
                isAuthenticated = session != null,
                isEmailVerificationAvailable = emailState.isAvailable,
                isVerifyingEmail = emailState.isVerifying,
                emailVerificationOutcome = emailState.outcome,
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

    val resolvedIdentity: StateFlow<ResolvedUserIdentity> =
        observeUserIdentityUseCase()
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                ResolvedUserIdentity(
                    displayName = "",
                    username = null,
                    profilePhotoUri = null,
                    bio = null,
                    birthday = null,
                    onboardedDate = null,
                    isAuthenticated = false,
                    cloudAccountId = null,
                ),
            )

    val isLibraryEnabled: StateFlow<Boolean> =
        preferencesDataSource
            .observeLibraryEnabled()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val streakData: StateFlow<StreakData> =
        observeStreakUseCase()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StreakData())

    fun setLibraryEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesDataSource.setLibraryEnabled(enabled)
        }
    }

    init {
        viewModelScope.launch {
            refreshStreakUseCase()
        }
        viewModelScope.launch {
            sessionStorage.getSessionFlow().collect { session ->
                if (session == null) {
                    _identityState.value = AccountIdentityState()
                } else {
                    refreshIdentityState()
                }
            }
        }
        viewModelScope.launch {
            val isAvailable =
                try {
                    emailVerificationAvailability.isAvailable()
                } catch (e: Exception) {
                    Napier.w("Failed to resolve email verification availability for Settings row", e)
                    false
                }
            emailVerificationStateFlow.value = emailVerificationStateFlow.value.copy(isAvailable = isAvailable)
        }
    }

    fun onVerifyEmailClicked() {
        if (emailVerificationStateFlow.value.isVerifying) return
        emailVerificationStateFlow.value =
            emailVerificationStateFlow.value.copy(
                isVerifying = true,
                outcome = null,
            )
        viewModelScope.launch {
            val outcome =
                try {
                    verifyEmailUseCase()
                } catch (e: Exception) {
                    Napier.e("Email verification crashed", e)
                    EmailVerificationOutcome.Failed("verification_crashed")
                }
            emailVerificationStateFlow.value =
                emailVerificationStateFlow.value.copy(
                    isVerifying = false,
                    outcome = outcome,
                )
            if (outcome is EmailVerificationOutcome.Success) {
                // Force-refresh the cached LogDateAccount so the Settings row flips to
                // the verified state immediately.
                getCurrentAccountUseCase(GetCurrentAccountUseCase.AccountRequest.RefreshAccountInfo)
            }
        }
    }

    fun dismissEmailVerificationSheet() {
        emailVerificationStateFlow.value =
            emailVerificationStateFlow.value.copy(
                outcome = null,
                isVerifying = false,
            )
    }

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
                // Drop the pending-uploads queue tied to this account. Without a session there's
                // nowhere for those items to go, and re-signing into the same or a different
                // account triggers a fresh BackfillLocalDataUseCase pass over local data.
                syncMetadataService.clearPending()
                Napier.i("Session cleared successfully")
            } catch (e: Exception) {
                Napier.e("Failed to sign out", e)
                onError(e.message ?: "Failed to sign out")
            }
        }
    }

    fun refreshIdentityState() {
        viewModelScope.launch {
            loadIdentityState()
        }
    }

    fun exportSigningKey(passphrase: String) {
        performIdentityAction("Exporting signing key…") {
            accountIdentityRepository.exportSigningKey(passphrase).map { payload ->
                IdentityActionResult(
                    message = "Signing key exported",
                    exportedKeyJson = identityJson.encodeToString(payload.exportedKey),
                )
            }
        }
    }

    fun rotateSigningKey(passphrase: String) {
        performIdentityAction("Rotating signing key…") {
            accountIdentityRepository.rotateSigningKey(passphrase).map { payload ->
                IdentityActionResult(
                    message = "Signing key rotated",
                    exportedKeyJson = identityJson.encodeToString(payload.exportedKey),
                )
            }
        }
    }

    fun importSigningKey(
        passphrase: String,
        exportedKeyJson: String,
    ) {
        performIdentityAction("Importing signing key…") {
            accountIdentityRepository
                .importSigningKey(
                    passphrase = passphrase,
                    exportedKeyJson = exportedKeyJson,
                ).map {
                    IdentityActionResult(message = "Signing key imported")
                }
        }
    }

    fun importSigningKeyWithRecovery(
        passphrase: String,
        exportedKeyJson: String,
        recoveryPhrase: String,
    ) {
        performIdentityAction("Importing signing key with recovery phrase…") {
            accountIdentityRepository
                .importSigningKeyWithRecovery(
                    passphrase = passphrase,
                    exportedKeyJson = exportedKeyJson,
                    recoveryPhrase = recoveryPhrase,
                ).map {
                    IdentityActionResult(message = "Signing key imported with recovery phrase")
                }
        }
    }

    fun derivePlcRecoveryKey(recoveryPhrase: String) {
        performIdentityAction("Deriving PLC recovery key…") {
            accountIdentityRepository
                .derivePlcRecoveryDidKey(recoveryPhrase)
                .map { derived ->
                    IdentityActionResult(
                        message = "PLC recovery key derived",
                        derivedRecoveryDidKey = derived.recoveryDidKey,
                    )
                }
        }
    }

    fun registerPlcRecoveryKey(recoveryDidKey: String) {
        performIdentityAction("Registering PLC recovery key…") {
            accountIdentityRepository.registerPlcRecoveryKey(recoveryDidKey).map {
                IdentityActionResult(message = "PLC recovery key registered")
            }
        }
    }

    fun registerDerivedPlcRecoveryKey() {
        val recoveryDidKey = _identityState.value.derivedRecoveryDidKey
        if (recoveryDidKey.isNullOrBlank()) {
            _identityState.value =
                _identityState.value.copy(
                    actionState = IdentityActionState.Error("Derive a PLC recovery key first"),
                )
            return
        }
        registerPlcRecoveryKey(recoveryDidKey)
    }

    fun clearIdentityActionState() {
        _identityState.value = _identityState.value.copy(actionState = IdentityActionState.Idle)
    }

    fun clearExportedKeyJson() {
        _identityState.value = _identityState.value.copy(exportedKeyJson = null)
    }

    fun clearDerivedRecoveryDidKey() {
        _identityState.value = _identityState.value.copy(derivedRecoveryDidKey = null)
    }

    private fun performIdentityAction(
        label: String,
        action: suspend () -> Result<IdentityActionResult>,
    ) {
        viewModelScope.launch {
            _identityState.value =
                _identityState.value.copy(
                    actionState = IdentityActionState.Working(label),
                )
            val result = action()
            if (result.isSuccess) {
                val actionResult = result.getOrThrow()
                loadIdentityState(
                    actionState = IdentityActionState.Success(actionResult.message),
                    exportedKeyJson = actionResult.exportedKeyJson ?: _identityState.value.exportedKeyJson,
                    derivedRecoveryDidKey = actionResult.derivedRecoveryDidKey ?: _identityState.value.derivedRecoveryDidKey,
                )
            } else {
                _identityState.value =
                    _identityState.value.copy(
                        actionState =
                            IdentityActionState.Error(
                                result.exceptionOrNull()?.message ?: "Identity action failed",
                            ),
                    )
            }
        }
    }

    private data class IdentityActionResult(
        val message: String,
        val exportedKeyJson: String? = null,
        val derivedRecoveryDidKey: String? = null,
    )

    private suspend fun loadIdentityState(
        actionState: IdentityActionState = IdentityActionState.Idle,
        exportedKeyJson: String? = _identityState.value.exportedKeyJson,
        derivedRecoveryDidKey: String? = _identityState.value.derivedRecoveryDidKey,
    ) {
        _identityState.value =
            _identityState.value.copy(
                isLoading = true,
                actionState = actionState,
                exportedKeyJson = exportedKeyJson,
                derivedRecoveryDidKey = derivedRecoveryDidKey,
            )

        val statusResult = accountIdentityRepository.getIdentityStatus()
        val operationsResult = accountIdentityRepository.getHostedPlcOperations()
        _identityState.value =
            when {
                statusResult.isSuccess && operationsResult.isSuccess -> {
                    _identityState.value.copy(
                        isLoading = false,
                        status = statusResult.getOrThrow(),
                        operations = operationsResult.getOrThrow(),
                        actionState = actionState,
                        exportedKeyJson = exportedKeyJson,
                        derivedRecoveryDidKey = derivedRecoveryDidKey,
                    )
                }

                else -> {
                    val message =
                        statusResult.exceptionOrNull()?.message
                            ?: operationsResult.exceptionOrNull()?.message
                            ?: "Failed to load identity state"
                    _identityState.value.copy(
                        isLoading = false,
                        actionState = IdentityActionState.Error(message),
                        exportedKeyJson = exportedKeyJson,
                        derivedRecoveryDidKey = derivedRecoveryDidKey,
                    )
                }
            }
    }
}
