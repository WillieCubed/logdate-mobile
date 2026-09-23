package app.logdate.feature.core.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.datastore.SessionStorage
import app.logdate.client.domain.account.EmailVerificationAvailability
import app.logdate.client.domain.account.GetCurrentAccountUseCase
import app.logdate.client.domain.account.VerifyEmailUseCase
import app.logdate.client.permissions.EmailVerificationOutcome
import app.logdate.client.repository.account.AccountHostedPlcOperation
import app.logdate.client.repository.account.AccountIdentityRepository
import app.logdate.client.repository.account.AccountIdentityStatus
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
    private val accountIdentityRepository: AccountIdentityRepository,
    private val passkeyAccountRepository: PasskeyAccountRepository,
    private val sessionStorage: SessionStorage,
    private val verifyEmailUseCase: VerifyEmailUseCase,
    private val emailVerificationAvailability: EmailVerificationAvailability,
) : ViewModel() {
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

    init {
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
                val result = passkeyAccountRepository.signOut()
                if (result.isFailure) {
                    val message = result.exceptionOrNull()?.message ?: "Failed to sign out"
                    onError(message)
                    return@launch
                }
                Napier.i("Session cleared; local backup queue is retained until sign-in resumes")
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
