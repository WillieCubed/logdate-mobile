package app.logdate.feature.core.settings.account.recovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.repository.account.IdentityRecoveryFailure
import app.logdate.client.text.toUiText
import app.logdate.feature.core.AppAuthState
import app.logdate.feature.core.BiometricGatekeeper
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
        /** The person already proved they saved these words, so showing them asks for nothing more. */
        val confirmed: Boolean = false,
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

/** Whether the recovery phrase on this device can also move the account; shown only when that is offered. */
sealed interface AccountRecoveryUiState {
    data object Checking : AccountRecoveryUiState

    data object Ready : AccountRecoveryUiState

    /** Setup needs one passkey confirmation, such as after the passkey prompt was closed. */
    data object NeedsSetup : AccountRecoveryUiState

    /** Waiting for the passkey and the server. */
    data object Finishing : AccountRecoveryUiState

    /** A connection or server problem stopped setup, and it will be tried again automatically; `null` when the check itself failed. */
    data class Retrying(
        val failure: IdentityRecoveryFailure?,
    ) : AccountRecoveryUiState

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
 * Shows the 12-word recovery phrase behind the device's screen lock or biometrics, so the person
 * can save a copy in case they lose every device. Nothing else waits for them to do so: backup and
 * account recovery are set up without it.
 *
 * @param loadPhrase reads the stored phrase, or `null` when this device has none
 * @param recovery sets up account recovery on its own and reports where it stands, or `null` where
 *   that is never offered
 */
class RecoveryPhraseViewModel(
    private val gatekeeper: BiometricGatekeeper,
    private val loadPhrase: suspend () -> List<String>?,
    private val confirmPhrase: suspend (List<String>) -> Boolean = { false },
    private val isPhraseVerified: suspend () -> Boolean = { false },
    private val onVerified: () -> Unit = {},
    private val recovery: AutomaticAccountRecovery? = null,
) : ViewModel() {
    /** Records that the person saved a copy of the shown phrase. */
    fun confirmSaved() {
        val revealed = _state.value as? RecoveryPhraseUiState.Revealed ?: return
        _state.value = RecoveryPhraseUiState.Verifying
        viewModelScope.launch {
            _state.value =
                runCatching { confirmPhrase(revealed.words) }
                    .fold(
                        onSuccess = { verified ->
                            if (verified) {
                                runCatching(onVerified)
                                    .onFailure { error -> Napier.w("Could not request a Cloud backup", error) }
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

    /** Where account recovery setup stands; `null` when it isn't offered or this device has no phrase. */
    val accountRecovery: StateFlow<AccountRecoveryUiState?> = recovery?.state ?: MutableStateFlow(null)

    init {
        check()
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

    /** Finishes setting up account recovery now; the passkey prompt is shown first. */
    fun finishAccountRecoverySetup() {
        recovery?.finishWithPasskey()
    }

    /** Checks again whether account recovery is set up, and finishes it if no prompt is needed. */
    fun checkAccountRecovery() {
        recovery?.checkAgain()
    }

    private suspend fun readPhrase(): RecoveryPhraseUiState =
        runCatching { loadPhrase() }
            .fold(
                onSuccess = { words ->
                    words?.let { RecoveryPhraseUiState.Revealed(it, confirmed = runCatching { isPhraseVerified() }.getOrDefault(false)) }
                        ?: RecoveryPhraseUiState.NotOnThisDevice
                },
                onFailure = { error ->
                    Napier.e("Could not read the recovery phrase", error)
                    RecoveryPhraseUiState.Failed(RevealFailure.UNREADABLE)
                },
            )
}
