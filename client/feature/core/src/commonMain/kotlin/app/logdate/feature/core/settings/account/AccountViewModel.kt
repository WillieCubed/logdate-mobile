package app.logdate.feature.core.settings.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.domain.identity.ResolvedUserIdentity
import app.logdate.client.permissions.EmailVerificationOutcome
import app.logdate.client.repository.account.LinkedSignInProvider
import app.logdate.client.repository.account.PasskeyAccountRepository
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the Account screen shows. */
sealed interface AccountUiState {
    data object Loading : AccountUiState

    /** No session on this device, so the screen offers sign-in instead. */
    data object SignedOut : AccountUiState

    data class SignedIn(
        val header: AccountHeader,
        val signIn: SignInSummary,
        /** Whether this device holds the recovery phrase that unlocks the synced journal; `null` until checked. */
        val hasRecoveryPhrase: Boolean?,
        /** `null` when there is no email to show and no way to add one here. */
        val email: EmailRow?,
        /** `null` when recovery keys are not offered: the feature is off or the server can't perform them. */
        val recoveryKey: RecoveryKeyRow? = null,
        val server: ServerRow,
        val isSigningOut: Boolean,
    ) : AccountUiState
}

data class AccountHeader(
    val displayName: String,
    val username: String?,
)

/** A short description of how the person signs in, for the row that opens the full list. */
sealed interface SignInSummary {
    data object Loading : SignInSummary

    data class Known(
        val passkeyCount: Int,
        val linkedProviders: List<LinkedSignInProvider.Kind>,
    ) : SignInSummary

    /** The list could not be read; the row still opens the screen that can retry. */
    data object Unknown : SignInSummary
}

/** What the Recovery key row says about the identity's recovery key. */
enum class RecoveryKeyRow {
    CHECKING,
    ON,
    OFF,

    /** The identity could not be read; the row still opens the screen that can retry. */
    UNKNOWN,
}

data class EmailRow(
    val address: String?,
    val isVerified: Boolean,
    val canVerify: Boolean,
)

data class ServerRow(
    val name: String?,
    val host: String,
    val isLogDateCloud: Boolean,
)

/** Where email verification stands, for the sheet that runs it. */
data class EmailVerificationProgress(
    val isVerifying: Boolean = false,
    val outcome: EmailVerificationOutcome? = null,
)

sealed interface AccountEvent {
    data object SignedOut : AccountEvent

    data object SignOutFailed : AccountEvent
}

/**
 * The Account screen: who is signed in, how they sign in and recover, where their journal is
 * hosted, and signing out.
 */
class AccountViewModel(
    private val accountRepository: PasskeyAccountRepository,
    userIdentity: Flow<ResolvedUserIdentity>,
    private val connectedServer: ConnectedServer,
    private val hasRecoveryPhrase: suspend () -> Boolean,
    private val isEmailVerificationAvailable: suspend () -> Boolean,
    private val verifyEmail: suspend () -> EmailVerificationOutcome,
    /** Whether the recovery key is offered: its feature flag is on and the server supports it. */
    private val isRecoveryKeyOffered: Flow<Boolean> = flowOf(false),
    /** Whether the identity has a recovery key registered. */
    private val hasRecoveryKey: suspend () -> Boolean = { false },
) : ViewModel() {
    private val details = MutableStateFlow(AccountDetails())
    private var loadJob: Job? = null

    private val _emailVerification = MutableStateFlow(EmailVerificationProgress())
    val emailVerification: StateFlow<EmailVerificationProgress> = _emailVerification.asStateFlow()

    private val _events = Channel<AccountEvent>(Channel.BUFFERED)
    val events: Flow<AccountEvent> = _events.receiveAsFlow()

    val state: StateFlow<AccountUiState> =
        combine(
            accountRepository.isAuthenticated,
            accountRepository.currentAccount,
            userIdentity,
            connectedServer.info,
            combine(details, isRecoveryKeyOffered) { details, offered -> details to offered },
        ) { isAuthenticated, account, identity, server, (details, recoveryKeyOffered) ->
            if (!isAuthenticated) return@combine AccountUiState.SignedOut
            AccountUiState.SignedIn(
                header =
                    AccountHeader(
                        displayName = identity.displayName.ifBlank { account?.displayName.orEmpty() },
                        username = identity.username ?: account?.username,
                    ),
                signIn = details.signIn,
                hasRecoveryPhrase = details.hasRecoveryPhrase,
                email =
                    when {
                        account?.email != null ->
                            EmailRow(
                                address = account.email,
                                isVerified = account.emailVerified,
                                canVerify = !account.emailVerified && details.canVerifyEmail,
                            )
                        details.canVerifyEmail -> EmailRow(address = null, isVerified = false, canVerify = true)
                        else -> null
                    },
                server = ServerRow(name = server.displayName, host = server.host, isLogDateCloud = server.isLogDateCloud),
                recoveryKey = if (recoveryKeyOffered) details.recoveryKey else null,
                isSigningOut = details.isSigningOut,
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, AccountUiState.Loading)

    init {
        refresh()
        // The server's description is refreshed after the screen opens, so the row can become offered later.
        viewModelScope.launch {
            isRecoveryKeyOffered.drop(1).filter { it }.collect { loadRecoveryKey() }
        }
    }

    /**
     * Reloads what may have changed since the screen was last open, such as a newly added passkey.
     * A reload already under way is left to finish.
     */
    fun refresh() {
        if (loadJob?.isActive == true) return
        loadJob =
            viewModelScope.launch {
                launch { connectedServer.refresh() }
                loadDetails()
            }
    }

    fun verifyEmail() {
        if (_emailVerification.value.isVerifying) return
        _emailVerification.value = EmailVerificationProgress(isVerifying = true)
        viewModelScope.launch {
            val outcome =
                runCatching { verifyEmail.invoke() }
                    .getOrElse { error ->
                        Napier.e("Email verification failed unexpectedly", error)
                        EmailVerificationOutcome.Failed("verification_crashed")
                    }
            _emailVerification.value = EmailVerificationProgress(outcome = outcome)
            if (outcome is EmailVerificationOutcome.Success) {
                accountRepository.getAccountInfo().onFailure { Napier.w("Could not reload the account after verifying email", it) }
            }
        }
    }

    fun dismissEmailVerification() {
        _emailVerification.value = EmailVerificationProgress()
    }

    fun signOut() {
        if (details.value.isSigningOut) return
        details.update { it.copy(isSigningOut = true) }
        viewModelScope.launch {
            accountRepository
                .signOut()
                .onSuccess { _events.send(AccountEvent.SignedOut) }
                .onFailure { error ->
                    Napier.w("Failed to sign out", error)
                    _events.send(AccountEvent.SignOutFailed)
                }
            details.update { it.copy(isSigningOut = false) }
        }
    }

    /** Each part is shown as soon as it is known, so the local checks don't wait on the network. */
    private suspend fun loadDetails() =
        coroutineScope {
            launch {
                val hasPhrase =
                    runCatching { hasRecoveryPhrase() }
                        .onFailure { Napier.w("Could not check for the recovery phrase", it) }
                        .getOrDefault(false)
                details.update { it.copy(hasRecoveryPhrase = hasPhrase) }
            }
            launch {
                val canVerify =
                    runCatching { isEmailVerificationAvailable() }
                        .onFailure { Napier.w("Could not check whether email verification is available", it) }
                        .getOrDefault(false)
                details.update { it.copy(canVerifyEmail = canVerify) }
            }
            launch {
                val signIn = loadSignInSummary()
                details.update { it.copy(signIn = signIn) }
            }
            launch { if (isRecoveryKeyOffered.first()) loadRecoveryKey() }
        }

    private suspend fun loadRecoveryKey() {
        val row =
            runCatching { hasRecoveryKey() }
                .onFailure { Napier.w("Could not check for a recovery key", it) }
                .fold(onSuccess = { if (it) RecoveryKeyRow.ON else RecoveryKeyRow.OFF }, onFailure = { RecoveryKeyRow.UNKNOWN })
        details.update { it.copy(recoveryKey = row) }
    }

    private suspend fun loadSignInSummary(): SignInSummary {
        val (passkeys, providers) =
            coroutineScope {
                val passkeys = async { accountRepository.listPasskeys() }
                val providers = async { accountRepository.listLinkedSignInProviders() }
                passkeys.await() to providers.await()
            }
        val error = passkeys.exceptionOrNull() ?: providers.exceptionOrNull()
        if (error != null) {
            Napier.w("Could not read sign-in methods for the Account summary", error)
            return SignInSummary.Unknown
        }
        return SignInSummary.Known(
            passkeyCount = passkeys.getOrThrow().size,
            linkedProviders = providers.getOrThrow().map { it.kind },
        )
    }

    private data class AccountDetails(
        val signIn: SignInSummary = SignInSummary.Loading,
        val hasRecoveryPhrase: Boolean? = null,
        val recoveryKey: RecoveryKeyRow = RecoveryKeyRow.CHECKING,
        val canVerifyEmail: Boolean = false,
        val isSigningOut: Boolean = false,
    )
}
