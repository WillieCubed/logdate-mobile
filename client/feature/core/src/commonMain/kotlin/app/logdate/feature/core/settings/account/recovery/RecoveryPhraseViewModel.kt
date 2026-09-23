package app.logdate.feature.core.settings.account.recovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
 */
class RecoveryPhraseViewModel(
    private val gatekeeper: BiometricGatekeeper,
    private val loadPhrase: suspend () -> List<String>?,
) : ViewModel() {
    private val _state = MutableStateFlow<RecoveryPhraseUiState>(RecoveryPhraseUiState.Checking)
    val state: StateFlow<RecoveryPhraseUiState> = _state.asStateFlow()

    init {
        check()
    }

    /** Looks again for the phrase, which may have been entered since this screen was last open. */
    fun check() {
        viewModelScope.launch {
            _state.value =
                runCatching { loadPhrase() }
                    .fold(
                        onSuccess = { if (it == null) RecoveryPhraseUiState.NotOnThisDevice else RecoveryPhraseUiState.Hidden },
                        onFailure = { error ->
                            Napier.w("Could not check for the recovery phrase", error)
                            RecoveryPhraseUiState.Failed(RevealFailure.UNREADABLE)
                        },
                    )
        }
    }

    fun reveal(prompt: RevealPrompt) {
        if (_state.value == RecoveryPhraseUiState.Confirming) return
        _state.value = RecoveryPhraseUiState.Confirming
        gatekeeper.authenticate(
            title = prompt.title,
            subtitle = prompt.subtitle,
            description = prompt.description,
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
