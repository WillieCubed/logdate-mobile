package app.logdate.feature.core.settings.account.recovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.repository.account.IdentityRecoveryException
import app.logdate.client.repository.account.IdentityRecoveryFailure
import app.logdate.client.text.toUiText
import app.logdate.feature.core.AppAuthState
import app.logdate.feature.core.BiometricGatekeeper
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed interface RecoveryPhraseUiState {
    data object Checking : RecoveryPhraseUiState

    /** The phrase is on this device, covered until the person confirms it is them. */
    data object Hidden : RecoveryPhraseUiState

    data object Confirming : RecoveryPhraseUiState

    data object Verifying : RecoveryPhraseUiState

    data object Verified : RecoveryPhraseUiState

    data object VerificationFailed : RecoveryPhraseUiState

    data class Revealed(
        val words: List<String>,
    ) : RecoveryPhraseUiState

    /** This device has no phrase, so synced entries from elsewhere can't be read until it's entered. */
    data object NotOnThisDevice : RecoveryPhraseUiState

    data class Failed(
        val reason: RevealFailure,
    ) : RecoveryPhraseUiState
}

enum class RevealFailure {
    /** The person didn't confirm with their screen lock or biometrics. */
    NOT_CONFIRMED,

    /** Secure storage could not be read. */
    UNREADABLE,
}

/** Whether the confirmed recovery phrase can also move the account; shown only when that is offered. */
sealed interface AccountRecoveryUiState {
    data object Checking : AccountRecoveryUiState

    data object Ready : AccountRecoveryUiState

    /** Setup hasn't finished, such as after the passkey prompt was closed. */
    data object NeedsSetup : AccountRecoveryUiState

    /** Waiting for the passkey and the server. */
    data object Finishing : AccountRecoveryUiState

    /** Whether setup is finished could not be checked. */
    data object CheckFailed : AccountRecoveryUiState

    data class SetupFailed(
        val failure: IdentityRecoveryFailure,
    ) : AccountRecoveryUiState
}

/** The wording of the confirmation prompt, resolved by the screen so it can be translated. */
data class RevealPrompt(
    val title: String,
    val subtitle: String,
    val description: String?,
)

/**
 * Shows the 12-word recovery phrase that unlocks the synced journal, behind the device's screen
 * lock or biometrics.
 *
 * @param loadPhrase reads the stored phrase, or `null` when this device has none
 * @param recovery sets up the confirmed phrase to move the account too, or `null` where that
 *   is never offered
 */
class RecoveryPhraseViewModel(
    private val gatekeeper: BiometricGatekeeper,
    private val loadPhrase: suspend () -> List<String>?,
    private val confirmPhrase: suspend (List<String>) -> Boolean = { false },
    private val isPhraseVerified: suspend () -> Boolean = { false },
    private val onVerified: () -> Unit = {},
    private val recovery: AccountRecovery? = null,
) : ViewModel() {
    fun verify(phraseInput: String) {
        if (_state.value !is RecoveryPhraseUiState.Revealed) return
        val words =
            phraseInput
                .trim()
                .lowercase()
                .split(Regex("\\s+"))
                .filter(String::isNotBlank)
        _state.value = RecoveryPhraseUiState.Verifying
        viewModelScope.launch {
            _state.value =
                runCatching { confirmPhrase(words) }
                    .fold(
                        onSuccess = { verified ->
                            if (verified) {
                                runCatching(onVerified)
                                    .onFailure { error -> Napier.w("Could not request a Cloud backup", error) }
                                finishAccountRecoverySetup()
                                RecoveryPhraseUiState.Verified
                            } else {
                                RecoveryPhraseUiState.VerificationFailed
                            }
                        },
                        onFailure = { error ->
                            Napier.w("Could not verify recovery phrase", error)
                            RecoveryPhraseUiState.VerificationFailed
                        },
                    )
        }
    }

    private val _state = MutableStateFlow<RecoveryPhraseUiState>(RecoveryPhraseUiState.Checking)
    val state: StateFlow<RecoveryPhraseUiState> = _state.asStateFlow()

    private val _accountRecovery = MutableStateFlow<AccountRecoveryUiState?>(null)

    /** `null` unless the phrase is confirmed and account recovery is offered. */
    val accountRecovery: StateFlow<AccountRecoveryUiState?> = _accountRecovery.asStateFlow()

    private var accountRecoveryJob: Job? = null

    init {
        check()
        // The server's description is refreshed after the screen opens, so recovery can become offered later.
        recovery?.let { available ->
            viewModelScope.launch { available.isOffered.drop(1).collect { checkAccountRecovery() } }
        }
    }

    /** Looks again for the phrase, which may have been entered since this screen was last open. */
    fun check() {
        viewModelScope.launch {
            _state.value =
                runCatching { loadPhrase() to isPhraseVerified() }
                    .fold(
                        onSuccess = { (phrase, verified) ->
                            val current = _state.value
                            when {
                                phrase == null -> RecoveryPhraseUiState.NotOnThisDevice

                                verified -> RecoveryPhraseUiState.Verified

                                // A screen rebuilt mid-reveal, such as after a rotation, keeps what the person confirmed.
                                current is RecoveryPhraseUiState.Revealed || current is RecoveryPhraseUiState.Confirming -> current

                                else -> RecoveryPhraseUiState.Hidden
                            }
                        },
                        onFailure = { error ->
                            Napier.w("Could not check for the recovery phrase", error)
                            RecoveryPhraseUiState.Failed(RevealFailure.UNREADABLE)
                        },
                    )
            checkAccountRecovery()
        }
    }

    fun reveal(prompt: RevealPrompt) {
        if (_state.value == RecoveryPhraseUiState.Confirming) return
        _state.value = RecoveryPhraseUiState.Confirming
        gatekeeper.authenticate(
            title = prompt.title.toUiText(),
            subtitle = prompt.subtitle.toUiText(),
            description = prompt.description?.toUiText(),
            onResult = { result ->
                if (result != AppAuthState.AUTHENTICATED && result != AppAuthState.NO_PROMPT_NEEDED) {
                    _state.value = RecoveryPhraseUiState.Failed(RevealFailure.NOT_CONFIRMED)
                    return@authenticate
                }
                viewModelScope.launch { _state.value = readPhrase() }
            },
        )
    }

    /** Covers a revealed phrase; any other state is left as it is. */
    fun hide() {
        if (_state.value is RecoveryPhraseUiState.Revealed) _state.value = RecoveryPhraseUiState.Hidden
    }

    /** Sets up the confirmed phrase to move the account too; the server asks for a passkey first. */
    fun finishAccountRecoverySetup() {
        val available = recovery ?: return
        if (_accountRecovery.value == AccountRecoveryUiState.Finishing) return
        accountRecoveryJob?.cancel()
        accountRecoveryJob =
            viewModelScope.launch {
                if (!available.isOffered.first()) {
                    _accountRecovery.value = null
                    return@launch
                }
                _accountRecovery.value = AccountRecoveryUiState.Finishing
                _accountRecovery.value =
                    runCatching { loadPhrase() ?: error("The recovery phrase is no longer on this device") }
                        .mapCatching { phrase -> available.finishSetup(phrase).getOrThrow() }
                        .fold(
                            onSuccess = { AccountRecoveryUiState.Ready },
                            onFailure = { error ->
                                if (error is CancellationException) throw error
                                val failure =
                                    (error as? IdentityRecoveryException)?.failure
                                        ?: IdentityRecoveryFailure.REFUSED.also {
                                            Napier.w(
                                                "Could not finish setting up account recovery",
                                                error,
                                            )
                                        }
                                // Closing the passkey prompt leaves setup unfinished; it isn't a failure.
                                if (failure == IdentityRecoveryFailure.STEP_UP_CANCELLED) {
                                    AccountRecoveryUiState.NeedsSetup
                                } else {
                                    AccountRecoveryUiState.SetupFailed(failure)
                                }
                            },
                        )
            }
    }

    /** Checks again whether the confirmed phrase can move the account. */
    fun checkAccountRecovery() {
        val available = recovery ?: return
        if (_accountRecovery.value == AccountRecoveryUiState.Finishing) return
        accountRecoveryJob?.cancel()
        accountRecoveryJob =
            viewModelScope.launch {
                if (!available.isOffered.first() || _state.value != RecoveryPhraseUiState.Verified) {
                    _accountRecovery.value = null
                    return@launch
                }
                if (_accountRecovery.value == null) _accountRecovery.value = AccountRecoveryUiState.Checking
                _accountRecovery.value =
                    runCatching { loadPhrase() ?: error("The recovery phrase is no longer on this device") }
                        .mapCatching { phrase -> available.status(phrase).getOrThrow() }
                        .fold(
                            onSuccess = { status ->
                                when (status) {
                                    AccountRecoveryStatus.READY -> AccountRecoveryUiState.Ready
                                    AccountRecoveryStatus.NEEDS_SETUP -> AccountRecoveryUiState.NeedsSetup
                                }
                            },
                            onFailure = { error ->
                                if (error is CancellationException) throw error
                                Napier.w("Could not check whether account recovery is set up", error)
                                AccountRecoveryUiState.CheckFailed
                            },
                        )
            }
    }

    private suspend fun readPhrase(): RecoveryPhraseUiState =
        runCatching { loadPhrase() }
            .fold(
                onSuccess = { words -> words?.let { RecoveryPhraseUiState.Revealed(it) } ?: RecoveryPhraseUiState.NotOnThisDevice },
                onFailure = { error ->
                    Napier.e("Could not read the recovery phrase", error)
                    RecoveryPhraseUiState.Failed(RevealFailure.UNREADABLE)
                },
            )
}
