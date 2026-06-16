package app.logdate.feature.onboarding.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.device.crypto.IdentityKeyManager
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RecoveryPhraseSetupUiState(
    val words: List<String> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
)

class RecoveryPhraseViewModel(
    private val identityKeyManager: IdentityKeyManager,
) : ViewModel() {
    private val _setupState = MutableStateFlow(RecoveryPhraseSetupUiState())
    val setupState: StateFlow<RecoveryPhraseSetupUiState> = _setupState.asStateFlow()

    init {
        prepareRecoveryPhrase()
    }

    fun prepareRecoveryPhrase() {
        viewModelScope.launch {
            _setupState.value = RecoveryPhraseSetupUiState(isLoading = true)
            _setupState.value =
                runCatching {
                    val existingPhrase = identityKeyManager.getStoredRecoveryPhrase()
                    when {
                        existingPhrase != null ->
                            RecoveryPhraseSetupUiState(
                                words = existingPhrase.words,
                                isLoading = false,
                            )
                        identityKeyManager.hasIdentityKey() ->
                            RecoveryPhraseSetupUiState(
                                isLoading = false,
                                errorMessage = "Enter your existing recovery phrase to make it available from settings.",
                            )
                        else -> {
                            val phrase = identityKeyManager.setupNewIdentity()
                            RecoveryPhraseSetupUiState(
                                words = phrase.words,
                                isLoading = false,
                            )
                        }
                    }
                }.getOrElse { error ->
                    Napier.e("Failed to prepare recovery phrase", error)
                    RecoveryPhraseSetupUiState(
                        isLoading = false,
                        errorMessage = "We could not prepare your recovery phrase. Try again.",
                    )
                }
        }
    }

    suspend fun recoverIdentity(words: List<String>): Result<Unit> =
        runCatching {
            identityKeyManager.recoverIdentity(words)
            _setupState.value =
                RecoveryPhraseSetupUiState(
                    words = words,
                    isLoading = false,
                )
        }
}
